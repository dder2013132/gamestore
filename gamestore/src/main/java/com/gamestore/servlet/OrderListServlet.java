package com.gamestore.servlet;

import java.io.IOException;
import java.util.List;

import com.gamestore.model.Order;
import com.gamestore.repository.OrderRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/orders")
public class OrderListServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        String orderId = request.getParameter("orderId");

        OrderRepository orderRepository = (OrderRepository) getServletContext().getAttribute("orderRepository");

        List<Order> result;

        if (orderId != null && !orderId.isBlank()) {
            result = orderRepository.searchByOrderId(orderId);
        } else {
            result = orderRepository.findAll();
        }
        request.setAttribute("orders", result);
        request.getRequestDispatcher("/orders.jsp")
                .forward(request, response);
    }
}
