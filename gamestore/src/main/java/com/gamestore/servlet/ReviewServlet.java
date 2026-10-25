package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import com.gamestore.model.*;
import com.gamestore.repository.*;
import com.gamestore.service.ReviewView;
import com.gamestore.util.AuthSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet(urlPatterns = {"/reviews/edit", "/reviews/create", "/reviews/update", "/reviews/delete", "/account/reviews"})
public class ReviewServlet extends HttpServlet {
    private final JdbcReviewRepository reviews;
    private final JdbcGameRepository games;
    public ReviewServlet() { this(new JdbcReviewRepository(), new JdbcGameRepository()); }
    ReviewServlet(JdbcReviewRepository reviews, JdbcGameRepository games) { this.reviews = reviews; this.games = games; }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        String path = request.getServletPath();
        if ("/account/reviews".equals(path)) {
            try {
                request.setAttribute("reviews", reviews.findForUser(user.getId(), ReviewView.page(request.getParameter("reviewPage"))));
                request.setAttribute("reviewListPath", "/account/reviews");
            } catch (SQLException | IOException | ClassNotFoundException e) {
                unavailable(request, response, e);
            }
            request.getRequestDispatcher("/WEB-INF/views/account-reviews.jsp").forward(request, response); return;
        }
        if (!"/reviews/edit".equals(path)) { response.sendError(405); return; }
        Game game = loadGame(request, response); if (game == null) { return; }
        try {
            Review own = reviews.findOwn(user.getId(), Integer.parseInt(game.getId()));
            boolean eligible = reviews.eligible(user.getId(), Integer.parseInt(game.getId()));
            request.setAttribute("canReview", eligible);
            request.setAttribute("reviewMode", own == null ? "create" : "update");
            request.setAttribute("formRating", own == null ? 5 : own.rating());
            request.setAttribute("formBody", own == null ? "" : own.body());
            if (own != null) { request.setAttribute("formReviewId", own.id()); request.setAttribute("formVersion", own.version()); }
            if (!eligible) { response.setStatus(403); request.setAttribute("reviewError", "注文が完了したゲームのみレビューを書けます。支払い待ち・キャンセル済みの注文は対象外です。"); }
        } catch (SQLException | IOException | ClassNotFoundException e) { unavailable(request, response, e); }
        request.getRequestDispatcher("/WEB-INF/views/review-edit.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        String path = request.getServletPath();
        if (!java.util.Set.of("/reviews/create", "/reviews/update", "/reviews/delete").contains(path)) { response.sendError(405); return; }
        Game game = loadGame(request, response); if (game == null) { return; }
        int gameId = Integer.parseInt(game.getId());
        boolean create = path.endsWith("/create"), delete = path.endsWith("/delete");
        request.setAttribute("reviewMode", create ? "create" : "update");
        request.setAttribute("canReview", true);
        request.setAttribute("formBody", request.getParameter("body"));
        String submittedRating = request.getParameter("rating");
        request.setAttribute("formRating", submittedRating != null && submittedRating.matches("[1-5]") ? Integer.parseInt(submittedRating) : 0);
        request.setAttribute("formReviewId", request.getParameter("reviewId"));
        request.setAttribute("formVersion", request.getParameter("version"));
        ReviewResult result;
        try {
            long id = create ? 0 : number(request.getParameter("reviewId"), false);
            long version = create ? 0 : number(request.getParameter("version"), true);
            if (delete) { result = reviews.delete(user.getId(), gameId, id, version); }
            else {
                String ratingText = request.getParameter("rating");
                if (ratingText == null || !ratingText.matches("[1-5]")) { throw new IllegalArgumentException("評価は1〜5から選んでください。"); }
                int rating = Integer.parseInt(ratingText);
                String body = Review.validate(rating, request.getParameter("body"));
                request.setAttribute("formBody", body);
                result = create ? reviews.create(user.getId(), gameId, rating, body)
                        : reviews.update(user.getId(), gameId, id, version, rating, body);
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(400); request.setAttribute("reviewError", e.getMessage());
            request.getRequestDispatcher(delete ? "/WEB-INF/views/review-conflict.jsp" : "/WEB-INF/views/review-edit.jsp").forward(request, response); return;
        } catch (SQLException | IOException | ClassNotFoundException e) {
            unavailable(request, response, e);
            request.getRequestDispatcher(delete ? "/WEB-INF/views/review-conflict.jsp" : "/WEB-INF/views/review-edit.jsp").forward(request, response); return;
        }
        switch (result) {
            case SAVED -> { response.sendRedirect(request.getContextPath() + (delete ? "/account/reviews?deleted=1" : "/games?id=" + gameId + "#reviews")); return; }
            case NOT_FOUND -> { response.sendError(404, "Review not found"); return; }
            case NOT_ELIGIBLE -> {
                response.setStatus(403); request.setAttribute("canReview", false);
                request.setAttribute("reviewError", "注文が完了したゲームのみレビューを書けます。支払い待ち・キャンセル済みの注文は対象外です。");
            }
            case ALREADY_EXISTS -> {
                response.setStatus(409); request.setAttribute("reviewError", "このゲームには投稿済みです。レビューを読み直してから編集してください。");
            }
            case STALE -> {
                response.setStatus(409); request.setAttribute("reviewError", "別の画面ですでに変更されています。最新のレビューを読み直してから操作してください。");
            }
        }
        request.getRequestDispatcher(delete ? "/WEB-INF/views/review-conflict.jsp" : "/WEB-INF/views/review-edit.jsp").forward(request, response);
    }

    private Game loadGame(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        int id;
        try { id = Integer.parseInt(request.getParameter("gameId")); if (id <= 0) { throw new NumberFormatException(); } }
        catch (NumberFormatException e) { response.sendError(400, "Invalid game ID"); return null; }
        try {
            Game game = games.findById(Integer.toString(id));
            if (game == null) { response.sendError(404, "Game not found"); return null; }
            request.setAttribute("game", game); return game;
        } catch (SQLException | IOException | ClassNotFoundException e) {
            unavailable(request, response, e); request.getRequestDispatcher("/WEB-INF/views/review-conflict.jsp").forward(request, response); return null;
        }
    }
    private static long number(String value, boolean zeroAllowed) {
        try { long number = Long.parseLong(value); if (number < (zeroAllowed ? 0 : 1)) { throw new NumberFormatException(); } return number; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("レビュー情報が正しくありません。画面を読み直してください。"); }
    }
    private void unavailable(HttpServletRequest request, HttpServletResponse response, Exception e) {
        getServletContext().log("Review database operation failed", e); response.setStatus(503);
        request.setAttribute("reviewError", "レビューを確認できませんでした。時間をおいて、もう一度内容を確認してください。");
    }
}
