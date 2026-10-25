package com.gamestore.filter;

import java.io.IOException;
import java.util.Set;
import java.sql.SQLException;

import com.gamestore.util.AuthSession;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcUserRepository;
import com.gamestore.repository.JdbcOwnershipRepository;
import com.gamestore.service.OwnershipView;
import com.gamestore.service.WishlistJson;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter("/*")
public class AuthFilter implements Filter {
    private final JdbcUserRepository users;
    private final JdbcOwnershipRepository ownership;

    public AuthFilter() { this(new JdbcUserRepository()); }
    AuthFilter(JdbcUserRepository users) { this(users, new JdbcOwnershipRepository()); }
    AuthFilter(JdbcUserRepository users, JdbcOwnershipRepository ownership) { this.users = users; this.ownership = ownership; }
    private static final Set<String> OWNERSHIP_PATHS = Set.of("/home", "/browse", "/games", "/wishlist",
            "/cart", "/checkout", "/order", "/order/complete", "/order/demo-pay", "/order/cancel");
    private static final Set<String> PRIVATE_PATHS = Set.of(
            "/account", "/account/password", "/account/reviews", "/library",
            "/reviews/edit", "/reviews/create", "/reviews/update", "/reviews/delete",
            "/cart", "/cart/remove", "/cart.jsp",
            "/wishlist", "/wishlist/add", "/wishlist/remove",
            "/checkout", "/order", "/orders", "/order/complete", "/order/demo-pay", "/order/cancel",
            "/checkout.jsp", "/orders.jsp", "/order-complete.jsp");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        request.setCharacterEncoding("UTF-8");
        String path = request.getServletPath();
        boolean wishlistJson="POST".equalsIgnoreCase(request.getMethod())
                && ("/wishlist/add".equals(path)||"/wishlist/remove".equals(path)) && WishlistJson.requested(request);
        if (path.startsWith("/css/") || path.startsWith("/images/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        boolean adminPath = path.equals("/admin") || path.startsWith("/admin/");
        if ((PRIVATE_PATHS.contains(path) || adminPath) && AuthSession.currentUser(request) == null) {
            String destination;
            if (adminPath) {
                destination = path.equals("/admin") || path.equals("/admin/dashboard") ? "/admin/dashboard"
                        : path.equals("/admin/orders") || path.startsWith("/admin/orders/") ? "/admin/orders" : "/admin/games";
            } else if (path.equals("/account") || path.equals("/account/password") || path.equals("/account/reviews") || path.equals("/library")) {
                destination = path;
            } else if (path.startsWith("/reviews/")) {
                destination = AuthSession.gameDestination(request.getParameter("gameId"));
            } else if (path.startsWith("/wishlist")) {
                String gameId = request.getParameter("gameId");
                destination = gameId == null ? "/wishlist" : AuthSession.gameDestination(gameId);
            } else if (path.equals("/cart") || path.equals("/cart/remove") || path.equals("/cart.jsp")) {
                // Return to the product after login; never replay an unauthenticated POST.
                destination = path.equals("/cart") && "POST".equalsIgnoreCase(request.getMethod())
                        ? AuthSession.gameDestination(request.getParameter("gameId")) : "/cart";
            } else {
                destination = path.startsWith("/orders") || path.startsWith("/order/")
                        || path.equals("/order-complete.jsp") ? "/orders" : "/checkout";
            }
            request.getSession().setAttribute("loginDestination", destination);
            if(wishlistJson) {
                WishlistJson.error(response,401,"login_required","ログインの有効期限が切れました。ログインし直してからお試しください。"); return;
            }
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (adminPath) {
            try {
                // Re-check the database so revoking a role takes effect during an existing session.
                User current = users.findById(AuthSession.currentUser(request).getId());
                if (current == null || !current.isAdmin()) {
                    if (current != null) { request.getSession().setAttribute("loginUser", current); }
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Administrator access is required");
                    return;
                }
                request.getSession().setAttribute("loginUser", current);
            } catch (SQLException | IOException | ClassNotFoundException e) {
                request.getServletContext().log("Administrator role check failed", e);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to check administrator access");
                return;
            }
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && !AuthSession.validCsrf(request)) {
            if(wishlistJson) {
                WishlistJson.error(response,403,"csrf_invalid","ページの有効期限が切れました。ページを読み直してからお試しください。"); return;
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token. Reload the page and try again.");
            return;
        }
        AuthSession.csrfToken(request.getSession());
        if (OWNERSHIP_PATHS.contains(path)) {
            try { OwnershipView.populate(request, ownership); }
            catch (SQLException | IOException | ClassNotFoundException e) {
                request.getServletContext().log("Ownership check failed", e);
                response.setStatus(503);
                request.getRequestDispatcher("/WEB-INF/views/ownership-error.jsp").forward(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
