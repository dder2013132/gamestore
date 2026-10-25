package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.AdminOrder;
import com.gamestore.model.AdminOrderPage;
import com.gamestore.model.Game;
import com.gamestore.model.Order;
import com.gamestore.model.OrderStatus;
import com.gamestore.util.DBConnection;

/** Global order queries belong here; member queries always retain their user predicate. */
public class JdbcAdminOrderRepository {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }
    private final ConnectionProvider connections;
    public JdbcAdminOrderRepository() { this(DBConnection::getConnection); }
    JdbcAdminOrderRepository(ConnectionProvider connections) { this.connections = connections; }

    public AdminOrderPage findPage(String query, LocalDate from, LocalDate to, int requestedPage, int pageSize)
            throws SQLException, IOException, ClassNotFoundException {
        return findPage(query, from, to, "", requestedPage, pageSize);
    }

    public AdminOrderPage findPage(String query, LocalDate from, LocalDate to, String status, int requestedPage, int pageSize)
            throws SQLException, IOException, ClassNotFoundException {
        if (pageSize < 1 || pageSize > 100) { throw new IllegalArgumentException("Invalid page size"); }
        String where = " FROM orders o JOIN users u ON u.user_id = o.user_id WHERE TRUE";
        List<Object> parameters = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            OrderStatus.valueOf(status);
            where += " AND o.status = ?"; parameters.add(status);
        }
        if (query != null && !query.isBlank()) {
            where += " AND (POSITION(LOWER(?) IN LOWER(u.username)) > 0 OR POSITION(LOWER(?) IN LOWER(o.order_id)) > 0)";
            parameters.add(query); parameters.add(query);
        }
        if (from != null) { where += " AND o.created_at >= ?"; parameters.add(from.atStartOfDay()); }
        if (to != null) { where += " AND o.created_at < ?"; parameters.add(to.plusDays(1).atStartOfDay()); }
        // Summary and page use one snapshot, even while members place new orders.
        try (Connection conn = connections.open()) {
            conn.setReadOnly(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                int count;
                long total;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*), COALESCE(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_price ELSE 0 END), 0)" + where)) {
                    bind(stmt, parameters);
                    try (ResultSet rs = stmt.executeQuery()) { rs.next(); count = rs.getInt(1); total = rs.getLong(2); }
                }
                int page = Math.min(Math.max(1, requestedPage), Math.max(1, Math.ceilDiv(count, pageSize)));
                List<AdminOrder> orders = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT o.order_id, o.total_price, o.user_id, o.created_at, o.status, o.completed_at, o.cancelled_at, u.username, "
                        + "(SELECT COUNT(*) FROM order_items i WHERE i.order_id = o.order_id) AS item_count" + where
                        + " ORDER BY o.created_at DESC, o.order_id DESC LIMIT ? OFFSET ?")) {
                    bind(stmt, parameters);
                    stmt.setInt(parameters.size() + 1, pageSize);
                    stmt.setLong(parameters.size() + 2, (long) (page - 1) * pageSize);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            orders.add(new AdminOrder(readOrder(rs, List.of()), rs.getString("username"), rs.getInt("item_count")));
                        }
                    }
                }
                conn.commit();
                return new AdminOrderPage(orders, count, total, page, pageSize);
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    public AdminOrder findById(String id) throws SQLException, IOException, ClassNotFoundException {
        String sql = "SELECT o.order_id, o.total_price, o.user_id, o.created_at, o.status, o.completed_at, o.cancelled_at, u.username, "
                + "i.item_id, i.game_id, i.price AS purchase_price, g.title, g.genre, g.image_url "
                + "FROM orders o JOIN users u ON u.user_id = o.user_id "
                + "LEFT JOIN order_items i ON i.order_id = o.order_id LEFT JOIN games g ON g.game_id = i.game_id "
                + "WHERE o.order_id = ? ORDER BY i.item_id";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) { return null; }
                Order order = readOrder(rs, List.of());
                String username = rs.getString("username");
                List<Game> games = new ArrayList<>();
                do {
                    if (rs.getObject("item_id") != null) {
                        games.add(new Game(rs.getString("game_id"), rs.getString("title"), rs.getInt("purchase_price"),
                                rs.getString("genre"), rs.getString("image_url")));
                    }
                } while (rs.next());
                return new AdminOrder(new Order(order.getOrderId(), games, order.getTotalPrice(), order.getUserId(),
                        order.getCreatedAt(), order.getStatus(), order.getCompletedAt(), order.getCancelledAt()), username, games.size());
            }
        }
    }

    private static Order readOrder(ResultSet rs, List<Game> games) throws SQLException {
        return new Order(rs.getString("order_id"), games, rs.getInt("total_price"), rs.getLong("user_id"),
                rs.getObject("created_at", LocalDateTime.class), OrderStatus.valueOf(rs.getString("status")),
                rs.getObject("completed_at", LocalDateTime.class), rs.getObject("cancelled_at", LocalDateTime.class));
    }
    private static void bind(PreparedStatement stmt, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) { stmt.setObject(i + 1, parameters.get(i)); }
    }
}
