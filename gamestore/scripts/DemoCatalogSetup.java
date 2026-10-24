import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.sql.*;
import java.util.List;
import com.gamestore.util.DBConnection;

public class DemoCatalogSetup {
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length != 1 || !("--apply".equals(args[0]) || "--check".equals(args[0]))) {
            throw new IllegalArgumentException("Use --apply or --check");
        }
        try (Connection conn = DBConnection.getConnection()) {
            if ("--apply".equals(args[0])) {
                for (String image : List.of("lantern_post", "cloudberry_cafe", "tideglass", "mossbound", "starlight_courier", "fablewood_chronicles")) {
                    if (Files.size(Path.of("src/main/webapp/images", image + ".png")) == 0) { throw new IllegalStateException("Cover is empty: " + image); }
                }
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(Files.readString(Path.of("sql/013_expand_demo_catalog.sql")))) {
                    int count = 0;
                    while (rows.next()) { count++; System.out.println("Added: " + rows.getInt("game_id") + " | " + rows.getString("title")); }
                    System.out.println("Inserted rows: " + count);
                }
                conn.commit();
                conn.setAutoCommit(true);
            }
            try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                    "SELECT game_id, title, price, genre, image_url, LENGTH(description) AS description_length, is_active FROM games ORDER BY game_id")) {
                int count = 0;
                while (rows.next()) {
                    count++;
                    System.out.println(rows.getInt("game_id") + " | " + rows.getString("title") + " | " + rows.getInt("price")
                            + " | " + rows.getString("genre") + " | description=" + rows.getInt("description_length")
                            + " | active=" + rows.getBoolean("is_active") + " | cover=" + Files.isRegularFile(Path.of("src/main/webapp", rows.getString("image_url"))));
                }
                System.out.println("Catalog size: " + count);
            }
        }
    }
}
