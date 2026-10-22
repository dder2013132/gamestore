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

        GameRepository gameRepository = new GameRepository();

        int total = 0;

        for (Game cartGame : cart) {
            Game game = gameRepository.findById(cartGame.getId());

            if (game == null) {
                response.sendError(
                        HttpServletResponse.SC_CONFLICT,
                        "Game is no longer available");
                return;
            }

            total += game.getPrice();
        }
        // 5. 모의 주문 완료 처리
    }
}