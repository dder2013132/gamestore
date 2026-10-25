package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

public class JdbcHomeRepository {
    public static final int SECTION_SIZE = 4;
    @FunctionalInterface interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }
    private final ConnectionProvider connections;
    public JdbcHomeRepository() { this(DBConnection::getConnection); }
    JdbcHomeRepository(ConnectionProvider connections) { this.connections = connections; }

    public HomePage findHome() throws SQLException, IOException, ClassNotFoundException {
        // Aggregate independently to avoid multiplying purchases by reviews. A single statement
        // gives the section lists, statistics and total count the same PostgreSQL snapshot.
        String sql = """
                WITH buyers AS (
                    SELECT i.game_id, COUNT(DISTINCT o.user_id) AS buyer_count
                    FROM orders o JOIN order_items i ON i.order_id = o.order_id
                    WHERE o.status = 'COMPLETED' GROUP BY i.game_id
                ), ratings AS (
                    SELECT game_id, COUNT(*) AS review_count, AVG(rating) AS average_rating
                    FROM reviews GROUP BY game_id
                ), catalog AS (
                    SELECT g.game_id, g.title, g.price, g.genre, g.image_url,
                        COALESCE(b.buyer_count, 0) AS buyer_count,
                        COALESCE(r.review_count, 0) AS review_count,
                        COALESCE(r.average_rating, 0) AS average_rating
                    FROM games g LEFT JOIN buyers b ON b.game_id = g.game_id
                    LEFT JOIN ratings r ON r.game_id = g.game_id WHERE g.is_active
                ), ranked AS (
                    SELECT catalog.*, COUNT(*) OVER () AS catalog_count,
                        ROW_NUMBER() OVER (ORDER BY game_id DESC) AS new_rank,
                        ROW_NUMBER() OVER (ORDER BY buyer_count DESC, game_id DESC) AS popular_rank,
                        ROW_NUMBER() OVER (ORDER BY average_rating DESC, review_count DESC, game_id DESC) AS rating_rank
                    FROM catalog
                )
                SELECT * FROM ranked WHERE new_rank <= ?
                    OR (buyer_count > 0 AND popular_rank <= ?)
                    OR (review_count > 0 AND rating_rank <= ?)
                """;
        var newest = new TreeMap<Long, HomeGame>(); var popular = new TreeMap<Long, HomeGame>(); var rated = new TreeMap<Long, HomeGame>();
        int total = 0;
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setReadOnly(true);
            for (int i = 1; i <= 3; i++) { stmt.setInt(i, SECTION_SIZE); }
            try (ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    total = rows.getInt("catalog_count");
                    Game game = new Game(rows.getString("game_id"), rows.getString("title"), rows.getInt("price"), rows.getString("genre"), rows.getString("image_url"));
                    var entry = new HomeGame(game, rows.getLong("buyer_count"), rows.getLong("review_count"), rows.getDouble("average_rating"));
                    long newRank = rows.getLong("new_rank"), popularRank = rows.getLong("popular_rank"), ratingRank = rows.getLong("rating_rank");
                    if (newRank <= SECTION_SIZE) { newest.put(newRank, entry); }
                    if (entry.buyerCount() > 0 && popularRank <= SECTION_SIZE) { popular.put(popularRank, entry); }
                    if (entry.reviewCount() > 0 && ratingRank <= SECTION_SIZE) { rated.put(ratingRank, entry); }
                }
            }
        }
        return new HomePage(new ArrayList<>(newest.values()), new ArrayList<>(popular.values()), new ArrayList<>(rated.values()), total);
    }
}
