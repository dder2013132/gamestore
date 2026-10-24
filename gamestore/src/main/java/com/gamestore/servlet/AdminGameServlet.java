package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = {"/admin/games", "/admin/games/edit"})
public class AdminGameServlet extends HttpServlet {
    private final JdbcGameRepository games;

    public AdminGameServlet() { this(new JdbcGameRepository()); }
    AdminGameServlet(JdbcGameRepository games) { this.games = games; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (editing(request)) {
            Game game = loadEditGame(request, response);
            if (game == null) { return; }
            request.setAttribute("formTitle", game.getTitle());
            request.setAttribute("formPrice", game.getPrice());
            request.setAttribute("formGenre", game.getGenre());
            request.setAttribute("formImage", game.getImagePath());
            request.setAttribute("formDescription", game.getDescription());
        }
        render(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Game editingGame = null;
        if (editing(request)) {
            editingGame = loadEditGame(request, response);
            if (editingGame == null) { return; }
            if (request.getParameter("description") == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Description field is required");
                return;
            }
        }
        String title = value(request, "title");
        String price = value(request, "price");
        String genre = value(request, "genre");
        String image = value(request, "imagePath");
        String description = value(request, "description");
        request.setAttribute("formTitle", title);
        request.setAttribute("formPrice", price);
        request.setAttribute("formGenre", genre);
        request.setAttribute("formImage", image);
        request.setAttribute("formDescription", description);

        String error = null;
        Integer amount = null;
        try {
            if (price.matches("[0-9]{1,10}")) { amount = Integer.valueOf(price); }
        } catch (NumberFormatException ignored) { }
        if (title.isBlank() || title.length() > 200) {
            error = "タイトルは1〜200文字で入力してください。";
        } else if (amount == null) {
            error = "価格は0〜2147483647の整数で入力してください。";
        } else if (genre.isBlank() || genre.length() > 50) {
            error = "ジャンルは1〜50文字で入力してください。";
        } else if (description.length() > Game.DESCRIPTION_MAX_LENGTH) {
            error = "ゲームの説明は" + Game.DESCRIPTION_MAX_LENGTH + "文字以内で入力してください。";
        } else if (!images().contains(image)) {
            error = "一覧から画像を選択してください。";
        }
        if (error != null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("error", error);
            render(request, response);
            return;
        }
        try {
            if (editingGame == null) {
                games.create(title, amount, genre, image, description);
            } else if (!games.update(editingGame.getId(), title, amount, genre, image, description)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Game registration failed", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "ゲームを保存できませんでした。入力内容を確認して、もう一度お試しください。");
            render(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/admin/games?" + (editingGame == null ? "created=1" : "updated=1"));
    }

    private boolean editing(HttpServletRequest request) { return "/admin/games/edit".equals(request.getServletPath()); }

    private Game loadEditGame(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Game game = games.findById(request.getParameter("id"));
            if (game == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); }
            else { request.setAttribute("editingGame", game); }
            return game;
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Game edit lookup failed", e);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to load game");
            return null;
        }
    }

    private String value(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    private List<String> images() {
        Set<String> resources = getServletContext().getResourcePaths("/images/");
        if (resources == null) { return List.of(); }
        return resources.stream().filter(path -> path.toLowerCase(Locale.ROOT).matches("/images/[a-z0-9_.-]+\\.(png|jpg|jpeg|webp|gif)"))
                .map(path -> path.substring(1)).sorted().toList();
    }

    private void render(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("descriptionLimit", Game.DESCRIPTION_MAX_LENGTH);
        request.setAttribute("imageOptions", images());
        if (editing(request)) {
            request.getRequestDispatcher("/WEB-INF/views/admin-game-edit.jsp").forward(request, response);
            return;
        }
        try {
            request.setAttribute("games", games.findAll());
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Administrator game list failed", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            if (request.getAttribute("error") == null) {
                request.setAttribute("error", "ゲーム一覧を取得できませんでした。");
            }
            request.setAttribute("games", List.of());
        }
        request.getRequestDispatcher("/WEB-INF/views/admin-games.jsp").forward(request, response);
    }
}
