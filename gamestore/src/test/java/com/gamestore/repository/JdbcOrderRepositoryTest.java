package com.gamestore.repository;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gamestore.model.Game;
import com.gamestore.model.Order;
import com.gamestore.util.DBConnection;

/** Opt in: mvn -Dgamestore.dbTest=true test. Uses an isolated, disposable PostgreSQL schema. */
public class JdbcOrderRepositoryTest {
    private static String schema;
    private static boolean schemaCreated;
    private static final LocalDateTime PURCHASE_TIME = LocalDateTime.of(2026, 10, 24, 12, 0);

    private Order single(String id, long userId, int gameId) {
        Game game = new Game("" + gameId, "Game", gameId == 1 ? 4800 : 5900, "Adventure", "first.jpg");
        return new Order(id, List.of(game), game.getPrice(), userId);
    }
    private java.util.Set<String> owned(long userId) throws Exception {
        try (Connection conn = connection()) { return JdbcOwnershipRepository.findIds(conn, userId); }
    }
    @Test public void ownershipUsesOnlyOwnCompletedOrdersAndSurvivesSalePause() throws Exception {
        repository().save(single("own-pending", 1L, 1)); repository().save(single("own-cancelled", 1L, 2));
        repository().cancelPendingForUser("own-cancelled", 1L);
        repository().save(single("other-completed", 2L, 2)); repository().completeDemoForUser("other-completed", 2L);
        assertTrue(owned(1L).isEmpty()); assertEquals(java.util.Set.of("2"), owned(2L));
        repository().completeDemoForUser("own-pending", 1L);
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active = FALSE WHERE game_id = 1"); }
        assertEquals(java.util.Set.of("1"), owned(1L));
    }
    @Test public void freshOwnedOrderRollsBackButCompletedCheckoutReplayStillWorks() throws Exception {
        String originalToken = UUID.randomUUID().toString(), rejectedToken = UUID.randomUUID().toString();
        repository().saveOnce(single("owned-original", 1L, 1), originalToken); repository().completeDemoForUser("owned-original", 1L);
        try { repository().saveOnce(order("owned-new", 1L, PURCHASE_TIME), rejectedToken); fail("Owned order must be rejected"); }
        catch (AlreadyOwnedException expected) { }
        assertNull(repository().findByIdForUser("owned-new", 1L)); assertNull(repository().findIdByCheckoutToken(rejectedToken, 1L));
        assertEquals("owned-original", repository().saveOnce(order("unused-replay", 1L, PURCHASE_TIME), originalToken));
        assertEquals(1, repository().findForUser(1L, null).size()); assertEquals(java.util.Set.of("1"), owned(1L));
        try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery("SELECT COUNT(*) FROM order_items")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
    }
    @Test public void overlappingPendingOrderCannotPartiallyCompleteButCanBeCancelled() throws Exception {
        repository().save(single("first-pending", 1L, 1)); repository().save(order("overlapping-pending", 1L, PURCHASE_TIME));
        repository().completeDemoForUser("first-pending", 1L);
        assertEquals(OrderChangeResult.ALREADY_OWNED, repository().completeDemoForUser("overlapping-pending", 1L));
        Order pending = repository().findByIdForUser("overlapping-pending", 1L);
        assertTrue(pending.isPending()); assertNull(pending.getCompletedAt()); assertEquals(10700, pending.getTotalPrice()); assertEquals(2, pending.getGames().size());
        assertEquals(java.util.Set.of("1"), owned(1L));
        assertEquals(OrderChangeResult.CHANGED, repository().cancelPendingForUser("overlapping-pending", 1L));
        repository().save(single("only-unowned", 1L, 2)); assertEquals(OrderChangeResult.CHANGED, repository().completeDemoForUser("only-unowned", 1L));
        assertEquals(java.util.Set.of("1", "2"), owned(1L));
    }
    @Test public void simultaneousDifferentOrdersForSameGameCompleteOnlyOne() throws Exception {
        repository().save(single("race-first", 1L, 1)); repository().save(single("race-second", 1L, 1));
        var results = payTogether("race-first", 1L, "race-second", 1L);
        assertEquals(1, java.util.Collections.frequency(results, OrderChangeResult.CHANGED));
        assertEquals(1, java.util.Collections.frequency(results, OrderChangeResult.ALREADY_OWNED));
        assertEquals(1, repository().findForUser(1L, null).stream().filter(Order::isCompleted).count());
        assertEquals(1, repository().findForUser(1L, null).stream().filter(Order::isPending).count());
        for (Order row : repository().findForUser(1L, null)) {
            assertEquals(row.isCompleted() ? OrderChangeResult.ALREADY_APPLIED : OrderChangeResult.ALREADY_OWNED,
                    repository().completeDemoForUser(row.getOrderId(), 1L));
        }
    }
    @Test public void simultaneousDisjointOrdersForMemberCanBothComplete() throws Exception {
        repository().save(single("different-first", 1L, 1)); repository().save(single("different-second", 1L, 2));
        assertEquals(List.of(OrderChangeResult.CHANGED, OrderChangeResult.CHANGED), payTogether("different-first", 1L, "different-second", 1L));
        assertEquals(java.util.Set.of("1", "2"), owned(1L));
    }
    @Test public void differentMembersCanCompleteOrdersForSameGame() throws Exception {
        repository().save(single("alice-same", 1L, 1)); repository().save(single("bobby-same", 2L, 1));
        assertEquals(List.of(OrderChangeResult.CHANGED, OrderChangeResult.CHANGED), payTogether("alice-same", 1L, "bobby-same", 2L));
        assertEquals(java.util.Set.of("1"), owned(1L)); assertEquals(java.util.Set.of("1"), owned(2L));
    }
    private List<OrderChangeResult> payTogether(String first, long firstUser, String second, long secondUser) throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2); var gate = new java.util.concurrent.CountDownLatch(1);
        try {
            var a = pool.submit(() -> { gate.await(); return repository().completeDemoForUser(first, firstUser); });
            var b = pool.submit(() -> { gate.await(); return repository().completeDemoForUser(second, secondUser); });
            gate.countDown();
            return List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS), b.get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test public void creatingOrderWaitsForMemberPaymentAndThenSeesNewOwnership() throws Exception {
        repository().save(single("pay-in-flight", 1L, 1));
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor(); var started = new java.util.concurrent.CountDownLatch(1);
        try (Connection blocker = connection(); Statement stmt = blocker.createStatement()) {
            blocker.setAutoCommit(false); stmt.executeQuery("SELECT user_id FROM users WHERE user_id = 1 FOR UPDATE").close();
            stmt.executeUpdate("UPDATE orders SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE order_id = 'pay-in-flight'");
            var purchase = pool.submit(() -> {
                var concurrent = new JdbcOrderRepository(() -> {
                    try { Connection conn = connection(); started.countDown(); return conn; } catch (Exception e) { throw new SQLException(e); }
                });
                concurrent.save(single("stale-new-order", 1L, 1)); return null;
            });
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            try { purchase.get(200, java.util.concurrent.TimeUnit.MILLISECONDS); fail("Must wait for member lock"); }
            catch (java.util.concurrent.TimeoutException expected) { }
            blocker.commit();
            try { purchase.get(5, java.util.concurrent.TimeUnit.SECONDS); fail("Must observe committed ownership"); }
            catch (java.util.concurrent.ExecutionException expected) { assertTrue(expected.getCause() instanceof AlreadyOwnedException); }
            assertNull(repository().findByIdForUser("stale-new-order", 1L));
        } finally { pool.shutdownNow(); }
    }
    @Test public void duplicateGameWithinOneOrderDoesNotLeaveOrderOrItems() throws Exception {
        Game game = single("unused", 1L, 1).getGames().get(0);
        try { repository().save(new Order("duplicate-items", List.of(game, game), 9600, 1L)); fail("Duplicate items must be rejected"); }
        catch (IllegalArgumentException expected) { }
        assertNull(repository().findByIdForUser("duplicate-items", 1L)); assertTrue(owned(1L).isEmpty());
    }

    private static Connection connection() throws Exception {
        Connection connection = DBConnection.getConnection();
        try {
            connection.setSchema(schema);
            return connection;
        } catch (Exception e) {
            connection.close();
            throw e;
        }
    }

    private JdbcOrderRepository repository() {
        return new JdbcOrderRepository(() -> {
            try { return connection(); }
            catch (SQLException | java.io.IOException | ClassNotFoundException e) { throw e; }
            catch (Exception e) { throw new SQLException(e); }
        });
    }

    private JdbcAdminOrderRepository adminRepository() {
        return new JdbcAdminOrderRepository(() -> {
            try { return connection(); }
            catch (SQLException | java.io.IOException | ClassNotFoundException e) { throw e; }
            catch (Exception e) { throw new SQLException(e); }
        });
    }

    @Test
    public void newOrdersAwaitPaymentAndCompletionIsIdempotentAndMemberScoped() throws Exception {
        repository().save(order("pending-order", 1L, PURCHASE_TIME));
        Order pending = repository().findByIdForUser("pending-order", 1L);
        assertTrue(pending.isPending()); assertNull(pending.getCompletedAt()); assertNull(pending.getCancelledAt());
        assertEquals(OrderChangeResult.NOT_FOUND, repository().completeDemoForUser("pending-order", 2L));
        assertEquals(OrderChangeResult.CHANGED, repository().completeDemoForUser("pending-order", 1L));
        Order completed = repository().findByIdForUser("pending-order", 1L);
        assertTrue(completed.isCompleted()); assertNotNull(completed.getCompletedAt()); assertNull(completed.getCancelledAt());
        assertEquals(OrderChangeResult.ALREADY_APPLIED, repository().completeDemoForUser("pending-order", 1L));
        assertEquals(completed.getCompletedAt(), repository().findByIdForUser("pending-order", 1L).getCompletedAt());
        assertEquals(OrderChangeResult.CONFLICT, repository().cancelPendingForUser("pending-order", 1L));
    }

    @Test
    public void cancelledOrdersKeepPricesItemsAndHistoryAndCannotBePaid() throws Exception {
        repository().save(order("cancel-order", 1L, PURCHASE_TIME));
        assertEquals(OrderChangeResult.NOT_FOUND, repository().cancelPendingForUser("cancel-order", 2L));
        assertEquals(OrderChangeResult.CHANGED, repository().cancelPendingForUser("cancel-order", 1L));
        Order cancelled = repository().findByIdForUser("cancel-order", 1L);
        assertTrue(cancelled.isCancelled()); assertNotNull(cancelled.getCancelledAt()); assertNull(cancelled.getCompletedAt());
        assertEquals(10700, cancelled.getTotalPrice()); assertEquals(2, cancelled.getGames().size());
        assertEquals(1, repository().findForUser(1L, null).size());
        assertEquals(OrderChangeResult.ALREADY_APPLIED, repository().cancelPendingForUser("cancel-order", 1L));
        assertEquals(cancelled.getCancelledAt(), repository().findByIdForUser("cancel-order", 1L).getCancelledAt());
        assertEquals(OrderChangeResult.CONFLICT, repository().completeDemoForUser("cancel-order", 1L));
    }

    @Test
    public void simultaneousPaymentAndCancellationHaveExactlyOneWinner() throws Exception {
        repository().save(order("race-order", 1L, PURCHASE_TIME));
        var gate = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<OrderChangeResult>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                boolean pay = i % 2 == 0;
                tasks.add(pool.submit(() -> { gate.await(); return pay ? repository().completeDemoForUser("race-order", 1L)
                        : repository().cancelPendingForUser("race-order", 1L); }));
            }
            gate.countDown(); int changed = 0, conflicts = 0;
            for (var task : tasks) {
                var result = task.get(10, java.util.concurrent.TimeUnit.SECONDS);
                if (result == OrderChangeResult.CHANGED) { changed++; }
                if (result == OrderChangeResult.CONFLICT) { conflicts++; }
            }
            assertEquals(1, changed); assertEquals(2, conflicts);
            Order result = repository().findByIdForUser("race-order", 1L);
            assertFalse(result.isPending()); assertTrue((result.getCompletedAt() == null) ^ (result.getCancelledAt() == null));
            assertEquals(2, result.getGames().size());
        } finally { pool.shutdownNow(); }
    }

    @Test
    public void replayingCheckoutAfterCompletionOrCancellationCannotReopenOrder() throws Exception {
        for (boolean pay : new boolean[] {false, true}) {
            String id = "replay-" + pay, token = UUID.randomUUID().toString();
            repository().saveOnce(order(id, 1L, PURCHASE_TIME), token);
            if (pay) { repository().completeDemoForUser(id, 1L); } else { repository().cancelPendingForUser(id, 1L); }
            assertEquals(id, repository().saveOnce(order("unused-" + pay, 1L, PURCHASE_TIME), token));
            assertEquals(pay, repository().findByIdForUser(id, 1L).isCompleted());
            assertEquals(!pay, repository().findByIdForUser(id, 1L).isCancelled());
        }
        assertEquals(2, repository().findForUser(1L, null).size());
    }

    @Test
    public void adminFiltersStatusAndExcludesCancelledAmountsWithoutHidingHistory() throws Exception {
        for (String id : List.of("pending", "complete", "cancelled")) { repository().save(order(id, 1L, PURCHASE_TIME)); }
        repository().completeDemoForUser("complete", 1L); repository().cancelPendingForUser("cancelled", 1L);
        var all = adminRepository().findPage("", null, null, "", 1, 10);
        assertEquals(3, all.resultCount()); assertEquals(21400L, all.totalPrice());
        var cancelled = adminRepository().findPage("", null, null, "CANCELLED", 1, 10);
        assertEquals(1, cancelled.resultCount()); assertEquals(0L, cancelled.totalPrice());
        assertTrue(cancelled.orders().get(0).order().isCancelled());
        assertEquals(1, adminRepository().findPage("", null, null, "COMPLETED", 1, 10).resultCount());
        assertEquals(1, adminRepository().findPage("", null, null, "PENDING", 1, 10).resultCount());
        assertTrue(adminRepository().findById("complete").order().isCompleted());
        assertNotNull(adminRepository().findById("cancelled").order().getCancelledAt());
        try { adminRepository().findPage("", null, null, "' OR TRUE --", 1, 10); fail("Invalid status accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test
    public void statusMigrationPreservesOldCompletedOrdersAndDoesNotResetNewStates() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            // A temporary shadow table models the old schema without changing the shared fixture.
            stmt.execute("CREATE TEMP TABLE orders (order_id VARCHAR(36) PRIMARY KEY, total_price INTEGER NOT NULL, created_at TIMESTAMP, user_id BIGINT, checkout_token UUID)");
            stmt.executeUpdate("INSERT INTO orders VALUES ('legacy', 4800, TIMESTAMP '2026-10-24 12:00:00', 1, NULL)");
            String migration = Files.readString(Path.of("sql/016_order_status.sql")); stmt.execute(migration);
            try (ResultSet rs = stmt.executeQuery("SELECT status, completed_at, total_price FROM orders WHERE order_id = 'legacy'")) {
                assertTrue(rs.next()); assertEquals("COMPLETED", rs.getString(1)); assertEquals(PURCHASE_TIME, rs.getObject(2, LocalDateTime.class)); assertEquals(4800, rs.getInt(3));
            }
            stmt.executeUpdate("INSERT INTO orders (order_id, total_price, user_id) VALUES ('new-order', 2400, 1)");
            try (ResultSet rs = stmt.executeQuery("SELECT status FROM orders WHERE order_id = 'new-order'")) { rs.next(); assertEquals("PENDING", rs.getString(1)); }
            stmt.executeUpdate("UPDATE orders SET status = 'CANCELLED', cancelled_at = TIMESTAMP '2026-10-25 12:00:00' WHERE order_id = 'new-order'");
            stmt.execute(migration);
            try (ResultSet rs = stmt.executeQuery("SELECT status, cancelled_at FROM orders WHERE order_id = 'new-order'")) {
                rs.next(); assertEquals("CANCELLED", rs.getString(1)); assertEquals(PURCHASE_TIME.plusDays(1), rs.getObject(2, LocalDateTime.class));
            }
            for (String bad : List.of("status = 'UNKNOWN'", "status = 'PENDING'", "completed_at = CURRENT_TIMESTAMP")) {
                try { stmt.executeUpdate("UPDATE orders SET " + bad + " WHERE order_id = 'new-order'"); fail("Invalid order state accepted"); }
                catch (SQLException expected) { assertEquals("23514", expected.getSQLState()); }
            }
        }
    }

    @Test
    public void adminListsMultipleMembersWithoutChangingMemberPrivacy() throws Exception {
        repository().save(order("alice-order", 1L, PURCHASE_TIME));
        repository().save(order("bobby-order", 2L, PURCHASE_TIME.plusHours(1)));
        var result = adminRepository().findPage("", null, null, 1, 10);
        assertEquals(2, result.resultCount()); assertEquals(21400L, result.totalPrice());
        assertEquals("test_bobby", result.orders().get(0).username());
        assertEquals(2, result.orders().get(0).itemCount());
        assertEquals(1, repository().findForUser(1L, null).size());
        assertNull(repository().findByIdForUser("bobby-order", 1L));
    }

    @Test
    public void adminSearchTreatsSqlCharactersLiterallyAndMatchesBuyerOrOrder() throws Exception {
        repository().save(order("literal_%_order", 1L, PURCHASE_TIME));
        repository().save(order("bobby-order", 2L, PURCHASE_TIME));
        assertEquals(1, adminRepository().findPage("ALICE", null, null, 1, 10).resultCount());
        assertEquals(1, adminRepository().findPage("BOBBY-ORDER", null, null, 1, 10).resultCount());
        assertEquals(1, adminRepository().findPage("_%", null, null, 1, 10).resultCount());
        assertEquals(0, adminRepository().findPage("' OR TRUE --", null, null, 1, 10).resultCount());
    }

    @Test
    public void adminDateFiltersIncludeBothBoundaryDaysAndExcludeFollowingMidnight() throws Exception {
        var day = PURCHASE_TIME.toLocalDate();
        repository().save(order("before", 1L, day.minusDays(1).atTime(23, 59, 59)));
        repository().save(order("start", 1L, day.atStartOfDay()));
        repository().save(order("end", 2L, day.atTime(23, 59, 59, 999999000)));
        repository().save(order("after", 2L, day.plusDays(1).atStartOfDay()));
        var result = adminRepository().findPage("", day, day, 1, 10);
        assertEquals(2, result.resultCount()); assertEquals(21400L, result.totalPrice());
        assertEquals(List.of("end", "start"), result.orders().stream().map(e -> e.order().getOrderId()).toList());
        assertEquals(3, adminRepository().findPage("", day, null, 1, 10).resultCount());
        assertEquals(3, adminRepository().findPage("", null, day, 1, 10).resultCount());
    }

    @Test
    public void adminPaginationHasStableOrderAndWholeResultTotals() throws Exception {
        for (int i = 0; i < 23; i++) { repository().save(order(String.format("order-%02d", i), i % 2 + 1L, PURCHASE_TIME)); }
        var first = adminRepository().findPage("order-", null, null, 1, 10);
        var second = adminRepository().findPage("order-", null, null, 2, 10);
        var last = adminRepository().findPage("order-", null, null, Integer.MAX_VALUE, 10);
        assertEquals(23, first.resultCount()); assertEquals(246100L, second.totalPrice());
        assertEquals(10, first.orders().size()); assertEquals(10, second.orders().size()); assertEquals(3, last.orders().size());
        assertEquals(3, last.page()); assertEquals(21, last.firstResult()); assertEquals(23, last.lastResult());
        var ids = new java.util.HashSet<String>();
        for (var page : List.of(first, second, last)) { for (var entry : page.orders()) { assertTrue(ids.add(entry.order().getOrderId())); } }
        assertEquals("order-22", first.orders().get(0).order().getOrderId());
        assertEquals(1, adminRepository().findPage("order-", null, null, -1, 10).page());
        var empty = adminRepository().findPage("missing", null, null, 9, 10);
        assertEquals(1, empty.page()); assertEquals(0, empty.firstResult()); assertEquals(0L, empty.totalPrice());
    }

    @Test
    public void adminDetailUsesPurchasePricesAndIncludesPausedGames() throws Exception {
        repository().save(order("detail-order", 2L, PURCHASE_TIME));
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE games SET price = 1, is_active = FALSE WHERE game_id = 1");
        }
        var detail = adminRepository().findById("detail-order");
        assertEquals("test_bobby", detail.username()); assertEquals(2, detail.itemCount());
        assertEquals(4800, detail.order().getGames().get(0).getPrice()); assertEquals(10700, detail.order().getTotalPrice());
        assertEquals(PURCHASE_TIME, detail.order().getCreatedAt());
        assertNull(adminRepository().findById("' OR TRUE --")); assertNull(adminRepository().findById("missing"));
    }

    @Test
    public void adminAggregatesDoNotMultiplyOrdersByItemCountOrOverflowInteger() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO orders (order_id, user_id, total_price) VALUES ('large-1', 1, 2000000000), ('large-2', 2, 2000000000)");
        }
        var result = adminRepository().findPage("large-", null, null, 1, 10);
        assertEquals(4000000000L, result.totalPrice()); assertEquals(2, result.resultCount());
        assertEquals(0, result.orders().get(0).itemCount());
        assertEquals(0, adminRepository().findById("large-1").itemCount());
    }

    @BeforeClass
    public static void createSchema() throws Exception {
        Assume.assumeTrue("Enable PostgreSQL tests with -Dgamestore.dbTest=true", Boolean.getBoolean("gamestore.dbTest"));
        schema = "gamestore_order_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DBConnection.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA " + schema);
            schemaCreated = true;
        }
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE games (game_id INTEGER PRIMARY KEY, title TEXT, price INTEGER, genre TEXT, image_url TEXT)");
            for (String file : List.of("006_create_users.sql", "004_create_orders.sql", "005_create_order_items.sql", "007_add_order_user.sql", "014_order_checkout_tokens.sql", "016_order_status.sql")) {
                stmt.execute(Files.readString(Path.of("sql", file)));
            }
            stmt.execute("INSERT INTO users (username, password_hash) VALUES ('test_alice', 'unused'), ('test_bobby', 'unused')");
            stmt.execute("INSERT INTO games VALUES (1, 'First game', 4800, 'Adventure', 'first.jpg'), (2, 'Second game', 5900, 'Racing', 'second.jpg')");
            stmt.execute(Files.readString(Path.of("sql/012_add_game_sale_status.sql")));
        }
    }

    @AfterClass
    public static void dropSchema() throws Exception {
        if (!schemaCreated) { return; }
        if (!schema.matches("gamestore_order_test_[a-f0-9]{32}")) { throw new IllegalStateException("Unexpected schema name"); }
        try (Connection connection = DBConnection.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Before
    public void clearOrders() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM order_items");
            stmt.executeUpdate("DELETE FROM orders");
            stmt.executeUpdate("UPDATE games SET price = CASE game_id WHEN 1 THEN 4800 ELSE 5900 END, is_active = TRUE");
        }
    }

    private Order order(String id, long userId, LocalDateTime time) {
        return new Order(id, List.of(new Game("1", "First game", 4800, "Adventure", "first.jpg"),
                new Game("2", "Second game", 5900, "Racing", "second.jpg")), 10700, userId, time);
    }

    @Test
    public void savesAndReloadsFullOrderThroughFreshRepository() throws Exception {
        String id = UUID.randomUUID().toString();
        repository().save(order(id, 1L, PURCHASE_TIME));
        Order restored = repository().findByIdForUser(id, 1L);
        assertNotNull(restored);
        assertEquals(Long.valueOf(1), restored.getUserId());
        assertEquals(PURCHASE_TIME, restored.getCreatedAt());
        assertEquals(10700, restored.getTotalPrice());
        assertEquals(2, restored.getGames().size());
        assertEquals("First game", restored.getGames().get(0).getTitle());
        assertEquals(5900, restored.getGames().get(1).getPrice());
        assertNull(repository().findByIdForUser(id, 2L));
        assertNull(repository().findByIdForUser("missing", 1L));
    }

    @Test
    public void purchasePriceDoesNotChangeWithCatalogPrice() throws Exception {
        String id = UUID.randomUUID().toString();
        repository().save(order(id, 1L, PURCHASE_TIME));
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE games SET price = 9999 WHERE game_id = 1");
        }
        Order restored = repository().findByIdForUser(id, 1L);
        assertEquals(4800, restored.getGames().get(0).getPrice());
        assertEquals(10700, restored.getTotalPrice());
    }

    @Test
    public void itemFailureRollsBackOrderAndAllItems() throws Exception {
        Order invalid = order(UUID.randomUUID().toString(), 1L, PURCHASE_TIME);
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE order_items ADD CONSTRAINT reject_second_fixture_item CHECK (game_id <> 2)");
        }
        try {
            repository().save(invalid);
            fail("Expected the second item to fail");
        } catch (SQLException expected) {
            // A valid item precedes the invalid item; neither may remain after rollback.
        } finally {
            try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE order_items DROP CONSTRAINT reject_second_fixture_item");
            }
        }
        try (Connection connection = connection(); Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT (SELECT COUNT(*) FROM orders), (SELECT COUNT(*) FROM order_items)")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
            assertEquals(0, rs.getInt(2));
        }
    }

    @Test
    public void listAndLiteralSearchStayWithinMemberAndSortByPurchaseTime() throws Exception {
        repository().save(order("alice-first", 1L, PURCHASE_TIME));
        repository().save(order("alice-second", 1L, PURCHASE_TIME.plusDays(1)));
        repository().save(order("bob-order", 2L, PURCHASE_TIME.plusDays(2)));
        List<Order> all = repository().findForUser(1L, " ");
        assertEquals(2, all.size());
        assertEquals("alice-second", all.get(0).getOrderId());
        assertEquals(2, all.get(0).getGames().size());
        assertEquals(1, repository().findForUser(1L, "first").size());
        assertTrue(repository().findForUser(1L, "bob").isEmpty());
        assertTrue(repository().findForUser(1L, "%").isEmpty());
        assertTrue(repository().findForUser(1L, "' OR 1=1 --").isEmpty());
    }

    @Test
    public void missingMemberIsRejectedWithoutSaving() throws Exception {
        try {
            repository().save(order("invalid-member", 999999L, PURCHASE_TIME));
            fail("Expected the member foreign key to reject the order");
        } catch (SQLException expected) { }
        assertTrue(repository().findForUser(999999L, null).isEmpty());
    }

    @Test
    public void suspendedGameKeepsHistoryButBlocksNewOrdersUntilResumed() throws Exception {
        repository().save(order("before-pause", 1L, PURCHASE_TIME));
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE games SET is_active = FALSE WHERE game_id = 1");
        }
        Order historical = repository().findByIdForUser("before-pause", 1L);
        assertNotNull(historical);
        assertEquals(4800, historical.getGames().get(0).getPrice());
        try {
            repository().save(order("during-pause", 1L, PURCHASE_TIME));
            fail("Suspended game must be rejected inside the transaction");
        } catch (CatalogConflictException expected) { }
        assertNull(repository().findByIdForUser("during-pause", 1L));
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE games SET is_active = TRUE WHERE game_id = 1");
        }
        repository().save(order("after-resume", 1L, PURCHASE_TIME));
        assertEquals(2, repository().findForUser(1L, null).size());
    }

    @Test
    public void stalePriceIsRejectedBeforeAnyOrderOrItemIsWritten() throws Exception {
        try (Connection connection = connection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE games SET price = 9999 WHERE game_id = 1");
        }
        try {
            repository().save(order("stale-price", 1L, PURCHASE_TIME));
            fail("Stale price must be rejected");
        } catch (CatalogConflictException expected) { }
        assertTrue(repository().findForUser(1L, null).isEmpty());
    }

    @Test
    public void concurrentSaleSuspensionIsObservedAfterWaitingForItsTransaction() throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        try (Connection blocker = connection(); Statement stmt = blocker.createStatement()) {
            blocker.setAutoCommit(false);
            stmt.executeUpdate("UPDATE games SET is_active = FALSE WHERE game_id = 1");
            java.util.concurrent.Future<?> purchase = executor.submit(() -> {
                JdbcOrderRepository concurrent = new JdbcOrderRepository(() -> {
                    try {
                        Connection conn = connection();
                        started.countDown();
                        return conn;
                    } catch (Exception e) { throw new SQLException(e); }
                });
                concurrent.save(order("concurrent-pause", 1L, PURCHASE_TIME));
                return null;
            });
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            try {
                purchase.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                fail("Purchase should wait for the administrator's transaction");
            } catch (java.util.concurrent.TimeoutException expected) { }
            blocker.commit();
            try {
                purchase.get(5, java.util.concurrent.TimeUnit.SECONDS);
                fail("Purchase must observe the committed suspension");
            } catch (java.util.concurrent.ExecutionException expected) {
                assertTrue(expected.getCause() instanceof CatalogConflictException);
            }
            assertNull(repository().findByIdForUser("concurrent-pause", 1L));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void repeatedCheckoutReturnsOriginalOrderEvenAfterCatalogChanges() throws Exception {
        String token = UUID.randomUUID().toString();
        assertEquals("first-checkout", repository().saveOnce(order("first-checkout", 1L, PURCHASE_TIME), token));
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.executeUpdate("UPDATE games SET price = 9999, is_active = FALSE WHERE game_id = 1"); }
        assertEquals("first-checkout", repository().saveOnce(order("duplicate-checkout", 1L, PURCHASE_TIME.plusDays(1)), token));
        assertEquals("first-checkout", repository().findIdByCheckoutToken(token, 1L));
        assertEquals(1, repository().findForUser(1L, null).size());
        Order original = repository().findByIdForUser("first-checkout", 1L);
        assertEquals(10700, original.getTotalPrice()); assertEquals(PURCHASE_TIME, original.getCreatedAt());
        assertEquals(2, original.getGames().size()); assertEquals(4800, original.getGames().get(0).getPrice());
    }

    @Test
    public void checkoutTokensAreScopedToMember() throws Exception {
        String token = UUID.randomUUID().toString();
        repository().saveOnce(order("alice-token", 1L, PURCHASE_TIME), token);
        assertNull(repository().findIdByCheckoutToken(token, 2L));
        assertEquals("bob-token", repository().saveOnce(order("bob-token", 2L, PURCHASE_TIME), token));
        assertEquals("alice-token", repository().findIdByCheckoutToken(token, 1L));
        assertEquals("bob-token", repository().findIdByCheckoutToken(token, 2L));
        assertNull(repository().findIdByCheckoutToken("' OR 1=1", 1L));
    }

    @Test
    public void simultaneousCheckoutSubmissionsCreateOneOrderAndOneSetOfItems() throws Exception {
        String token = UUID.randomUUID().toString();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var ready = new java.util.concurrent.CountDownLatch(4);
        var start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<String>> submissions = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                String id = "parallel-" + i;
                submissions.add(executor.submit(() -> { ready.countDown(); start.await(); return repository().saveOnce(order(id, 1L, PURCHASE_TIME), token); }));
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            java.util.Set<String> results = new java.util.HashSet<>();
            for (var submission : submissions) { results.add(submission.get(10, java.util.concurrent.TimeUnit.SECONDS)); }
            assertEquals(1, results.size()); assertEquals(1, repository().findForUser(1L, null).size());
            try (Connection conn = connection(); Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery("SELECT COUNT(*) FROM order_items")) {
                rows.next(); assertEquals(2, rows.getInt(1));
            }
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    public void rolledBackCheckoutDoesNotConsumeToken() throws Exception {
        String token = UUID.randomUUID().toString();
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active = FALSE WHERE game_id = 1"); }
        try { repository().saveOnce(order("failed-token", 1L, PURCHASE_TIME), token); fail("Paused game must fail"); }
        catch (CatalogConflictException expected) { }
        assertNull(repository().findIdByCheckoutToken(token, 1L));
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active = TRUE WHERE game_id = 1"); }
        assertEquals("retried-token", repository().saveOnce(order("retried-token", 1L, PURCHASE_TIME), token));
        assertEquals(1, repository().findForUser(1L, null).size());
    }

    @Test
    public void lostCommitAcknowledgementCanBeRecoveredByToken() throws Exception {
        String token = UUID.randomUUID().toString();
        JdbcOrderRepository uncertain = new JdbcOrderRepository(() -> {
            Connection original;
            try { original = connection(); } catch (Exception e) { throw new SQLException(e); }
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                try {
                    Object value = method.invoke(original, args);
                    if ("commit".equals(method.getName())) { throw new SQLException("Commit succeeded but acknowledgement was lost"); }
                    return value;
                } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
            });
        });
        try { uncertain.saveOnce(order("ack-lost", 1L, PURCHASE_TIME), token); fail("Expected lost acknowledgement"); }
        catch (SQLException expected) { }
        assertEquals("ack-lost", repository().findIdByCheckoutToken(token, 1L));
        assertEquals("ack-lost", repository().saveOnce(order("retry-after-ack", 1L, PURCHASE_TIME), token));
        assertEquals(1, repository().findForUser(1L, null).size());
    }
}
