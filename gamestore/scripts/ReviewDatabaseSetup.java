import java.nio.file.*;
import java.sql.*;
import com.gamestore.util.DBConnection;

public class ReviewDatabaseSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("--apply".equals(args[0]) || "--check".equals(args[0]))) { throw new IllegalArgumentException("Use --apply or --check"); }
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            if ("--apply".equals(args[0])) {
                stmt.execute(Files.readString(Path.of("sql/017_create_reviews.sql")));
                System.out.println("Review migration applied; existing accounts, games and orders unchanged.");
            }
            try (ResultSet columns = conn.getMetaData().getColumns(null, conn.getSchema(), "reviews", "review_id")) {
                if (!columns.next()) { System.out.println("Review migration not applied yet."); return; }
            }
            try (ResultSet rows = stmt.executeQuery("SELECT COUNT(*), COUNT(DISTINCT game_id) FROM reviews")) {
                rows.next(); System.out.println("reviews=" + rows.getLong(1) + " reviewed_games=" + rows.getLong(2));
            }
        }
    }
}
