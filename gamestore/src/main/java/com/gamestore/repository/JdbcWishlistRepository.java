package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import com.gamestore.model.Game;
import com.gamestore.model.CatalogPage;
import com.gamestore.util.DBConnection;

public class JdbcWishlistRepository {
    @FunctionalInterface interface ConnectionProvider { Connection open() throws SQLException, IOException, ClassNotFoundException; }
    private final ConnectionProvider connections;
    public JdbcWishlistRepository() { this(DBConnection::getConnection); }
    JdbcWishlistRepository(ConnectionProvider connections) { this.connections = connections; }

    public Set<String> findIdsForUser(long userId) throws SQLException, IOException, ClassNotFoundException {
        Set<String> ids = new HashSet<>();
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement("SELECT game_id FROM wishlist_items WHERE user_id = ?")) {
            stmt.setLong(1, userId);
            try (ResultSet rows = stmt.executeQuery()) { while (rows.next()) { ids.add(rows.getString(1)); } }
        }
        return Set.copyOf(ids);
    }

    public boolean add(long userId, int gameId) throws SQLException, IOException, ClassNotFoundException {
        String sql = "WITH candidate AS (SELECT game_id FROM games WHERE game_id = ?), added AS ("
                + "INSERT INTO wishlist_items (user_id, game_id) SELECT ?, game_id FROM candidate "
                + "ON CONFLICT (user_id, game_id) DO NOTHING RETURNING game_id) SELECT EXISTS (SELECT 1 FROM candidate)";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gameId);
            stmt.setLong(2, userId);
            try (ResultSet rows = stmt.executeQuery()) { rows.next(); return rows.getBoolean(1); }
        }
    }

    public void remove(long userId, int gameId) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM wishlist_items WHERE user_id = ? AND game_id = ?")) {
            stmt.setLong(1, userId); stmt.setInt(2, gameId); stmt.executeUpdate();
        }
    }

    public CatalogPage findPageForUser(long userId, int requestedPage, int pageSize) throws SQLException, IOException, ClassNotFoundException {
        if (pageSize < 1 || pageSize > 100) { throw new IllegalArgumentException("Invalid wishlist page size"); }
        try (Connection conn = connections.open()) {
            conn.setReadOnly(true); conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); conn.setAutoCommit(false);
            try {
                int count;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM wishlist_items WHERE user_id = ?")) {
                    stmt.setLong(1, userId);
                    try (ResultSet rows = stmt.executeQuery()) { rows.next(); count = rows.getInt(1); }
                }
                int page = Math.max(1, Math.min(requestedPage, Math.max(1, Math.ceilDiv(count, pageSize))));
                List<Game> games = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT g.game_id, g.title, g.price, g.genre, g.image_url, g.description, g.is_active "
                        + "FROM wishlist_items w JOIN games g ON g.game_id = w.game_id WHERE w.user_id = ? "
                        + "ORDER BY w.created_at DESC, w.game_id DESC LIMIT ? OFFSET ?")) {
                    stmt.setLong(1, userId); stmt.setInt(2, pageSize); stmt.setInt(3, (page - 1) * pageSize);
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) { games.add(new Game(rows.getString("game_id"), rows.getString("title"), rows.getInt("price"),
                                rows.getString("genre"), rows.getString("image_url"), rows.getString("description"), rows.getBoolean("is_active"))); }
                    }
                }
                conn.commit();
                return new CatalogPage(games, List.of(), count, count, page, pageSize);
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
}
