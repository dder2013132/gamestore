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
            for (String file : List.of("006_create_users.sql", "004_create_orders.sql", "005_create_order_items.sql", "007_add_order_user.sql", "014_order_checkout_tokens.sql")) {
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
