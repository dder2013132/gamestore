import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.gamestore.util.DBConnection;

/** Fill demo metadata without touching accounts, catalog prices, orders or gallery content. */
public class CatalogMetadataSetup {
    private record Existing(String developer, java.time.LocalDate date, String languages, String note) { }
    private static String fingerprint(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                "SELECT md5(COALESCE(string_agg(t::text, E'\\n' ORDER BY t::text), '')) FROM " + table + " t")) {
            rows.next(); return rows.getString(1);
        }
    }
    private static Map<Integer, Existing> details(Connection conn) throws SQLException {
        var result = new HashMap<Integer, Existing>();
        try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                "SELECT game_id, developer, release_date, supported_languages, gallery_note FROM game_details")) {
            while (rows.next()) result.put(rows.getInt(1), new Existing(rows.getString(2), rows.getObject(3, java.time.LocalDate.class), rows.getString(4), rows.getString(5)));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("--apply".equals(args[0]) || "--check".equals(args[0]))) { throw new IllegalArgumentException("Use --apply or --check"); }
        try (Connection conn = DBConnection.getConnection()) {
            if ("--apply".equals(args[0])) {
                conn.setAutoCommit(false);
                try {
                    var before = new HashMap<String, String>();
                    for (String table : List.of("games", "users", "orders", "order_items", "wishlist_items", "reviews", "game_screenshots")) before.put(table, fingerprint(conn, table));
                    var existing = details(conn);
                    try (Statement stmt = conn.createStatement()) { stmt.execute(Files.readString(Path.of("sql/020_seed_catalog_metadata.sql"))); }
                    for (var entry : before.entrySet()) if (!entry.getValue().equals(fingerprint(conn, entry.getKey()))) throw new IllegalStateException("Unrelated data changed: " + entry.getKey());
                    var after = details(conn);
                    for (var entry : existing.entrySet()) {
                        Existing old = entry.getValue(), now = after.get(entry.getKey());
                        if (now == null || (!old.developer().isBlank() && !old.developer().equals(now.developer()))
                                || (old.date() != null && !old.date().equals(now.date()))
                                || (!old.languages().isBlank() && !old.languages().equals(now.languages())) || !old.note().equals(now.note())) {
                            throw new IllegalStateException("Existing nonblank metadata or gallery note changed");
                        }
                    }
                    conn.commit();
                    System.out.println("Fictional catalog metadata filled; existing values, gallery, catalog and member data preserved.");
                } catch (Exception e) { conn.rollback(); throw e; }
            }
            try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                    "SELECT COUNT(*), COUNT(*) FILTER (WHERE BTRIM(COALESCE(d.developer,'')) <> '' AND d.release_date IS NOT NULL "
                    + "AND BTRIM(COALESCE(d.supported_languages,'')) <> ''), (SELECT COUNT(*) FROM game_screenshots) "
                    + "FROM games g LEFT JOIN game_details d USING(game_id)")) {
                rows.next(); System.out.println("games=" + rows.getInt(1) + " complete_metadata=" + rows.getInt(2) + " gallery_images=" + rows.getInt(3));
            }
        }
    }
}
