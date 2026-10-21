package com.gamestore.servlet;

import java.io.IOException;

import com.gamestore.model.Game;
import com.gamestore.repository.GameRepository;

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

        GameRepository gameRepository = new GameRepository();
        Game game = gameRepository.findById(gameId);

        if (game == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Game not found");
            return;
        }

        request.setAttribute("game", game);
        request.getRequestDispatcher("/game-detail.jsp")
        .forward(request, response);
    }
}