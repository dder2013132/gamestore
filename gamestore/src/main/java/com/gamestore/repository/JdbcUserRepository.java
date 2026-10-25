package com.gamestore.repository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import com.gamestore.model.User;
import com.gamestore.util.DBConnection;
import com.gamestore.util.PasswordHasher;

public class JdbcUserRepository {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }
    private final ConnectionProvider connections;

    public JdbcUserRepository() { this(DBConnection::getConnection); }
    JdbcUserRepository(ConnectionProvider connections) { this.connections = connections; }
    // Perform a password derivation even when the username does not exist.
    private static final String DUMMY_HASH = PasswordHasher.hash("unused-account-password");

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public User create(String username, String password)
            throws SQLException, IOException, ClassNotFoundException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, 'USER') RETURNING user_id, username, role";
        String hash = PasswordHasher.hash(password);
        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizeUsername(username));
            stmt.setString(2, hash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) { throw new SQLException("User creation returned no row"); }
                return new User(rs.getLong("user_id"), rs.getString("username"), rs.getString("role"));
            }
        }
    }

    public User authenticate(String username, String password)
            throws SQLException, IOException, ClassNotFoundException {
        String sql = "SELECT user_id, username, password_hash, role FROM users WHERE username = ?";
        User user = null;
        String hash = DUMMY_HASH;
        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizeUsername(username));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    user = new User(rs.getLong("user_id"), rs.getString("username"), rs.getString("role"));
                    hash = rs.getString("password_hash");
                }
            }
        }
        return PasswordHasher.verify(password, hash) ? user : null;
    }

    /** Compare-and-set prevents two concurrent password changes from overwriting each other. */
    public boolean changePassword(long userId, String currentPassword, String newPassword)
            throws SQLException, IOException, ClassNotFoundException {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128
                || newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("Invalid new password");
        }
        try (Connection conn = connections.open()) {
            String oldHash;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?")) {
                stmt.setLong(1, userId);
                try (ResultSet rows = stmt.executeQuery()) {
                    if (!rows.next()) { return false; }
                    oldHash = rows.getString("password_hash");
                }
            }
            if (!PasswordHasher.verify(currentPassword, oldHash)) { return false; }
            String newHash = PasswordHasher.hash(newPassword);
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ? AND password_hash = ?")) {
                stmt.setString(1, newHash); stmt.setLong(2, userId); stmt.setString(3, oldHash);
                return stmt.executeUpdate() == 1;
            }
        }
    }

    public User findById(long id) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement("SELECT user_id, username, role FROM users WHERE user_id = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? new User(rs.getLong("user_id"), rs.getString("username"), rs.getString("role")) : null;
            }
        }
    }
}
