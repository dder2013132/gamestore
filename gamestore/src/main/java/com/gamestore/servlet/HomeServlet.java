package com.gamestore.servlet;

import java.io.IOException;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.repository.GameRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/home")
public class HomeServlet extends HttpServlet {
    @Override 
    protected void doGet(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws ServletException, IOException {
        GameRepository gameRepository = new GameRepository();
        List<Game> games = gameRepository.findAll();

        request.setAttribute("games", games);
        request.getRequestDispatcher("/index.jsp")
                .forward(request, response);
    }
}
