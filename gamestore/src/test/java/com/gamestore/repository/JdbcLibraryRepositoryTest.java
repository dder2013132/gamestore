package com.gamestore.repository;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.*;
import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

public class JdbcLibraryRepositoryTest {
    private static String schema; private static boolean created;
    private static Connection connection() throws SQLException, java.io.IOException, ClassNotFoundException {
        Connection conn = DBConnection.getConnection(); try { conn.setSchema(schema); return conn; } catch (SQLException e) { conn.close(); throw e; }
    }
    private JdbcLibraryRepository library() { return new JdbcLibraryRepository(JdbcLibraryRepositoryTest::connection); }
    private JdbcOrderRepository orders() { return new JdbcOrderRepository(JdbcLibraryRepositoryTest::connection); }
    private JdbcReviewRepository reviews() { return new JdbcReviewRepository(JdbcLibraryRepositoryTest::connection); }
    @BeforeClass public static void setup() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("gamestore.dbTest")); schema = "gamestore_library_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) { stmt.execute("CREATE SCHEMA " + schema); created = true; }
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE games (game_id INTEGER PRIMARY KEY, title TEXT, price INTEGER, genre TEXT, image_url TEXT, description TEXT, is_active BOOLEAN NOT NULL DEFAULT TRUE)");
            stmt.executeUpdate("INSERT INTO games (game_id, title, price, genre, image_url) SELECT n, 'Game ' || n, n * 100, 'Adventure', 'images/aetherbound.png' FROM generate_series(1,12) n");
            for (String file : List.of("006_create_users.sql", "004_create_orders.sql", "005_create_order_items.sql", "007_add_order_user.sql", "014_order_checkout_tokens.sql", "015_create_wishlist.sql", "016_order_status.sql", "017_create_reviews.sql")) {
                stmt.execute(Files.readString(Path.of("sql", file)));
            }
        }
    }
    @AfterClass public static void cleanup() throws Exception {
        if (!created) { return; } if (!schema.matches("gamestore_library_test_[a-f0-9]{32}")) { throw new IllegalStateException("Unexpected schema"); }
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) { stmt.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
    @Before public void reset() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE users RESTART IDENTITY CASCADE");
            stmt.executeUpdate("INSERT INTO users (username, password_hash) VALUES ('member_alice', 'unused'), ('member_bobby', 'unused')");
            stmt.executeUpdate("UPDATE games SET title = 'Game ' || game_id, genre = 'Adventure', price = game_id * 100, is_active = TRUE");
        }
    }
    private String order(long userId, String state, int... ids) throws Exception {
        List<Game> games = Arrays.stream(ids).mapToObj(i -> new Game("" + i, "Game " + i, i * 100, "Adventure", "images/aetherbound.png")).toList();
        String id = UUID.randomUUID().toString(); orders().save(new Order(id, games, games.stream().mapToInt(Game::getPrice).sum(), userId));
        if (state.equals("complete")) { orders().completeDemoForUser(id, userId); } else if (state.equals("cancel")) { orders().cancelPendingForUser(id, userId); }
        return id;
    }
    // Historical purchases from before the repurchase guard must remain deduplicated.
    private String legacyRepeat(long userId, int gameId) throws Exception {
        String id = UUID.randomUUID().toString();
        try (Connection conn = connection(); PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO orders (order_id, user_id, total_price, status, completed_at) VALUES (?, ?, ?, 'COMPLETED', CURRENT_TIMESTAMP)")) {
            stmt.setString(1, id); stmt.setLong(2, userId); stmt.setInt(3, gameId * 100); stmt.executeUpdate();
            try (PreparedStatement item = conn.prepareStatement("INSERT INTO order_items (order_id, game_id, price) VALUES (?, ?, ?)")) {
                item.setString(1, id); item.setInt(2, gameId); item.setInt(3, gameId * 100); item.executeUpdate();
            }
        }
        return id;
    }
    @Test public void onlyOwnCompletedGamesAppearAndRepeatedPurchasesAreDeduplicated() throws Exception {
        order(1L, "complete", 1, 2); legacyRepeat(1L, 1); order(1L, "pending", 3); order(1L, "cancel", 4); order(2L, "complete", 5);
        LibraryPage result = library().findForUser(1L, "", 1); assertEquals(2, result.totalCount()); assertEquals(2, result.resultCount());
        assertEquals(Set.of("1", "2"), new HashSet<>(result.games().stream().map(g -> g.game().getId()).toList()));
        assertEquals(List.of("5"), library().findForUser(2L, "", 1).games().stream().map(g -> g.game().getId()).toList());
    }
    @Test public void paymentCompletionAddsOwnershipButCancellationDoesNot() throws Exception {
        String pending = order(1L, "pending", 1); String cancelled = order(1L, "pending", 2);
        assertEquals(0, library().findForUser(1L, "", 1).totalCount());
        orders().completeDemoForUser(pending, 1L); orders().cancelPendingForUser(cancelled, 1L);
        assertEquals(1, library().findForUser(1L, "", 1).totalCount()); assertEquals("1", library().findForUser(1L, "", 1).games().get(0).game().getId());
    }
    @Test public void reviewBadgesBelongToCurrentMemberAndRefreshAfterEditsAndDelete() throws Exception {
        order(1L, "complete", 1); order(2L, "complete", 1); reviews().create(2L, 1, 5, "Other member");
        assertFalse(library().findForUser(1L, "", 1).games().get(0).isReviewed());
        reviews().create(1L, 1, 3, "Own review"); assertEquals(Integer.valueOf(3), library().findForUser(1L, "", 1).games().get(0).rating());
        var own = reviews().findOwn(1L, 1); reviews().update(1L, 1, own.id(), own.version(), 4, "Updated");
        assertEquals(Integer.valueOf(4), library().findForUser(1L, "", 1).games().get(0).rating());
        own = reviews().findOwn(1L, 1); reviews().delete(1L, 1, own.id(), own.version());
        assertFalse(library().findForUser(1L, "", 1).games().get(0).isReviewed());
    }
    @Test public void saleSuspensionAndCatalogUpdatesDoNotRemoveOwnedGames() throws Exception {
        order(1L, "complete", 1);
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE games SET is_active = FALSE, title = 'Updated title', genre = 'Puzzle', price = 999 WHERE game_id = 1");
        }
        LibraryGame game = library().findForUser(1L, "", 1).games().get(0);
        assertFalse(game.game().isActive()); assertEquals("Updated title", game.game().getTitle()); assertEquals("Puzzle", game.game().getGenre());
        assertTrue(reviews().eligible(1L, 1));
    }
    @Test public void earliestAcquisitionIsRetainedAfterBuyingSameGameAgain() throws Exception {
        String first = order(1L, "complete", 1), second = order(1L, "complete", 2), again = legacyRepeat(1L, 1);
        try (Connection conn = connection(); PreparedStatement stmt = conn.prepareStatement("UPDATE orders SET completed_at = ? WHERE order_id = ?")) {
            for (var row : List.of(Map.entry(first, 1), Map.entry(second, 2), Map.entry(again, 3))) {
                stmt.setObject(1, LocalDateTime.of(2026, 10, row.getValue(), 12, 0)); stmt.setString(2, row.getKey()); stmt.executeUpdate();
            }
        }
        var result = library().findForUser(1L, "", 1);
        assertEquals(List.of("2", "1"), result.games().stream().map(g -> g.game().getId()).toList());
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), result.games().get(1).acquiredAt());
    }
    @Test public void searchTreatsSqlCharactersLiterallyAndPaginationUsesStableDistinctGames() throws Exception {
        order(1L, "complete", java.util.stream.IntStream.rangeClosed(1, 12).toArray());
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE orders SET completed_at = TIMESTAMP '2026-10-25 12:00:00'");
            stmt.executeUpdate("UPDATE games SET title = 'Moon_% Garden', genre = 'Puzzle' WHERE game_id = 1");
        }
        var first = library().findForUser(1L, "", 1); var second = library().findForUser(1L, "", Integer.MAX_VALUE);
        assertEquals(12, first.totalCount()); assertEquals(9, first.games().size()); assertEquals(3, second.games().size()); assertEquals(2, second.page());
        Set<String> ids = new HashSet<>(); first.games().forEach(g -> assertTrue(ids.add(g.game().getId()))); second.games().forEach(g -> assertTrue(ids.add(g.game().getId())));
        assertEquals(12, ids.size()); assertEquals("12", first.games().get(0).game().getId());
        assertEquals(1, library().findForUser(1L, "mOoN", 1).resultCount()); assertEquals(1, library().findForUser(1L, "pUzZlE", 1).resultCount());
        assertEquals(1, library().findForUser(1L, "_%", 1).resultCount()); assertEquals(0, library().findForUser(1L, "' OR TRUE --", 1).resultCount());
        assertEquals(1, library().findForUser(1L, "", -1).page());
    }
    @Test public void accountLibraryCountMatchesDistinctCompletedGamesWhileOrderCountKeepsHistory() throws Exception {
        order(1L, "complete", 1, 2); legacyRepeat(1L, 1); order(1L, "pending", 3); order(1L, "cancel", 4);
        var overview = new JdbcAccountRepository(JdbcLibraryRepositoryTest::connection).findForUser(1L);
        assertEquals(4L, overview.orderCount()); assertEquals(2L, overview.libraryCount());
        var empty = library().findForUser(2L, "", 999); assertEquals(0, empty.totalCount()); assertEquals(1, empty.page()); assertEquals(0, empty.getFirstResult());
    }
}
