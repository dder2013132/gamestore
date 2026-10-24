package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import com.gamestore.model.Game;
import com.gamestore.model.Order;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.repository.OrderRepository;
import com.gamestore.repository.CatalogConflictException;
import com.gamestore.service.CartCatalog;
import com.gamestore.util.AuthSession;
import com.gamestore.util.CheckoutTokens;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet("/order")
public class OrderServlet extends HttpServlet {
    private final JdbcGameRepository games;
    public OrderServlet() { this(new JdbcGameRepository()); }
    OrderServlet(JdbcGameRepository games) { this.games = games; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = AuthSession.currentUser(request);
        if (session == null || user == null) { response.sendError(401); return; }
        String token = request.getParameter("checkoutToken");
        if (!CheckoutTokens.valid(token)) { response.sendError(400, "Reload checkout to obtain a confirmation token"); return; }
        List<Game> cart;
        synchronized (session) {
            @SuppressWarnings("unchecked") List<Game> saved = (List<Game>) session.getAttribute("cart");
            cart = saved == null ? List.of() : List.copyOf(saved);
        }
        OrderRepository orders = (OrderRepository) getServletContext().getAttribute("orderRepository");
        if (orders == null) { throw new ServletException("OrderRepository is not initialized"); }
        try {
            String completed = orders.findIdByCheckoutToken(token, user.getId());
            if (completed != null) { replay(request, response, completed, token, user.getId()); return; }
            if (cart.isEmpty()) { response.sendError(400, "Cart is empty"); return; }
            CheckoutTokens.Intent intent = CheckoutTokens.find(session, token);
            if (!CheckoutTokens.matches(session, intent, user.getId(), cart)) {
                // A simultaneous request may have committed and removed its session intent meanwhile.
                completed = orders.findIdByCheckoutToken(token, user.getId());
                if (completed != null) { replay(request, response, completed, token, user.getId()); return; }
                error(request, response, cart, 409, "カートの内容が変わったか、確認画面の有効期限が切れました。カートから注文内容を確認し直してください。", true);
                return;
            }
            List<Game> current = CartCatalog.refresh(cart, games);
            if (CartCatalog.blocked(current)) {
                CheckoutTokens.refreshIfCurrent(session, intent, current);
                error(request, response, current, 409, "販売休止中の商品、または金額上限を超える商品があります。カートを確認してください。", true);
                return;
            }
            boolean priceChanged = false;
            int total = 0;
            for (int i = 0; i < current.size(); i++) {
                priceChanged |= cart.get(i).getPrice() != current.get(i).getPrice();
                total = Math.addExact(total, current.get(i).getPrice());
            }
            if (priceChanged) {
                boolean refreshed = CheckoutTokens.refreshIfCurrent(session, intent, current);
                error(request, response, current, 409, refreshed
                        ? "商品の価格が変更されました。新しい金額を確認してから、もう一度注文を確定してください。"
                        : "カートの内容が変わりました。カートから注文内容を確認し直してください。", !refreshed);
                return;
            }
            @SuppressWarnings("unchecked") List<Game> latest = (List<Game>) session.getAttribute("cart");
            if (!CheckoutTokens.matches(session, intent, user.getId(), latest)) {
                error(request, response, cart, 409, "カートの内容が変わりました。注文内容を確認し直してください。", true);
                return;
            }
            String orderId = orders.saveOnce(new Order(UUID.randomUUID().toString(), current, total, user.getId()), token);
            CheckoutTokens.complete(session, intent);
            complete(request, response, orderId);
        } catch (CatalogConflictException e) {
            error(request, response, cart, 409, "商品の価格または販売状況が変更されました。カートで最新の内容を確認してください。", true);
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Order database operation failed", e);
            error(request, response, cart, 503, "注文を確認できませんでした。カートは保持されています。注文履歴を確認してから再度お試しください。", true);
        } catch (ArithmeticException e) {
            error(request, response, cart, 400, "注文金額が上限を超えています。カートを確認してください。", true);
        }
    }
    private void complete(HttpServletRequest request, HttpServletResponse response, String id) throws IOException {
        response.sendRedirect(request.getContextPath() + "/order/complete?id=" + id);
    }
    private void replay(HttpServletRequest request, HttpServletResponse response, String id, String token, long userId) throws IOException {
        CheckoutTokens.Intent pending = CheckoutTokens.find(request.getSession(), token);
        if (pending != null && pending.userId() == userId) { CheckoutTokens.complete(request.getSession(), pending); }
        complete(request, response, id);
    }
    private void error(HttpServletRequest request, HttpServletResponse response, List<Game> cart,
            int status, String message, boolean blocked) throws ServletException, IOException {
        response.setStatus(status);
        request.setAttribute("orderError", message);
        request.setAttribute("orderBlocked", blocked);
        request.setAttribute("cart", cart);
        request.setAttribute("total", cart.stream().mapToLong(Game::getPrice).sum());
        if (!blocked) { request.setAttribute("checkoutToken", CheckoutTokens.prepare(request.getSession(), AuthSession.currentUser(request).getId(), cart).token()); }
        request.getRequestDispatcher("/checkout.jsp").forward(request, response);
    }
}
