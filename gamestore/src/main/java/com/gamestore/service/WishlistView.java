package com.gamestore.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcWishlistRepository;
import com.gamestore.util.AuthSession;
import jakarta.servlet.http.HttpServletRequest;

public final class WishlistView {
    private static final Set<String> PAGES = Set.of("/home", "/browse", "/games", "/wishlist");
    private WishlistView() { }

    public static void populate(HttpServletRequest request, JdbcWishlistRepository repository)
            throws SQLException, IOException, ClassNotFoundException {
        User user = AuthSession.currentUser(request);
        Map<String, Boolean> ids = new HashMap<>();
        if (user != null) { for (String id : repository.findIdsForUser(user.getId())) { ids.put(id, true); } }
        request.setAttribute("wishlistIds", ids);
        request.setAttribute("wishlistReturnTo", currentPage(request));
    }

    public static String safeReturn(String value) {
        if (value == null || value.length() > 2048 || value.contains("\\")) { return "/wishlist"; }
        try {
            URI uri = new URI(value);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null || !PAGES.contains(uri.getRawPath())) { return "/wishlist"; }
            return uri.toASCIIString();
        } catch (Exception ignored) { return "/wishlist"; }
    }

    private static String currentPage(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!PAGES.contains(path)) { return "/browse"; }
        List<String> query = new ArrayList<>();
        List<String> names = switch (path) {
            case "/browse" -> List.of("q", "genre", "sort", "page");
            case "/games" -> List.of("id");
            case "/wishlist" -> List.of("page");
            default -> List.of();
        };
        for (String name : names) {
            String value = request.getParameter(name);
            if (value != null) { query.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)); }
        }
        return path + (query.isEmpty() ? "" : "?" + String.join("&", query));
    }
}
