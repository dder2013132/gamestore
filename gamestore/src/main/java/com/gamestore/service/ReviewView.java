package com.gamestore.service;

import java.io.IOException;
import java.sql.SQLException;
import com.gamestore.repository.JdbcReviewRepository;
import com.gamestore.util.AuthSession;
import jakarta.servlet.http.HttpServletRequest;

public final class ReviewView {
    private ReviewView() { }
    public static void populate(HttpServletRequest request, JdbcReviewRepository reviews, int gameId)
            throws SQLException, IOException, ClassNotFoundException {
        var user = AuthSession.currentUser(request);
        var result = reviews.findForGame(gameId, user == null ? null : user.getId(), page(request.getParameter("reviewPage")));
        request.setAttribute("reviews", result.page()); request.setAttribute("ownReview", result.own());
        request.setAttribute("canReview", result.eligible()); request.setAttribute("reviewGameId", gameId);
        request.setAttribute("reviewListPath", "/games");
    }
    public static int page(String value) {
        try { return Math.max(1, Integer.parseInt(value)); } catch (NumberFormatException e) { return 1; }
    }
}
