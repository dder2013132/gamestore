package com.gamestore.util;

import java.util.UUID;

import com.gamestore.model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class AuthSession {
    private AuthSession() { }

    public static User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("loginUser");
    }

    public static String csrfToken(HttpSession session) {
        String token = (String) session.getAttribute("csrfToken");
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute("csrfToken", token);
        }
        return token;
    }

    public static boolean validCsrf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute("csrfToken");
        return expected != null && expected.equals(request.getParameter("csrfToken"));
    }

    public static void login(HttpServletRequest request, User user) {
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute("loginUser", user);
        session.setAttribute("csrfToken", UUID.randomUUID().toString());
        session.setMaxInactiveInterval(30 * 60);
    }

    public static String takeDestination(HttpSession session) {
        String destination = (String) session.getAttribute("loginDestination");
        session.removeAttribute("loginDestination");
        if (destination != null && destination.startsWith("/games?id=")) {
            return gameDestination(destination.substring("/games?id=".length()));
        }
        // Only these local GET endpoints are allowed after login.
        return "/library".equals(destination) || "/account".equals(destination) || "/account/password".equals(destination) || "/account/reviews".equals(destination)
                || "/wishlist".equals(destination) || "/cart".equals(destination) || "/checkout".equals(destination) || "/orders".equals(destination)
                || "/admin/dashboard".equals(destination) || "/admin/games".equals(destination) || "/admin/orders".equals(destination) ? destination : "/home";
    }

    public static String gameDestination(String gameId) {
        try {
            int id = Integer.parseInt(gameId);
            return id > 0 ? "/games?id=" + id : "/cart";
        } catch (NumberFormatException e) {
            return "/cart";
        }
    }
}
