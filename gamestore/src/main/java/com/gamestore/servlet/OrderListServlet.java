package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.gamestore.model.Order;
import com.gamestore.repository.OrderRepository;
import com.gamestore.util.AuthSession;

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

        if (orderRepository == null) {
            throw new ServletException("OrderRepository is not initialized");
        }
        List<Order> result;
        try {
            result = orderRepository.findForUser(AuthSession.currentUser(request).getId(), orderId);
        } catch (SQLException | IOException | ClassNotFoundException e) {
            throw new ServletException("Could not retrieve orders", e);
        }
        request.setAttribute("orders", result);
        request.getRequestDispatcher("/orders.jsp")
                .forward(request, response);
    }
}
