package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

public class JdbcLibraryRepository {
    @FunctionalInterface interface ConnectionProvider { Connection open() throws SQLException, IOException, ClassNotFoundException; }
    private final ConnectionProvider connections;
    public JdbcLibraryRepository() { this(DBConnection::getConnection); }
    JdbcLibraryRepository(ConnectionProvider connections) { this.connections = connections; }
    private static final String OWNED = "WITH owned AS (SELECT i.game_id, MIN(o.completed_at) AS acquired_at FROM orders o "
            + "JOIN order_items i ON i.order_id = o.order_id WHERE o.user_id = ? AND o.status = 'COMPLETED' GROUP BY i.game_id) ";
    private static final String MATCH = "(POSITION(LOWER(?) IN LOWER(g.title)) > 0 OR POSITION(LOWER(?) IN LOWER(g.genre)) > 0)";

    public LibraryPage findForUser(long userId, String query, int requestedPage) throws SQLException, IOException, ClassNotFoundException {
        String keyword = query == null ? "" : query;
        try (Connection conn = connections.open()) {
            conn.setReadOnly(true); conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); conn.setAutoCommit(false);
            try {
                int total, count;
                try (PreparedStatement stmt = conn.prepareStatement(OWNED + "SELECT COUNT(*), COUNT(*) FILTER (WHERE " + MATCH
                        + ") FROM owned JOIN games g ON g.game_id = owned.game_id")) {
                    stmt.setLong(1, userId); stmt.setString(2, keyword); stmt.setString(3, keyword);
                    try (ResultSet rows = stmt.executeQuery()) { rows.next(); total = rows.getInt(1); count = rows.getInt(2); }
                }
                int page = Math.max(1, Math.min(requestedPage, Math.max(1, Math.ceilDiv(count, 9))));
                List<LibraryGame> games = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(OWNED
                        + "SELECT g.game_id, g.title, g.price, g.genre, g.image_url, g.is_active, owned.acquired_at, r.rating "
                        + "FROM owned JOIN games g ON g.game_id = owned.game_id "
                        + "LEFT JOIN reviews r ON r.game_id = g.game_id AND r.user_id = ? WHERE " + MATCH
                        + " ORDER BY owned.acquired_at DESC, g.game_id DESC LIMIT 9 OFFSET ?")) {
                    stmt.setLong(1, userId); stmt.setLong(2, userId); stmt.setString(3, keyword); stmt.setString(4, keyword); stmt.setInt(5, (page - 1) * 9);
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            Game game = new Game(rows.getString("game_id"), rows.getString("title"), rows.getInt("price"), rows.getString("genre"),
                                    rows.getString("image_url"), null, rows.getBoolean("is_active"));
                            Number rating = (Number) rows.getObject("rating");
                            games.add(new LibraryGame(game, rows.getObject("acquired_at", LocalDateTime.class), rating == null ? null : rating.intValue()));
                        }
                    }
                }
                conn.commit(); return new LibraryPage(games, total, count, page, 9);
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); } throw e;
            }
        }
    }
}
