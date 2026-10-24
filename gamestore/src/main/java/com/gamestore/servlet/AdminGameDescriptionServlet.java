package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authorization and CSRF checks for all /admin/ routes are enforced by AuthFilter. */
@WebServlet("/admin/games/description")
public class AdminGameDescriptionServlet extends HttpServlet {
    private final JdbcGameRepository games;

    public AdminGameDescriptionServlet() { this(new JdbcGameRepository()); }
    AdminGameDescriptionServlet(JdbcGameRepository games) { this.games = games; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Game game = load(request, response);
        if (game == null) { return; }
        request.setAttribute("formDescription", game.getDescription());
        render(request, response, game);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Game game = load(request, response);
        if (game == null) { return; }
        String submitted = request.getParameter("description");
        if (submitted == null) {
            // An incomplete request must not accidentally clear an existing description.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Description field is required");
            return;
        }
        String description = submitted.trim();
        request.setAttribute("formDescription", description);
        if (description.length() > Game.DESCRIPTION_MAX_LENGTH) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("error", "ゲームの説明は" + Game.DESCRIPTION_MAX_LENGTH + "文字以内で入力してください。");
            render(request, response, game);
            return;
        }
        try {
            if (!games.updateDescription(game.getId(), description)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Game description update failed", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "説明を保存できませんでした。入力内容を確認して、もう一度お試しください。");
            render(request, response, game);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/admin/games/description?id=" + game.getId() + "&saved=1");
    }

    private Game load(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Game game = games.findById(request.getParameter("id"));
            if (game == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); }
            return game;
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Game description lookup failed", e);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to load game");
            return null;
        }
    }

    private void render(HttpServletRequest request, HttpServletResponse response, Game game)
            throws ServletException, IOException {
        request.setAttribute("game", game);
        request.setAttribute("descriptionLimit", Game.DESCRIPTION_MAX_LENGTH);
        request.getRequestDispatcher("/WEB-INF/views/admin-game-description.jsp").forward(request, response);
    }
}
