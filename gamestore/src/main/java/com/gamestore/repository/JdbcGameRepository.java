package com.gamestore.repository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.gamestore.model.Game;
import com.gamestore.model.CatalogPage;
import com.gamestore.util.DBConnection;

public class JdbcGameRepository {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }

    private final ConnectionProvider connections;

    public JdbcGameRepository() { this(DBConnection::getConnection); }
    JdbcGameRepository(ConnectionProvider connections) { this.connections = connections; }

    public CatalogPage findPage(String query, String genre, String sort, int requestedPage, int pageSize)
            throws SQLException, IOException, ClassNotFoundException {
        if (pageSize < 1 || pageSize > 100) { throw new IllegalArgumentException("Invalid catalog page size"); }
        List<String> parameters = new ArrayList<>();
        String where = " WHERE is_active";
        if (genre != null && !genre.isEmpty()) { where += " AND genre = ?"; parameters.add(genre); }
        if (query != null && !query.isEmpty()) {
            where += " AND (POSITION(? IN LOWER(COALESCE(title, ''))) > 0 OR POSITION(? IN LOWER(COALESCE(genre, ''))) > 0)";
            parameters.add(query.toLowerCase(Locale.ROOT));
            parameters.add(query.toLowerCase(Locale.ROOT));
        }
        String order = switch (sort == null ? "" : sort) {
            case "price_asc" -> "price ASC, game_id DESC";
            case "price_desc" -> "price DESC, game_id DESC";
            case "title" -> "LOWER(title) ASC, game_id DESC";
            default -> "game_id DESC";
        };
        try (Connection conn = connections.open()) {
            conn.setReadOnly(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                int catalogCount;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM games WHERE is_active"); ResultSet rows = stmt.executeQuery()) {
                    rows.next(); catalogCount = rows.getInt(1);
                }
                int resultCount;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM games" + where)) {
                    bind(stmt, parameters);
                    try (ResultSet rows = stmt.executeQuery()) { rows.next(); resultCount = rows.getInt(1); }
                }
                int page = Math.max(1, Math.min(requestedPage, Math.max(1, Math.ceilDiv(resultCount, pageSize))));
                List<Game> games = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT game_id, title, price, genre, image_url, description, is_active FROM games" + where
                        + " ORDER BY " + order + " LIMIT ? OFFSET ?")) {
                    bind(stmt, parameters);
                    stmt.setInt(parameters.size() + 1, pageSize);
                    stmt.setInt(parameters.size() + 2, (page - 1) * pageSize);
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) { games.add(new Game(rows.getString("game_id"), rows.getString("title"), rows.getInt("price"),
                                rows.getString("genre"), rows.getString("image_url"), rows.getString("description"), rows.getBoolean("is_active"))); }
                    }
                }
                List<String> genres = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT genre FROM (SELECT DISTINCT genre FROM games WHERE is_active "
                        + "AND genre IS NOT NULL AND BTRIM(genre) <> '') available ORDER BY LOWER(genre), genre"); ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) { genres.add(rows.getString(1)); }
                }
                conn.commit();
                return new CatalogPage(games, genres, catalogCount, resultCount, page, pageSize);
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    private void bind(PreparedStatement stmt, List<String> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) { stmt.setString(i + 1, parameters.get(i)); }
    }

    public List<Game> findAll() throws SQLException, IOException, ClassNotFoundException {
        List<Game> games = new ArrayList<>();

        String sql = "SELECT game_id, title, price, genre, image_url, description, is_active FROM games ORDER BY game_id";

        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("game_id");
                String title = rs.getString("title");
                int price = rs.getInt("price");
                String genre = rs.getString("genre");
                String imagePath = rs.getString("image_url");

                Game game = new Game(id, title, price, genre, imagePath, rs.getString("description"), rs.getBoolean("is_active"));
                games.add(game);
            }

        }

        return games;
    }

    public Game findById(String gameId) throws SQLException, IOException, ClassNotFoundException {
        int idValue;
        try {
            idValue = Integer.parseInt(gameId);
            if (idValue <= 0) { return null; }
        } catch (NumberFormatException e) {
            return null;
        }
        String sql = "SELECT game_id, title, price, genre, image_url, description, is_active "
                + "FROM games WHERE game_id = ?";

        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idValue);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("game_id");
                    String title = rs.getString("title");
                    int price = rs.getInt("price");
                    String genre = rs.getString("genre");
                    String imagePath = rs.getString("image_url");

                    return new Game(id, title, price, genre, imagePath, rs.getString("description"), rs.getBoolean("is_active"));
                }
            }
        }
        return null;
    }

    public Game create(String title, int price, String genre, String imagePath)
            throws SQLException, IOException, ClassNotFoundException {
        return create(title, price, genre, imagePath, "");
    }

    public Game create(String title, int price, String genre, String imagePath, String description)
            throws SQLException, IOException, ClassNotFoundException {
        String sql = "INSERT INTO games (title, price, genre, image_url, description) VALUES (?, ?, ?, ?, ?) "
                + "RETURNING game_id, title, price, genre, image_url, description, is_active";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setInt(2, price);
            stmt.setString(3, genre);
            stmt.setString(4, imagePath);
            stmt.setString(5, description);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) { throw new SQLException("Game creation returned no row"); }
                return new Game(rs.getString("game_id"), rs.getString("title"), rs.getInt("price"),
                        rs.getString("genre"), rs.getString("image_url"), rs.getString("description"), rs.getBoolean("is_active"));
            }
        }
    }

    public boolean updateDescription(String gameId, String description)
            throws SQLException, IOException, ClassNotFoundException {
        int id;
        try {
            id = Integer.parseInt(gameId);
            if (id <= 0) { return false; }
        } catch (NumberFormatException e) {
            return false;
        }
        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement("UPDATE games SET description = ? WHERE game_id = ?")) {
            stmt.setString(1, description);
            stmt.setInt(2, id);
            return stmt.executeUpdate() == 1;
        }
    }

    public boolean update(String gameId, String title, int price, String genre, String imagePath, String description)
            throws SQLException, IOException, ClassNotFoundException {
        Integer id = positiveId(gameId);
        if (id == null) { return false; }
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(
                "UPDATE games SET title = ?, price = ?, genre = ?, image_url = ?, description = ? WHERE game_id = ?")) {
            stmt.setString(1, title);
            stmt.setInt(2, price);
            stmt.setString(3, genre);
            stmt.setString(4, imagePath);
            stmt.setString(5, description);
            stmt.setInt(6, id);
            return stmt.executeUpdate() == 1;
        }
    }

    public boolean setActive(String gameId, boolean active) throws SQLException, IOException, ClassNotFoundException {
        Integer id = positiveId(gameId);
        if (id == null) { return false; }
        try (Connection conn = connections.open();
                PreparedStatement stmt = conn.prepareStatement("UPDATE games SET is_active = ? WHERE game_id = ?")) {
            stmt.setBoolean(1, active);
            stmt.setInt(2, id);
            return stmt.executeUpdate() == 1;
        }
    }

    private Integer positiveId(String value) {
        try {
            int id = Integer.parseInt(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) { return null; }
    }
}
