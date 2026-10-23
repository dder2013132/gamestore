package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;

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
            HttpServletResponse response) throws ServletException, IOException {
        JdbcGameRepository gameRepository = new JdbcGameRepository();
        List<Game> games;

        try {
            games = gameRepository.findAll();
        } catch (SQLException | ClassNotFoundException e) {
            throw new ServletException("게임 목록 조회 실패", e);
        }

        System.out.println("DEBUG games size: " + games.size());
        System.out.println("DEBUG working directory: " + System.getProperty("user.dir"));

        request.setAttribute("games", games);
        request.getRequestDispatcher("/index.jsp")
                .forward(request, response);
    }
}
