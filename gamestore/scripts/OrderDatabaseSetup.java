import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import com.gamestore.util.DBConnection;

/** Run from the project directory after mvn package; connection secrets stay in db.properties. */
public class OrderDatabaseSetup {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("--check".equals(args[0]) || "--apply".equals(args[0]))) {
            throw new IllegalArgumentException("Use --check or --apply");
        }
        try (Connection connection = DBConnection.getConnection(); Statement stmt = connection.createStatement()) {
            String schema = connection.getSchema();
            long orders = count(stmt, "orders");
            long items = count(stmt, "order_items");
            boolean hasUserId;
            try (ResultSet columns = connection.getMetaData().getColumns(null, schema, "orders", "user_id")) {
                hasUserId = columns.next();
            }
            System.out.println("Schema: " + schema + "; orders=" + orders + "; order_items=" + items);
            if ("--apply".equals(args[0]) && !hasUserId) {
                if (orders != 0 || items != 0) {
                    throw new IllegalStateException("Orders are not empty. Assign owners before applying this migration.");
                }
                stmt.execute(Files.readString(Path.of("sql", "007_add_order_user.sql")));
                hasUserId = true;
                System.out.println("Applied 007_add_order_user.sql");
            }
            System.out.println("orders.user_id present: " + hasUserId);
            try (ResultSet columns = connection.getMetaData().getColumns(null, schema, "orders", null)) {
                while (columns.next()) {
                    System.out.println("orders." + columns.getString("COLUMN_NAME") + " "
                            + columns.getString("TYPE_NAME") + " nullable=" + columns.getString("IS_NULLABLE"));
                }
            }
            try (ResultSet keys = connection.getMetaData().getImportedKeys(null, schema, "orders")) {
                while (keys.next()) {
                    System.out.println("Foreign key: " + keys.getString("FKCOLUMN_NAME") + " -> "
                            + keys.getString("PKTABLE_NAME") + "." + keys.getString("PKCOLUMN_NAME"));
                }
            }
        }
    }

    private static long count(Statement stmt, String table) throws Exception {
        // Only fixed table names from main() reach this method.
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
