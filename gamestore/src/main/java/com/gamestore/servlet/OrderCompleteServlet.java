package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;

import com.gamestore.model.Order;
import com.gamestore.repository.OrderRepository;
import com.gamestore.util.AuthSession;
import com.gamestore.service.OwnershipView;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/order/complete")
public class OrderCompleteServlet extends HttpServlet {

    @Override
    protected void doGet(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws ServletException, IOException {

        // 1. URL에서 id 가져오기
        String orderId = request.getParameter("id");
        // 2. id가 null 또는 빈 문자열이면 400
        if (orderId == null || orderId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Order ID is required");
            return;
        }
        // 3. ServletContext에서 공유 OrderRepository 가져오기
        OrderRepository orderRepository = 
            (OrderRepository) getServletContext().getAttribute("orderRepository");
        if (orderRepository == null) {
            throw new ServletException("OrderRepository is not initialized");
        }
        // 4. DB에서 현재 회원의 주문만 조회
        Order order;
        try {
            order = orderRepository.findByIdForUser(orderId, AuthSession.currentUser(request).getId());
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Could not retrieve member order", e);
            response.setStatus(503);
            request.setAttribute("pageError", "注文情報を取得できませんでした。しばらくしてから注文履歴を確認してください。");
            request.getRequestDispatcher("/order-complete.jsp").forward(request, response);
            return;
        }

        // 5. 주문이 없다면 404
        if (order == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Order not found");
            return;
        }

        // 6. request에 order 전달
        request.setAttribute("order", order);
        request.setAttribute("repurchaseBlocked", order.isPending() && OwnershipView.overlaps(request, order.getGames()));

        // 7. /order-complete.jsp로 forward()
        request.getRequestDispatcher("/order-complete.jsp").forward(request, response);
    }
}
