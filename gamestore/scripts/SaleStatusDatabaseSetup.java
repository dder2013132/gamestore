import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import com.gamestore.util.DBConnection;

public class SaleStatusDatabaseSetup {
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length != 1 || !("--check".equals(args[0]) || "--apply".equals(args[0]))) {
            throw new IllegalArgumentException("Use --check or --apply");
        }
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            if ("--apply".equals(args[0])) {
                stmt.execute(Files.readString(Path.of("sql/012_add_game_sale_status.sql")));
            }
            try (ResultSet rows = stmt.executeQuery("SELECT game_id, title, price, is_active FROM games ORDER BY game_id")) {
                while (rows.next()) {
                    System.out.println(rows.getInt("game_id") + " | " + rows.getString("title")
                            + " | " + rows.getInt("price") + " | active=" + rows.getBoolean("is_active"));
                }
            }
        }
    }
}
