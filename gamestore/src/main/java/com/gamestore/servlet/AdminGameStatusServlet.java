package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;

import com.gamestore.repository.JdbcGameRepository;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/admin/games/status")
public class AdminGameStatusServlet extends HttpServlet {
    private final JdbcGameRepository games;
    public AdminGameStatusServlet() { this(new JdbcGameRepository()); }
    AdminGameStatusServlet(JdbcGameRepository games) { this.games = games; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String active = request.getParameter("active");
        if (!"true".equals(active) && !"false".equals(active)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid sale status");
            return;
        }
        try {
            if (!games.setActive(request.getParameter("id"), Boolean.parseBoolean(active))) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Game sale status update failed", e);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to update sale status");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/admin/games?statusUpdated=1");
    }
}
