package com.gamestore.servlet;

import java.io.IOException;

import com.gamestore.model.Order;
import com.gamestore.repository.OrderRepository;

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
        // 4. findById()로 주문 조회
        Order order = orderRepository.findById(orderId);

        // 5. 주문이 없다면 404
        if (order == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Order not found");
            return;
        }

        // 6. request에 order 전달
        request.setAttribute("order", order);

        // 7. /order-complete.jsp로 forward()
        request.getRequestDispatcher("/order-complete.jsp").forward(request, response);
    }
}