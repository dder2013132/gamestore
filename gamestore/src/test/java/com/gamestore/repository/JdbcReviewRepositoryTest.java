package com.gamestore.repository;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

/** Opt-in PostgreSQL tests in a disposable schema, never the application's tables. */
public class JdbcReviewRepositoryTest {
    private static String schema; private static boolean created;
    private static Connection connection() throws SQLException, java.io.IOException, ClassNotFoundException {
        Connection conn = DBConnection.getConnection();
        try { conn.setSchema(schema); return conn; } catch (SQLException e) { conn.close(); throw e; }
    }
    private JdbcReviewRepository reviews() { return new JdbcReviewRepository(JdbcReviewRepositoryTest::connection); }
    private JdbcOrderRepository orders() { return new JdbcOrderRepository(JdbcReviewRepositoryTest::connection); }
    @BeforeClass public static void setup() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("gamestore.dbTest")); schema = "gamestore_review_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) { stmt.execute("CREATE SCHEMA " + schema); created = true; }
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE games (game_id INTEGER PRIMARY KEY, title TEXT, price INTEGER, genre TEXT, image_url TEXT, description TEXT, is_active BOOLEAN NOT NULL DEFAULT TRUE)");
            stmt.executeUpdate("INSERT INTO games (game_id, title, price, genre, image_url) SELECT n, 'Game ' || n, n * 100, 'Adventure', 'images/aetherbound.png' FROM generate_series(1,12) n");
            for (String file : List.of("006_create_users.sql", "004_create_orders.sql", "005_create_order_items.sql", "007_add_order_user.sql", "014_order_checkout_tokens.sql", "016_order_status.sql", "017_create_reviews.sql")) {
                stmt.execute(Files.readString(Path.of("sql", file)));
            }
        }
    }
    @AfterClass public static void cleanup() throws Exception {
        if (!created) { return; }
        if (!schema.matches("gamestore_review_test_[a-f0-9]{32}")) { throw new IllegalStateException("Unexpected test schema"); }
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) { stmt.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
    @Before public void reset() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE users RESTART IDENTITY CASCADE");
            stmt.executeUpdate("INSERT INTO users (username, password_hash) SELECT 'member_' || n, 'unused' FROM generate_series(1,8) n");
            stmt.executeUpdate("UPDATE games SET is_active = TRUE");
        }
    }
    private String order(long uid, boolean complete, int... ids) throws Exception {
        List<Game> games = Arrays.stream(ids).mapToObj(i -> new Game("" + i, "Game " + i, i * 100, "Adventure", "images/aetherbound.png")).toList();
        String id = UUID.randomUUID().toString(); orders().save(new Order(id, games, games.stream().mapToInt(Game::getPrice).sum(), uid));
        if (complete) { orders().completeDemoForUser(id, uid); } return id;
    }
    @Test public void onlyCompletedPurchaseOfSameGameAndMemberAllowsReview() throws Exception {
        assertEquals(ReviewResult.NOT_ELIGIBLE, reviews().create(1L, 1, 5, "No order"));
        String pending = order(1L, false, 1);
        assertFalse(reviews().eligible(1L, 1)); assertEquals(ReviewResult.NOT_ELIGIBLE, reviews().create(1L, 1, 5, "Pending"));
        orders().cancelPendingForUser(pending, 1L); assertFalse(reviews().eligible(1L, 1));
        order(2L, true, 1); assertFalse(reviews().eligible(1L, 1));
        order(1L, true, 2); assertFalse(reviews().eligible(1L, 1));
        order(1L, true, 1); assertTrue(reviews().eligible(1L, 1));
        assertEquals(ReviewResult.SAVED, reviews().create(1L, 1, 5, "Completed"));
        assertNull(reviews().findOwn(2L, 1));
    }
    @Test public void createUpdateDeleteRecalculateAverageAndPreserveCreatedTime() throws Exception {
        order(1L, true, 1); order(2L, true, 1);
        reviews().create(1L, 1, 5, "First"); reviews().create(2L, 1, 3, "Second");
        var summary = reviews().findForGame(1, null, 1); assertEquals(2, summary.page().totalCount()); assertEquals(4.0, summary.page().averageRating(), 0.00001);
        Review own = reviews().findOwn(1L, 1);
        assertEquals(ReviewResult.SAVED, reviews().update(1L, 1, own.id(), own.version(), 1, "Updated"));
        Review updated = reviews().findOwn(1L, 1); assertEquals(own.createdAt(), updated.createdAt()); assertEquals(1L, updated.version()); assertTrue(updated.isEdited());
        assertEquals(2.0, reviews().findForGame(1, null, 1).page().averageRating(), 0.00001);
        assertEquals(ReviewResult.SAVED, reviews().delete(1L, 1, updated.id(), updated.version()));
        assertEquals(1, reviews().findForGame(1, null, 1).page().totalCount()); assertEquals(3.0, reviews().findForGame(1, null, 1).page().averageRating(), 0.00001);
        Review other = reviews().findOwn(2L, 1); reviews().delete(2L, 1, other.id(), other.version());
        var empty = reviews().findForGame(1, null, 999).page(); assertEquals(0, empty.totalCount()); assertEquals(0.0, empty.averageRating(), 0); assertEquals(1, empty.page());
    }
    @Test public void duplicateAndConcurrentCreatesNeverOverwriteExistingReview() throws Exception {
        order(1L, true, 1); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<ReviewResult>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) { final int n = i; tasks.add(pool.submit(() -> { start.await(); return reviews().create(1L, 1, n + 1, "Review " + n); })); }
            start.countDown(); int saved = 0, duplicates = 0;
            for (var task : tasks) { var result = task.get(15, TimeUnit.SECONDS); if (result == ReviewResult.SAVED) { saved++; } if (result == ReviewResult.ALREADY_EXISTS) { duplicates++; } }
            assertEquals(1, saved); assertEquals(3, duplicates);
        } finally { pool.shutdownNow(); }
        Review before = reviews().findOwn(1L, 1); assertEquals(ReviewResult.ALREADY_EXISTS, reviews().create(1L, 1, 5, "Overwrite attempt"));
        assertEquals(before, reviews().findOwn(1L, 1)); assertEquals(1, reviews().findForGame(1, null, 1).page().totalCount());
    }
    @Test public void ownershipAndGameIdentityAreRequiredForUpdateAndDelete() throws Exception {
        order(1L, true, 1, 2); order(2L, true, 1); reviews().create(1L, 1, 5, "Owner's review");
        Review own = reviews().findOwn(1L, 1);
        assertEquals(ReviewResult.NOT_FOUND, reviews().update(2L, 1, own.id(), own.version(), 1, "Forged author"));
        assertEquals(ReviewResult.NOT_FOUND, reviews().delete(2L, 1, own.id(), own.version()));
        assertEquals(ReviewResult.NOT_FOUND, reviews().update(1L, 2, own.id(), own.version(), 1, "Wrong game"));
        assertEquals(ReviewResult.NOT_FOUND, reviews().delete(1L, 2, own.id(), own.version()));
        assertEquals(own, reviews().findOwn(1L, 1));
    }
    @Test public void staleFormsAndDeletedThenRecreatedIdentityCannotOverwriteReview() throws Exception {
        order(1L, true, 1); reviews().create(1L, 1, 5, "Original"); Review old = reviews().findOwn(1L, 1);
        reviews().update(1L, 1, old.id(), old.version(), 4, "Latest");
        assertEquals(ReviewResult.STALE, reviews().update(1L, 1, old.id(), old.version(), 1, "Stale"));
        assertEquals(ReviewResult.STALE, reviews().delete(1L, 1, old.id(), old.version()));
        Review latest = reviews().findOwn(1L, 1); reviews().delete(1L, 1, latest.id(), latest.version()); reviews().create(1L, 1, 3, "Recreated");
        assertNotEquals(old.id(), reviews().findOwn(1L, 1).id());
        assertEquals(ReviewResult.NOT_FOUND, reviews().update(1L, 1, old.id(), 0, 1, "Old identity"));
        assertEquals(ReviewResult.NOT_FOUND, reviews().delete(1L, 1, old.id(), 0));
    }
    @Test public void publicPaginationUsesAllReviewsForAverageAndFindsOwnOutsideCurrentPage() throws Exception {
        for (long uid = 1; uid <= 8; uid++) { order(uid, true, 1); reviews().create(uid, 1, uid <= 4 ? 5 : 3, "Member " + uid); }
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.executeUpdate("UPDATE reviews SET created_at = TIMESTAMP '2026-10-25 12:00:00'"); }
        var first = reviews().findForGame(1, 1L, 1); var second = reviews().findForGame(1, null, 2);
        assertEquals(5, first.page().entries().size()); assertEquals(3, second.page().entries().size()); assertEquals(8, first.page().totalCount());
        assertEquals(4.0, first.page().averageRating(), 0.00001); assertEquals(4.0, second.page().averageRating(), 0.00001);
        assertNotNull(first.own()); assertEquals(1L, first.own().userId()); assertTrue(first.eligible());
        Set<Long> ids = new HashSet<>(); first.page().entries().forEach(r -> assertTrue(ids.add(r.id()))); second.page().entries().forEach(r -> assertTrue(ids.add(r.id())));
        assertEquals(8, ids.size()); assertNull(second.own()); assertFalse(second.eligible());
        assertEquals(2, reviews().findForGame(1, null, Integer.MAX_VALUE).page().page()); assertEquals(1, reviews().findForGame(1, null, -1).page().page());
    }
    @Test public void myReviewsPaginationIsScopedAndPausedGamesRemainReviewableAfterCompletedOrder() throws Exception {
        int[] all = java.util.stream.IntStream.rangeClosed(1, 12).toArray(); order(1L, true, all); order(2L, true, 1);
        for (int id : all) { reviews().create(1L, id, 4, "Own game " + id); } reviews().create(2L, 1, 5, "Other member");
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active = FALSE WHERE game_id = 1"); }
        var first = reviews().findForUser(1L, 1); var last = reviews().findForUser(1L, Integer.MAX_VALUE);
        assertEquals(12, first.totalCount()); assertEquals(10, first.entries().size()); assertEquals(2, last.entries().size());
        assertTrue(first.entries().stream().allMatch(r -> r.userId() == 1L)); assertTrue(reviews().eligible(1L, 1));
        assertEquals(2, reviews().findForGame(1, null, 1).page().totalCount());
    }
    @Test public void inputValidationAndJapaneseHtmlTextAreStoredLiterally() throws Exception {
        order(1L, true, 1);
        for (int rating : new int[] {0, 6}) { try { reviews().create(1L, 1, rating, "Text"); fail("Invalid rating accepted"); } catch (IllegalArgumentException expected) { } }
        for (String body : new String[] {null, " \n\t", "　", "x".repeat(2001)}) {
            try { reviews().create(1L, 1, 5, body); fail("Invalid body accepted"); } catch (IllegalArgumentException expected) { }
        }
        String body = "月明かりがきれい！\n<script>alert('literal')</script> & 楽しい";
        reviews().create(1L, 1, 5, "  " + body + "  "); Review own = reviews().findOwn(1L, 1); assertEquals(body, own.body());
        assertEquals(ReviewResult.SAVED, reviews().update(1L, 1, own.id(), own.version(), 1, "x".repeat(2000)));
        assertEquals(2000, reviews().findOwn(1L, 1).body().length());
    }
    @Test public void migrationIsRepeatableAndDatabaseConstraintsRejectInvalidRows() throws Exception {
        order(1L, true, 1); reviews().create(1L, 1, 5, "Kept review"); Review original = reviews().findOwn(1L, 1);
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("sql/017_create_reviews.sql")));
            for (String sql : List.of("UPDATE reviews SET rating = 0", "UPDATE reviews SET rating = 6", "UPDATE reviews SET body = ''", "UPDATE reviews SET body = '   '", "UPDATE reviews SET version = -1")) {
                try { stmt.executeUpdate(sql); fail("Invalid DB row accepted"); } catch (SQLException expected) { assertEquals("23514", expected.getSQLState()); }
            }
            try { stmt.executeUpdate("INSERT INTO reviews (user_id, game_id, rating, body) VALUES (1, 1, 5, 'Duplicate')"); fail("Duplicate accepted"); }
            catch (SQLException expected) { assertEquals("23505", expected.getSQLState()); }
        }
        assertEquals(original, reviews().findOwn(1L, 1));
    }
    @Test public void concurrentUpdateAndDeleteDoNotBothApplyToSameVersion() throws Exception {
        order(1L, true, 1); reviews().create(1L, 1, 5, "Original"); Review own = reviews().findOwn(1L, 1);
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var edit = pool.submit(() -> { start.await(); return reviews().update(1L, 1, own.id(), own.version(), 4, "Changed"); });
            var delete = pool.submit(() -> { start.await(); return reviews().delete(1L, 1, own.id(), own.version()); }); start.countDown();
            var one = edit.get(15, TimeUnit.SECONDS); var two = delete.get(15, TimeUnit.SECONDS);
            assertTrue((one == ReviewResult.SAVED) ^ (two == ReviewResult.SAVED));
        } finally { pool.shutdownNow(); }
    }
}
