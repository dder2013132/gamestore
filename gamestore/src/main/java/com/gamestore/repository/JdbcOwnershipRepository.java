package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import com.gamestore.util.DBConnection;

/** Ownership is derived only from the member's completed orders. */
public class JdbcOwnershipRepository {
    @FunctionalInterface interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }
    private final ConnectionProvider connections;
    public JdbcOwnershipRepository() { this(DBConnection::getConnection); }
    JdbcOwnershipRepository(ConnectionProvider connections) { this.connections = connections; }

    public Set<String> findIdsForUser(long userId) throws SQLException, IOException, ClassNotFoundException {
        try (Connection conn = connections.open()) { return findIds(conn, userId); }
    }

    static Set<String> findIds(Connection conn, long userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT i.game_id FROM orders o "
                + "JOIN order_items i ON i.order_id = o.order_id WHERE o.user_id = ? AND o.status = 'COMPLETED'")) {
            stmt.setLong(1, userId);
            Set<String> ids = new HashSet<>();
            try (ResultSet rows = stmt.executeQuery()) { while (rows.next()) { ids.add(rows.getString(1)); } }
            return Set.copyOf(ids);
        }
    }
}
