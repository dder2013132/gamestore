package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.gamestore.model.AdminOrder;
import com.gamestore.model.AdminOrderPage;
import com.gamestore.model.OrderStatus;
import com.gamestore.repository.JdbcAdminOrderRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

/** AuthFilter checks the current database role before either route. No order mutations are exposed. */
@WebServlet(urlPatterns = {"/admin/orders", "/admin/orders/detail"})
public class AdminOrderServlet extends HttpServlet {
    private final JdbcAdminOrderRepository orders;
    public AdminOrderServlet() { this(new JdbcAdminOrderRepository()); }
    AdminOrderServlet(JdbcAdminOrderRepository orders) { this.orders = orders; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String query = value(request, "q");
        String fromText = value(request, "from"), toText = value(request, "to");
        String status = value(request, "status");
        request.setAttribute("query", query);
        request.setAttribute("fromDate", fromText);
        request.setAttribute("toDate", toText);
        request.setAttribute("selectedStatus", status);
        request.setAttribute("orderStatuses", OrderStatus.values());
        request.setAttribute("requestedPage", value(request, "page"));
        boolean detail = "/admin/orders/detail".equals(request.getServletPath());
        String view = detail ? "/WEB-INF/views/admin-order-detail.jsp" : "/WEB-INF/views/admin-orders.jsp";
        try {
            if (detail) {
                String id = value(request, "id");
                if (id.isEmpty() || id.length() > 36) { response.sendError(400, "Invalid order ID"); return; }
                AdminOrder order = orders.findById(id);
                if (order == null) { response.sendError(404, "Order not found"); return; }
                request.setAttribute("adminOrder", order);
            } else {
                LocalDate from = date(fromText), to = date(toText);
                if (!status.isEmpty()) {
                    try { OrderStatus.valueOf(status); }
                    catch (IllegalArgumentException e) { throw new IllegalArgumentException("注文の状態を正しく選んでください。"); }
                }
                if (query.length() > 100) { throw new IllegalArgumentException("検索キーワードは100文字以内で入力してください。"); }
                if (from != null && to != null && from.isAfter(to)) {
                    throw new IllegalArgumentException("開始日は終了日以前の日付を選んでください。");
                }
                int page = 1;
                try { page = Integer.parseInt(request.getParameter("page")); } catch (NumberFormatException ignored) { }
                AdminOrderPage result = orders.findPage(query, from, to, status, page, 10);
                request.setAttribute("orders", result.orders());
                request.setAttribute("resultCount", result.resultCount());
                request.setAttribute("totalPrice", result.totalPrice());
                request.setAttribute("pageNumber", result.page());
                request.setAttribute("pageCount", result.pageCount());
                request.setAttribute("firstResult", result.firstResult());
                request.setAttribute("lastResult", result.lastResult());
                request.setAttribute("pageStart", Math.max(1, result.page() - 2));
                request.setAttribute("pageEnd", Math.min(result.pageCount(), result.page() + 2));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            request.setAttribute("error", e.getMessage());
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Administrator order retrieval failed", e);
            response.setStatus(503);
            request.setAttribute("error", "注文情報を取得できませんでした。しばらくしてから、もう一度お試しください。");
        }
        request.getRequestDispatcher(view).forward(request, response);
    }

    private static String value(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }
    private static LocalDate date(String text) {
        if (text.isEmpty()) { return null; }
        try {
            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) { throw new DateTimeParseException("Invalid date", text, 0); }
            LocalDate date = LocalDate.parse(text);
            if (date.getYear() < 1) { throw new DateTimeParseException("Invalid year", text, 0); }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日付は有効な年月日（YYYY-MM-DD）で入力してください。");
        }
    }
}
