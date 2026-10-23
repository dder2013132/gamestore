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

@WebServlet("/games")
public class GameDetailServlet extends HttpServlet {

    @Override
    protected void doGet(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws ServletException, IOException {

        String gameId = request.getParameter("id");

        if(gameId == null || gameId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid game ID");
            return;
        }

        Game game;
        JdbcGameRepository gameRepository = new JdbcGameRepository();
        try {
            game = gameRepository.findById(gameId);
        } catch (SQLException | IOException | ClassNotFoundException e) {
            throw new ServletException("Error retrieving game details", e);
        }

        if (game == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Game not found");
            return;
        }

        request.setAttribute("game", game);
        request.getRequestDispatcher("/game-detail.jsp")
        .forward(request, response);
    }
}