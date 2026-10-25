package com.gamestore.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import com.gamestore.model.User;
import com.gamestore.repository.*;
import com.gamestore.util.AuthSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

/** Local demo payment only. Never contacts a payment provider or accepts card data. */
@WebServlet(urlPatterns = {"/order/demo-pay", "/order/cancel"})
public class OrderActionServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        boolean pay = "/order/demo-pay".equals(request.getServletPath());
        if (!pay && !"/order/cancel".equals(request.getServletPath())) { response.sendError(405); return; }
        String id = request.getParameter("id");
        if (id == null || id.isBlank() || id.trim().length() > 36) { response.sendError(400, "Invalid order ID"); return; }
        id = id.trim();
        OrderRepository orders = (OrderRepository) getServletContext().getAttribute("orderRepository");
        if (orders == null) { throw new ServletException("OrderRepository is not initialized"); }
        try {
            OrderChangeResult result = pay ? orders.completeDemoForUser(id, user.getId()) : orders.cancelPendingForUser(id, user.getId());
            if (result == OrderChangeResult.NOT_FOUND) { response.sendError(404, "Order not found"); return; }
            if (result == OrderChangeResult.ALREADY_OWNED || result == OrderChangeResult.CONFLICT) {
                response.setStatus(409);
                request.setAttribute("order", orders.findByIdForUser(id, user.getId()));
                request.setAttribute("repurchaseBlocked", result == OrderChangeResult.ALREADY_OWNED);
                request.setAttribute("actionError", result == OrderChangeResult.ALREADY_OWNED
                        ? "この注文には、すでにライブラリにあるゲームが含まれています。重複購入を防ぐため決済できません。この注文をキャンセルし、未購入のゲームだけで注文し直してください。"
                        : "注文の状態が変わったため、操作できませんでした。支払い待ちの注文のみ、テスト決済・キャンセルができます。");
                request.getRequestDispatcher("/order-complete.jsp").forward(request, response);
                return;
            }
            response.sendRedirect(request.getContextPath() + "/order/complete?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8));
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Demo order transition failed", e);
            response.setStatus(503);
            request.setAttribute("pageError", "操作の結果を確認できませんでした。注文履歴から現在の状態を確認してください。");
            request.getRequestDispatcher("/order-complete.jsp").forward(request, response);
        }
    }
}
