import java.nio.file.*;
import java.sql.*;
import com.gamestore.util.DBConnection;

public class GameDetailsDatabaseSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("--apply".equals(args[0]) || "--check".equals(args[0]))) { throw new IllegalArgumentException("Use --apply or --check"); }
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            if ("--apply".equals(args[0])) {
                conn.setAutoCommit(false);
                try {
                    stmt.execute(Files.readString(Path.of("sql/018_game_details.sql")));
                    stmt.execute(Files.readString(Path.of("sql/019_seed_game_details.sql")));
                    stmt.execute(Files.readString(Path.of("sql/020_seed_catalog_metadata.sql")));
                    conn.commit();
                } catch (Exception e) { conn.rollback(); throw e; }
                System.out.println("Game details, fictional catalog metadata and Moonlit gallery applied; existing catalog values and nonblank details preserved.");
            }
            try (ResultSet rows = stmt.executeQuery("SELECT COUNT(*), (SELECT COUNT(*) FROM game_screenshots) FROM game_details")) {
                rows.next(); System.out.println("game_details=" + rows.getLong(1) + " gallery_images=" + rows.getLong(2));
            }
        }
    }
}
