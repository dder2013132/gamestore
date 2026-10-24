import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;
import java.sql.*;
import java.util.List;
import com.gamestore.util.DBConnection;

public class MemberFeaturesDatabaseSetup {
    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length != 1 || !("--apply".equals(args[0]) || "--check".equals(args[0]))) { throw new IllegalArgumentException("Use --apply or --check"); }
        try (Connection conn = DBConnection.getConnection()) {
            if ("--apply".equals(args[0])) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    for (String file : List.of("014_order_checkout_tokens.sql", "015_create_wishlist.sql")) { stmt.execute(Files.readString(Path.of("sql", file))); }
                }
                conn.commit(); conn.setAutoCommit(true);
                System.out.println("Checkout-token and wishlist migrations applied; existing orders preserved.");
            }
            try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery(
                    "SELECT (SELECT COUNT(*) FROM games), (SELECT COUNT(*) FROM users), (SELECT COUNT(*) FROM orders), "
                    + "(SELECT COUNT(*) FROM order_items), (SELECT COUNT(*) FROM wishlist_items), (SELECT COUNT(*) FROM orders WHERE checkout_token IS NOT NULL)")) {
                rows.next(); System.out.println("games="+rows.getInt(1)+" users="+rows.getInt(2)+" orders="+rows.getInt(3)
                        +" order_items="+rows.getInt(4)+" wishlist_items="+rows.getInt(5)+" tokenized_orders="+rows.getInt(6));
            }
        }
    }
}
