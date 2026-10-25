import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.gamestore.util.DBConnection;

public class OrderStatusDatabaseSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("--check".equals(args[0]) || "--apply".equals(args[0]))) {
            throw new IllegalArgumentException("Use --check or --apply");
        }
        try (Connection conn = DBConnection.getConnection()) {
            if ("--apply".equals(args[0])) {
                conn.setAutoCommit(false);
                try {
                    List<String> before = snapshot(conn);
                    try (Statement stmt = conn.createStatement()) { stmt.execute(Files.readString(Path.of("sql/016_order_status.sql"))); }
                    if (!before.equals(snapshot(conn))) { throw new SQLException("Original order or item fields changed"); }
                    conn.commit(); conn.setAutoCommit(true);
                    System.out.println("Order status migration applied; original order and item fields preserved.");
                } catch (Exception e) { conn.rollback(); throw e; }
            }
            boolean exists;
            try (ResultSet columns = conn.getMetaData().getColumns(null, conn.getSchema(), "orders", "status")) { exists = columns.next(); }
            if (!exists) { System.out.println("Order status migration not applied yet."); return; }
            try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                    "SELECT status, COUNT(*) FROM orders GROUP BY status ORDER BY status")) {
                while (rows.next()) { System.out.println(rows.getString(1) + "=" + rows.getLong(2)); }
            }
        }
    }
    private static List<String> snapshot(Connection conn) throws SQLException {
        List<String> rows = new ArrayList<>();
        for (String sql : List.of("SELECT order_id, total_price, user_id, created_at, checkout_token FROM orders ORDER BY order_id",
                "SELECT item_id, order_id, game_id, price FROM order_items ORDER BY item_id")) {
            try (Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(sql)) {
                while (result.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) { row.add(result.getObject(i)); }
                    rows.add(row.toString());
                }
            }
        }
        return rows;
    }
}
