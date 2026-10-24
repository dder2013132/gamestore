import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import com.gamestore.util.DBConnection;

public class CatalogDatabaseSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("--check".equals(args[0]) || "--apply".equals(args[0]))) {
            throw new IllegalArgumentException("Use --check or --apply");
        }
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            if ("--apply".equals(args[0])) {
                stmt.execute(Files.readString(Path.of("sql", "008_add_user_role.sql")));
                stmt.execute(Files.readString(Path.of("sql", "010_seed_games.sql")));
                stmt.execute(Files.readString(Path.of("sql", "012_add_game_sale_status.sql")));
                stmt.execute(Files.readString(Path.of("sql", "014_order_checkout_tokens.sql")));
                stmt.execute(Files.readString(Path.of("sql", "015_create_wishlist.sql")));
                System.out.println("Catalog and member-feature migrations applied. No account was promoted.");
            }
            try (ResultSet columns = conn.getMetaData().getColumns(null, conn.getSchema(), "games", null)) {
                while (columns.next()) {
                    System.out.println("games." + columns.getString("COLUMN_NAME") + " " + columns.getString("TYPE_NAME")
                            + " size=" + columns.getInt("COLUMN_SIZE") + " default=" + columns.getString("COLUMN_DEF"));
                }
            }
            try (ResultSet rs = stmt.executeQuery("SELECT game_id, title, price, genre, image_url FROM games ORDER BY game_id")) {
                while (rs.next()) {
                    System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getInt(3) + " | " + rs.getString(4) + " | " + rs.getString(5));
                }
            }
        }
    }
}
