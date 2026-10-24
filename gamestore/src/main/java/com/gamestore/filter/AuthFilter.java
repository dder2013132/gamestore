package com.gamestore.filter;

import java.io.IOException;
import java.util.Set;
import java.sql.SQLException;

import com.gamestore.util.AuthSession;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcUserRepository;

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

    public AuthFilter() { this(new JdbcUserRepository()); }
    AuthFilter(JdbcUserRepository users) { this.users = users; }
    private static final Set<String> PRIVATE_PATHS = Set.of(
            "/cart", "/cart/remove", "/cart.jsp",
            "/wishlist", "/wishlist/add", "/wishlist/remove",
            "/checkout", "/order", "/orders", "/order/complete",
            "/checkout.jsp", "/orders.jsp", "/order-complete.jsp");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        request.setCharacterEncoding("UTF-8");
        String path = request.getServletPath();
        if (path.startsWith("/css/") || path.startsWith("/images/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        boolean adminPath = path.equals("/admin") || path.startsWith("/admin/");
        if ((PRIVATE_PATHS.contains(path) || adminPath) && AuthSession.currentUser(request) == null) {
            String destination;
            if (adminPath) {
                destination = "/admin/games";
            } else if (path.startsWith("/wishlist")) {
                String gameId = request.getParameter("gameId");
                destination = gameId == null ? "/wishlist" : AuthSession.gameDestination(gameId);
            } else if (path.equals("/cart") || path.equals("/cart/remove") || path.equals("/cart.jsp")) {
                // Return to the product after login; never replay an unauthenticated POST.
                destination = path.equals("/cart") && "POST".equalsIgnoreCase(request.getMethod())
                        ? AuthSession.gameDestination(request.getParameter("gameId")) : "/cart";
            } else {
                destination = path.startsWith("/orders") || path.equals("/order/complete")
                        || path.equals("/order-complete.jsp") ? "/orders" : "/checkout";
            }
            request.getSession().setAttribute("loginDestination", destination);
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
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token. Reload the page and try again.");
            return;
        }
        AuthSession.csrfToken(request.getSession());
        chain.doFilter(request, response);
    }
}
