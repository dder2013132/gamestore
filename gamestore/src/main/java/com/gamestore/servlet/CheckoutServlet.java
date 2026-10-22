package com.gamestore.servlet;

import java.io.IOException;
import java.util.List;

import com.gamestore.model.Game;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {

    @Override
    protected void doGet(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws ServletException, IOException {

        // 1. 세션에서 장바구니 가져오기
        HttpSession session = request.getSession(false);
        // 2. 장바구니가 비어 있는지 확인
        if (session == null) {
            response.sendRedirect("cart");
            return;
        }
        @SuppressWarnings("unchecked")
        List<Game> cart = (List<Game>) session.getAttribute("cart");
        if (cart == null || cart.isEmpty()) {
            response.sendRedirect("cart");
            return;
        }
        // 3. 서버의 상품 가격으로 총액 계산
        int total = 0;
        for(Game g : cart) {
            total += g.getPrice();
        }
        // 4. checkout.jsp로 전달
        request.setAttribute("cart", cart);
        request.setAttribute("total", total);
        request.getRequestDispatcher("/checkout.jsp").forward(request, response);
    }
}