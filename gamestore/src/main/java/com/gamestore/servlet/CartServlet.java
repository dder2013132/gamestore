package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/cart")
public class CartServlet extends HttpServlet {

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        // 1. 폼에서 gameId 가져오기
        String gameId = request.getParameter("gameId");

        // 2. null 또는 빈 문자열 검사
        if (gameId == null || gameId.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "gameId is required");
            return;
        }

        // 3. GameRepository에서 게임 검색
        JdbcGameRepository gameRepository = new JdbcGameRepository();
        Game game;
        try {
            game = gameRepository.findById(gameId);
        } catch (SQLException | IOException | ClassNotFoundException e) {
            throw new ServletException("Error retrieving game details", e);
        }
        // 4. 존재하지 않으면 404 반환
        if (game == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Game not found");
            return;
        }

        HttpSession session = request.getSession();

        @SuppressWarnings("unchecked")
        List<Game> cart = (List<Game>) session.getAttribute("cart");

        if (cart == null) {
            cart = new ArrayList<>();
            session.setAttribute("cart", cart);
        }
        for (Game g : cart) {
            if (g.getId().equals(game.getId())) {
                // 이미 장바구니에 있는 경우 추가하지 않고 종료
                session.setAttribute("cartMessage", "이미 장바구니에 있는 게임입니다.");
                response.sendRedirect(request.getContextPath() + "/cart");
                return;
            }
        }
        cart.add(game);
        response.sendRedirect(request.getContextPath() + "/cart");
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        List<Game> cart = null;

        if (session != null) {
            @SuppressWarnings("unchecked")
            List<Game> savedCart = (List<Game>) session.getAttribute("cart");
            String message = (String) session.getAttribute("cartMessage");
            if (message != null) {
                request.setAttribute("cartMessage", message);
                session.removeAttribute("cartMessage");
                // 여기서 세션의 cartMessage를 삭제!
            }
            cart = savedCart;
        }

        if (cart == null) {
            cart = new ArrayList<>();
        }

        int totalPrice = 0;
        for (Game g : cart) {
            totalPrice += g.getPrice();
        }

        request.setAttribute("cart", cart);
        request.setAttribute("totalPrice", totalPrice);
        request.getRequestDispatcher("/cart.jsp")
                .forward(request, response);
    }
}
