package com.gamestore.repository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.UUID;

import com.gamestore.model.Game;
import com.gamestore.model.Order;
import com.gamestore.model.OrderStatus;
import com.gamestore.util.DBConnection;

public class JdbcOrderRepository implements OrderRepository {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }

    private final ConnectionProvider connections;

    public JdbcOrderRepository() { this(DBConnection::getConnection); }

    JdbcOrderRepository(ConnectionProvider connections) { this.connections = connections; }

    @Override
    public void save(Order order) throws SQLException, IOException, ClassNotFoundException {
        persist(order, null);
    }

    @Override
    public String saveOnce(Order order, String checkoutToken) throws SQLException, IOException, ClassNotFoundException {
        return persist(order, UUID.fromString(checkoutToken));
    }

    @Override
    public String findIdByCheckoutToken(String checkoutToken, long userId) throws SQLException, IOException, ClassNotFoundException {
        UUID token;
        try { token = UUID.fromString(checkoutToken); }
        catch (IllegalArgumentException | NullPointerException e) { return null; }
        try (Connection conn = connections.open()) { return findToken(conn, token, userId); }
    }

    private String findToken(Connection conn, UUID token, long userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT order_id FROM orders WHERE user_id = ? AND checkout_token = ?")) {
            stmt.setLong(1, userId);
            stmt.setObject(2, token);
            try (ResultSet rows = stmt.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    @Override
    public OrderChangeResult completeDemoForUser(String orderId, long userId) throws SQLException, IOException, ClassNotFoundException {
        return transition(orderId, userId, OrderStatus.COMPLETED);
    }

    @Override
    public OrderChangeResult cancelPendingForUser(String orderId, long userId) throws SQLException, IOException, ClassNotFoundException {
        return transition(orderId, userId, OrderStatus.CANCELLED);
    }

    private OrderChangeResult transition(String orderId, long userId, OrderStatus target)
            throws SQLException, IOException, ClassNotFoundException {
        String sql = target == OrderStatus.COMPLETED
                ? "UPDATE orders SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE order_id = ? AND user_id = ? AND status = 'PENDING'"
                : "UPDATE orders SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE order_id = ? AND user_id = ? AND status = 'PENDING'";
        try (Connection conn = connections.open()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn.setAutoCommit(false);
            try {
                // All order creation and transitions take the same member lock first.
                // A second checkout sees the first checkout's committed ownership.
                if (!lockMember(conn, userId)) { conn.commit(); return OrderChangeResult.NOT_FOUND; }
                String status;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT status FROM orders WHERE order_id = ? AND user_id = ? FOR UPDATE")) {
                    stmt.setString(1, orderId); stmt.setLong(2, userId);
                    try (ResultSet rows = stmt.executeQuery()) {
                        if (!rows.next()) { conn.commit(); return OrderChangeResult.NOT_FOUND; }
                        status = rows.getString(1);
                    }
                }
                if (!"PENDING".equals(status)) {
                    conn.commit();
                    return target.name().equals(status) ? OrderChangeResult.ALREADY_APPLIED : OrderChangeResult.CONFLICT;
                }
                if (target == OrderStatus.COMPLETED) {
                    var owned = JdbcOwnershipRepository.findIds(conn, userId);
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT game_id FROM order_items WHERE order_id = ?")) {
                        stmt.setString(1, orderId);
                        try (ResultSet rows = stmt.executeQuery()) {
                            while (rows.next()) {
                                if (owned.contains(rows.getString(1))) { conn.commit(); return OrderChangeResult.ALREADY_OWNED; }
                            }
                        }
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, orderId); stmt.setLong(2, userId);
                    if (stmt.executeUpdate() != 1) { throw new SQLException("Locked order changed unexpectedly"); }
                }
                conn.commit();
                return OrderChangeResult.CHANGED;
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                throw e;
            }
        }
    }

    private boolean lockMember(Connection conn, long userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE")) {
            stmt.setLong(1, userId);
            try (ResultSet rows = stmt.executeQuery()) { return rows.next(); }
        }
    }

    private String persist(Order order, UUID token) throws SQLException, IOException, ClassNotFoundException {
        if (order.getUserId() == null || order.getGames().isEmpty()) {
            throw new IllegalArgumentException("An order requires a member and at least one game");
        }
        String orderSql = "INSERT INTO orders (order_id, total_price, user_id, created_at, checkout_token) VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT (user_id, checkout_token) DO NOTHING RETURNING order_id";
        String itemSql = "INSERT INTO order_items (order_id, game_id, price) VALUES (?, ?, ?)";

        try (Connection conn = connections.open()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn.setAutoCommit(false);
            try {
                if (!lockMember(conn, order.getUserId())) { throw new SQLException("Order member does not exist"); }
                // The unique key serializes simultaneous submissions before any items are inserted.
                try (PreparedStatement stmt = conn.prepareStatement(orderSql)) {
                    stmt.setString(1, order.getOrderId());
                    stmt.setInt(2, order.getTotalPrice());
                    stmt.setLong(3, order.getUserId());
                    stmt.setObject(4, order.getCreatedAt());
                    stmt.setObject(5, token);
                    try (ResultSet rows = stmt.executeQuery()) {
                        if (!rows.next()) {
                            String existing = findToken(conn, token, order.getUserId());
                            if (existing == null) { throw new SQLException("Checkout conflict returned no order"); }
                            conn.commit();
                            return existing;
                        }
                    }
                }
                var owned = JdbcOwnershipRepository.findIds(conn, order.getUserId());
                if (order.getGames().stream().anyMatch(game -> owned.contains(game.getId()))) { throw new AlreadyOwnedException(); }
                if (order.getGames().stream().map(Game::getId).distinct().count() != order.getGames().size()) {
                    throw new IllegalArgumentException("A game may occur only once in an order");
                }
                // Lock in a consistent order. Admin price/status changes wait until purchase commits.
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT price, is_active FROM games WHERE game_id = ? FOR SHARE")) {
                    List<Game> sorted = order.getGames().stream()
                            .sorted(Comparator.comparingInt(game -> Integer.parseInt(game.getId()))).toList();
                    for (Game game : sorted) {
                        check.setInt(1, Integer.parseInt(game.getId()));
                        try (ResultSet row = check.executeQuery()) {
                            if (!row.next() || !row.getBoolean("is_active") || row.getInt("price") != game.getPrice()) {
                                throw new CatalogConflictException("Game availability or price changed before order commit");
                            }
                        }
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement(itemSql)) {
                    for (Game game : order.getGames()) {
                        stmt.setString(1, order.getOrderId());
                        stmt.setInt(2, Integer.parseInt(game.getId()));
                        stmt.setInt(3, game.getPrice());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                conn.commit();
                return order.getOrderId();
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        }
    }

    @Override
    public Order findByIdForUser(String orderId, long userId)
            throws SQLException, IOException, ClassNotFoundException {
        if (orderId == null || orderId.isBlank()) { return null; }
        List<Order> orders = find(userId, orderId, null);
        return orders.isEmpty() ? null : orders.get(0);
    }

    @Override
    public List<Order> findForUser(long userId, String keyword)
            throws SQLException, IOException, ClassNotFoundException {
        return find(userId, null, keyword == null || keyword.isBlank() ? null : keyword);
    }

    private List<Order> find(long userId, String orderId, String keyword)
            throws SQLException, IOException, ClassNotFoundException {
        String sql = "SELECT o.order_id, o.total_price, o.user_id, o.created_at, o.status, o.completed_at, o.cancelled_at, "
                + "i.item_id, i.game_id, i.price AS purchase_price, g.title, g.genre, g.image_url "
                + "FROM orders o LEFT JOIN order_items i ON i.order_id = o.order_id "
                + "LEFT JOIN games g ON g.game_id = i.game_id WHERE o.user_id = ?";
        if (orderId != null) { sql += " AND o.order_id = ?"; }
        if (keyword != null) { sql += " AND POSITION(? IN o.order_id) > 0"; }
        sql += " ORDER BY o.created_at DESC, o.order_id DESC, i.item_id";

        Map<String, OrderRows> grouped = new LinkedHashMap<>();
        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            if (orderId != null) { stmt.setString(2, orderId); }
            if (keyword != null) { stmt.setString(2, keyword); }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("order_id");
                    OrderRows rows = grouped.get(id);
                    if (rows == null) {
                        rows = new OrderRows(id, rs.getInt("total_price"), rs.getLong("user_id"),
                                rs.getObject("created_at", LocalDateTime.class), OrderStatus.valueOf(rs.getString("status")),
                                rs.getObject("completed_at", LocalDateTime.class), rs.getObject("cancelled_at", LocalDateTime.class));
                        grouped.put(id, rows);
                    }
                    if (rs.getObject("item_id") != null) {
                        // Read the price paid, independently of today's catalog price.
                        rows.games.add(new Game(rs.getString("game_id"), rs.getString("title"),
                                rs.getInt("purchase_price"), rs.getString("genre"), rs.getString("image_url")));
                    }
                }
            }
        }
        List<Order> result = new ArrayList<>();
        for (OrderRows rows : grouped.values()) {
            result.add(new Order(rows.id, rows.games, rows.total, rows.userId, rows.createdAt, rows.status, rows.completedAt, rows.cancelledAt));
        }
        return result;
    }

    private static class OrderRows {
        final String id;
        final int total;
        final long userId;
        final LocalDateTime createdAt;
        final OrderStatus status;
        final LocalDateTime completedAt, cancelledAt;
        final List<Game> games = new ArrayList<>();

        OrderRows(String id, int total, long userId, LocalDateTime createdAt, OrderStatus status,
                LocalDateTime completedAt, LocalDateTime cancelledAt) {
            this.id = id;
            this.total = total;
            this.userId = userId;
            this.createdAt = createdAt;
            this.status = status; this.completedAt = completedAt; this.cancelledAt = cancelledAt;
        }
    }
}
