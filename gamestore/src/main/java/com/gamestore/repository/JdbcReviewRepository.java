package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import com.gamestore.model.Review;
import com.gamestore.model.ReviewPage;
import com.gamestore.util.DBConnection;

public class JdbcReviewRepository {
    @FunctionalInterface interface ConnectionProvider { Connection open() throws SQLException, IOException, ClassNotFoundException; }
    public record GameReviews(ReviewPage page, Review own, boolean eligible) { }
    private final ConnectionProvider connections;
    public JdbcReviewRepository() { this(DBConnection::getConnection); }
    JdbcReviewRepository(ConnectionProvider connections) { this.connections = connections; }
    private static final String FIELDS = "SELECT r.review_id, r.user_id, r.game_id, r.rating, r.body, r.version, r.created_at, r.updated_at, u.username, g.title, g.image_url "
            + "FROM reviews r JOIN users u ON u.user_id = r.user_id JOIN games g ON g.game_id = r.game_id ";
    private static final String PURCHASE = "EXISTS (SELECT 1 FROM orders o JOIN order_items i ON i.order_id = o.order_id "
            + "WHERE o.user_id = ? AND i.game_id = ? AND o.status = 'COMPLETED')";

    public GameReviews findForGame(int gameId, Long viewerId, int requestedPage) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open()) {
            beginRead(conn);
            try {
                ReviewPage page = page(conn, "r.game_id", gameId, requestedPage, 5, "r.created_at DESC, r.review_id DESC");
                Review own = viewerId == null ? null : findOwn(conn, viewerId, gameId);
                boolean eligible = viewerId != null && eligible(conn, viewerId, gameId);
                conn.commit(); return new GameReviews(page, own, eligible);
            } catch (SQLException | RuntimeException e) { rollback(conn, e); throw e; }
        }
    }
    public ReviewPage findForUser(long userId, int requestedPage) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open()) {
            beginRead(conn);
            try { ReviewPage page = page(conn, "r.user_id", userId, requestedPage, 10, "r.updated_at DESC, r.review_id DESC"); conn.commit(); return page; }
            catch (SQLException | RuntimeException e) { rollback(conn, e); throw e; }
        }
    }
    private ReviewPage page(Connection conn, String column, long id, int requestedPage, int size, String order) throws SQLException {
        int count; double average;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*), COALESCE(AVG(r.rating), 0) FROM reviews r WHERE " + column + " = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rows = stmt.executeQuery()) { rows.next(); count = rows.getInt(1); average = rows.getDouble(2); }
        }
        int page = Math.max(1, Math.min(requestedPage, Math.max(1, Math.ceilDiv(count, size))));
        List<Review> reviews = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(FIELDS + "WHERE " + column + " = ? ORDER BY " + order + " LIMIT ? OFFSET ?")) {
            stmt.setLong(1, id); stmt.setInt(2, size); stmt.setInt(3, (page - 1) * size);
            try (ResultSet rows = stmt.executeQuery()) { while (rows.next()) { reviews.add(read(rows)); } }
        }
        return new ReviewPage(reviews, count, average, page, size);
    }
    public Review findOwn(long userId, int gameId) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open()) { return findOwn(conn, userId, gameId); }
    }
    private Review findOwn(Connection conn, long userId, int gameId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(FIELDS + "WHERE r.user_id = ? AND r.game_id = ?")) {
            stmt.setLong(1, userId); stmt.setInt(2, gameId);
            try (ResultSet rows = stmt.executeQuery()) { return rows.next() ? read(rows) : null; }
        }
    }
    public boolean eligible(long userId, int gameId) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open()) { return eligible(conn, userId, gameId); }
    }
    private boolean eligible(Connection conn, long userId, int gameId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT " + PURCHASE)) {
            stmt.setLong(1, userId); stmt.setInt(2, gameId);
            try (ResultSet rows = stmt.executeQuery()) { rows.next(); return rows.getBoolean(1); }
        }
    }
    public ReviewResult create(long userId, int gameId, int rating, String body) throws SQLException, IOException, ClassNotFoundException {
        body = Review.validate(rating, body);
        try (Connection conn = connections.open()) {
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO reviews (user_id, game_id, rating, body) SELECT ?, ?, ?, ? WHERE "
                    + PURCHASE + " ON CONFLICT (user_id, game_id) DO NOTHING RETURNING review_id")) {
                stmt.setLong(1, userId); stmt.setInt(2, gameId); stmt.setInt(3, rating); stmt.setString(4, body);
                stmt.setLong(5, userId); stmt.setInt(6, gameId);
                try (ResultSet rows = stmt.executeQuery()) { if (rows.next()) { return ReviewResult.SAVED; } }
            }
            return findOwn(conn, userId, gameId) == null ? ReviewResult.NOT_ELIGIBLE : ReviewResult.ALREADY_EXISTS;
        }
    }
    public ReviewResult update(long userId, int gameId, long reviewId, long version, int rating, String body)
            throws SQLException, IOException, ClassNotFoundException {
        body = Review.validate(rating, body);
        try (Connection conn = connections.open()) {
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE reviews SET rating = ?, body = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1 "
                    + "WHERE user_id = ? AND game_id = ? AND review_id = ? AND version = ? AND " + PURCHASE)) {
                stmt.setInt(1, rating); stmt.setString(2, body); stmt.setLong(3, userId); stmt.setInt(4, gameId);
                stmt.setLong(5, reviewId); stmt.setLong(6, version); stmt.setLong(7, userId); stmt.setInt(8, gameId);
                if (stmt.executeUpdate() == 1) { return ReviewResult.SAVED; }
            }
            Review own = findOwn(conn, userId, gameId);
            if (own == null || own.id() != reviewId) { return ReviewResult.NOT_FOUND; }
            return eligible(conn, userId, gameId) ? ReviewResult.STALE : ReviewResult.NOT_ELIGIBLE;
        }
    }
    public ReviewResult delete(long userId, int gameId, long reviewId, long version) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM reviews WHERE user_id = ? AND game_id = ? AND review_id = ? AND version = ?")) {
                stmt.setLong(1, userId); stmt.setInt(2, gameId); stmt.setLong(3, reviewId); stmt.setLong(4, version);
                if (stmt.executeUpdate() == 1) { return ReviewResult.SAVED; }
            }
            Review own = findOwn(conn, userId, gameId);
            return own == null || own.id() != reviewId ? ReviewResult.NOT_FOUND : ReviewResult.STALE;
        }
    }
    private static Review read(ResultSet row) throws SQLException {
        return new Review(row.getLong("review_id"), row.getLong("user_id"), row.getInt("game_id"), row.getString("username"),
                row.getString("title"), row.getString("image_url"), row.getInt("rating"), row.getString("body"), row.getLong("version"),
                row.getObject("created_at", LocalDateTime.class), row.getObject("updated_at", LocalDateTime.class));
    }
    private static void beginRead(Connection conn) throws SQLException {
        conn.setReadOnly(true); conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); conn.setAutoCommit(false);
    }
    private static void rollback(Connection conn, Exception e) { try { conn.rollback(); } catch (SQLException failure) { e.addSuppressed(failure); } }
}
