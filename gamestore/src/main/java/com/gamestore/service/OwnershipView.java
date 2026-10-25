package com.gamestore.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import com.gamestore.model.Game;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcOwnershipRepository;
import com.gamestore.util.AuthSession;
import jakarta.servlet.http.HttpServletRequest;

public final class OwnershipView {
    private OwnershipView() { }
    public static void populate(HttpServletRequest request, JdbcOwnershipRepository repository)
            throws SQLException, IOException, ClassNotFoundException {
        User user = AuthSession.currentUser(request);
        Map<String, Boolean> owned = new HashMap<>();
        if (user != null) { repository.findIdsForUser(user.getId()).forEach(id -> owned.put(id, true)); }
        request.setAttribute("ownedIds", Map.copyOf(owned));
    }
    public static boolean owns(HttpServletRequest request, String gameId) {
        Object ids = request.getAttribute("ownedIds");
        return ids instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get(gameId));
    }
    public static boolean overlaps(HttpServletRequest request, List<Game> games) {
        return games.stream().anyMatch(game -> owns(request, game.getId()));
    }
}
