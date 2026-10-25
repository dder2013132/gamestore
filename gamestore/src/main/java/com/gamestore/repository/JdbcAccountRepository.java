package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

public class JdbcAccountRepository {
    @FunctionalInterface
    interface ConnectionProvider { Connection open() throws SQLException, IOException, ClassNotFoundException; }
    private final ConnectionProvider connections;
    public JdbcAccountRepository() { this(DBConnection::getConnection); }
    JdbcAccountRepository(ConnectionProvider connections) { this.connections = connections; }

    public AccountOverview findForUser(long userId) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open()) {
            conn.setReadOnly(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                String username;
                LocalDateTime joined;
                long orderCount, wishCount, libraryCount;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT u.username, u.created_at, "
                        + "(SELECT COUNT(*) FROM orders o WHERE o.user_id = u.user_id) AS order_count, "
                        + "(SELECT COUNT(*) FROM wishlist_items w WHERE w.user_id = u.user_id) AS wish_count, "
                        + "(SELECT COUNT(DISTINCT i.game_id) FROM orders o JOIN order_items i ON i.order_id = o.order_id "
                        + "WHERE o.user_id = u.user_id AND o.status = 'COMPLETED') AS library_count "
                        + "FROM users u WHERE u.user_id = ?")) {
                    stmt.setLong(1, userId);
                    try (ResultSet rows = stmt.executeQuery()) {
                        if (!rows.next()) { conn.commit(); return null; }
                        username = rows.getString("username"); joined = rows.getObject("created_at", LocalDateTime.class);
                        orderCount = rows.getLong("order_count"); wishCount = rows.getLong("wish_count");
                        libraryCount = rows.getLong("library_count");
                    }
                }
                List<Order> orders = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT order_id, total_price, created_at, status, completed_at, cancelled_at "
                        + "FROM orders WHERE user_id = ? ORDER BY created_at DESC, order_id DESC LIMIT 3")) {
                    stmt.setLong(1, userId);
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            orders.add(new Order(rows.getString("order_id"), List.of(), rows.getInt("total_price"), userId,
                                    rows.getObject("created_at", LocalDateTime.class), OrderStatus.valueOf(rows.getString("status")),
                                    rows.getObject("completed_at", LocalDateTime.class), rows.getObject("cancelled_at", LocalDateTime.class)));
                        }
                    }
                }
                List<Game> wishes = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT g.game_id, g.title, g.price, g.genre, g.image_url, g.is_active "
                        + "FROM wishlist_items w JOIN games g ON g.game_id = w.game_id WHERE w.user_id = ? "
                        + "ORDER BY w.created_at DESC, w.game_id DESC LIMIT 3")) {
                    stmt.setLong(1, userId);
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            wishes.add(new Game(rows.getString("game_id"), rows.getString("title"), rows.getInt("price"),
                                    rows.getString("genre"), rows.getString("image_url"), null, rows.getBoolean("is_active")));
                        }
                    }
                }
                conn.commit();
                return new AccountOverview(username, joined, orderCount, wishCount, libraryCount, orders, wishes);
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
}
