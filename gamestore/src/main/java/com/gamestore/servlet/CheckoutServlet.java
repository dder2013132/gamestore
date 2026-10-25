package com.gamestore.servlet;

import java.io.IOException;
import java.util.List;
import java.sql.SQLException;
import com.gamestore.model.Game;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.service.CartCatalog;
import com.gamestore.service.OwnershipView;
import com.gamestore.util.AuthSession;
import com.gamestore.util.CheckoutTokens;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {
    private final JdbcGameRepository games;
    public CheckoutServlet() { this(new JdbcGameRepository()); }
    CheckoutServlet(JdbcGameRepository games) { this.games = games; }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = AuthSession.currentUser(request);
        if (session == null || user == null) { response.sendRedirect(request.getContextPath() + "/login"); return; }
        synchronized (session) {
            @SuppressWarnings("unchecked") List<Game> cart = (List<Game>) session.getAttribute("cart");
            if (cart == null || cart.isEmpty()) { response.sendRedirect(request.getContextPath() + "/cart"); return; }
            try { cart = CartCatalog.refresh(cart, games); }
            catch (SQLException | IOException | ClassNotFoundException e) { throw new ServletException("Unable to refresh checkout", e); }
            session.setAttribute("cart", cart);
            if (OwnershipView.overlaps(request, cart)) {
                session.setAttribute("cartMessage", "すでにライブラリにあるゲームが含まれています。該当するゲームをカートから削除してください。");
                response.sendRedirect(request.getContextPath() + "/cart");
                return;
            }
            if (CartCatalog.blocked(cart)) {
                session.setAttribute("cartMessage", "販売休止中の商品、または金額上限を超える商品があります。カートを確認してください。");
                response.sendRedirect(request.getContextPath() + "/cart");
                return;
            }
            request.setAttribute("cart", List.copyOf(cart));
            request.setAttribute("total", cart.stream().mapToLong(Game::getPrice).sum());
            request.setAttribute("checkoutToken", CheckoutTokens.prepare(session, user.getId(), cart).token());
        }
        request.getRequestDispatcher("/checkout.jsp").forward(request, response);
    }
}
