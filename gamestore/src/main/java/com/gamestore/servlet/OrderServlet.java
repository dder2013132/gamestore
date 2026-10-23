package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gamestore.model.Game;
import com.gamestore.model.Order;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.repository.OrderRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/order")
public class OrderServlet extends HttpServlet {

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        // 1. 기존 세션 가져오기
        HttpSession session = request.getSession(false);

        if (session == null) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Cart is empty");
            return;
        }

        @SuppressWarnings("unchecked")
        List<Game> cart = (List<Game>) session.getAttribute("cart");

        if (cart == null || cart.isEmpty()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Cart is empty");
            return;
        }

        // 4. Repository에서 상품을 다시 조회하고 총액 계산

        JdbcGameRepository gameRepository = new JdbcGameRepository();

        List<Game> orderGames = new ArrayList<>();
        int total = 0;

        try {
            for (Game cartGame : cart) {
                Game game = gameRepository.findById(cartGame.getId());

            if (game == null) {
                response.sendError(
                        HttpServletResponse.SC_CONFLICT,
                        "Game is no longer available");
                return;
            }

                orderGames.add(game);
                total += game.getPrice();
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            throw new ServletException("Error retrieving game information", e);
        }
        // 5. 모의 주문 완료 처리
        OrderRepository orderRepository =
            (OrderRepository) getServletContext()
                .getAttribute("orderRepository");
        
        if (orderRepository == null) {
            throw new ServletException("OrderRepository is not initialized");
        }

        String orderId = UUID.randomUUID().toString();

        Order order = new Order(
            orderId,
            orderGames,
            total);
        orderRepository.save(order);

        session.removeAttribute("cart");

        response.sendRedirect(
            request.getContextPath() + "/order/complete?id=" + orderId
        );
    }
}