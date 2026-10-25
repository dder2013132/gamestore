package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import com.gamestore.model.GameDetails;
import com.gamestore.util.DBConnection;

public class JdbcGameDetailsRepository {
    @FunctionalInterface interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }
    private final ConnectionProvider connections;
    public JdbcGameDetailsRepository() { this(DBConnection::getConnection); }
    JdbcGameDetailsRepository(ConnectionProvider connections) { this.connections = connections; }

    public GameDetails findForGame(String gameId) throws SQLException, IOException, ClassNotFoundException {
        int id;
        try { id = Integer.parseInt(gameId); if (id <= 0) { return GameDetails.EMPTY; } }
        catch (NumberFormatException e) { return GameDetails.EMPTY; }
        String sql = "SELECT d.developer, d.release_date, d.supported_languages, d.gallery_note, s.image_path "
                + "FROM game_details d LEFT JOIN game_screenshots s ON s.game_id = d.game_id "
                + "WHERE d.game_id = ? ORDER BY s.position";
        try (Connection conn = connections.open(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rows = stmt.executeQuery()) {
                if (!rows.next()) { return GameDetails.EMPTY; }
                String developer = rows.getString("developer"), languages = rows.getString("supported_languages"), note = rows.getString("gallery_note");
                LocalDate date = rows.getObject("release_date", LocalDate.class);
                var screenshots = new ArrayList<String>();
                do { String path = rows.getString("image_path"); if (path != null) { screenshots.add(path); } } while (rows.next());
                return new GameDetails(developer, date, languages, screenshots, note);
            }
        }
    }

    /** Called inside the same transaction as the catalog create/update. */
    static void save(Connection conn, int gameId, GameDetails details) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO game_details (game_id, developer, release_date, supported_languages, gallery_note) "
                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (game_id) DO UPDATE SET developer = EXCLUDED.developer, release_date = EXCLUDED.release_date, "
                + "supported_languages = EXCLUDED.supported_languages, gallery_note = EXCLUDED.gallery_note")) {
            stmt.setInt(1, gameId); stmt.setString(2, details.developer()); stmt.setObject(3, details.releaseDate());
            stmt.setString(4, details.supportedLanguages()); stmt.setString(5, details.galleryNote()); stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM game_screenshots WHERE game_id = ?")) {
            stmt.setInt(1, gameId); stmt.executeUpdate();
        }
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO game_screenshots (game_id, position, image_path) VALUES (?, ?, ?)")) {
            int position = 0;
            for (String path : details.screenshots()) {
                stmt.setInt(1, gameId); stmt.setInt(2, ++position); stmt.setString(3, path); stmt.addBatch();
            }
            if (position > 0) { stmt.executeBatch(); }
        }
    }
}
