package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.support.MemoryOrders;
import com.gamestore.support.WebFixture;
import com.gamestore.util.CheckoutTokens;

import junit.framework.TestCase;

public class OrderServletTest extends TestCase {
    public void testNewlyOwnedCartGameBlocksStaleOrderConfirmationWithoutClearingCart() throws Exception {
        MemoryOrders orders = new MemoryOrders(); WebFixture web = cart(orders);
        web.attributes.put("ownedIds", java.util.Map.of("1", true));
        Object original = web.sessionValues.get("cart"); OrderServlet servlet = new OrderServlet(new Games()); servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status); assertEquals(0, orders.saves); assertSame(original, web.sessionValues.get("cart"));
        assertEquals(true, web.attributes.get("orderBlocked")); assertEquals("/checkout.jsp", web.forwarded);
    }
    public void testOwnershipRaceAtDatabaseReturnsConflictInsteadOfGenericFailure() throws Exception {
        MemoryOrders orders = new MemoryOrders() {
            @Override public synchronized String saveOnce(com.gamestore.model.Order order, String token) throws SQLException {
                throw new com.gamestore.repository.AlreadyOwnedException();
            }
        };
        WebFixture web = cart(orders); Object original = web.sessionValues.get("cart");
        OrderServlet servlet = new OrderServlet(new Games()); servlet.init(web.config); servlet.doPost(web.request, web.response);
        assertEquals(409, web.status); assertEquals(0, orders.saves); assertSame(original, web.sessionValues.get("cart"));
        assertEquals(true, web.attributes.get("orderBlocked")); assertTrue(web.attributes.get("orderError").toString().contains("ライブラリ"));
    }
    private static class Games extends JdbcGameRepository {
        Game available = new Game("1", "Game", 4800, "Adventure", "game.jpg");
        SQLException failure;

        @Override
        public Game findById(String id) throws SQLException {
            if (failure != null) { throw failure; }
            return available;
        }
    }

    private WebFixture cart(MemoryOrders orders) {
        WebFixture web = new WebFixture();
        web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(7L, "alice"));
        web.sessionValues.put("cart", new ArrayList<>(List.of(new Game("1", "Game", 4800, "Adventure", "game.jpg"))));
        web.contextValues.put("orderRepository", orders);
        @SuppressWarnings("unchecked") List<Game> initial = (List<Game>) web.sessionValues.get("cart");
        web.parameters.put("checkoutToken", CheckoutTokens.prepare(web.session, 7L, initial).token());
        return web;
    }

    public void testOrderUsesCatalogPriceAndMemberThenClearsCart() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        WebFixture web = cart(orders);
        web.parameters.put("total", "1");
        OrderServlet servlet = new OrderServlet(new Games());
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(4800, orders.lastSaved.getTotalPrice());
        assertEquals(Long.valueOf(7), orders.lastSaved.getUserId());
        assertEquals(4800, orders.lastSaved.getGames().get(0).getPrice());
        assertNull(web.sessionValues.get("cart"));
        assertEquals("/gamestore/order/complete?id=" + orders.lastSaved.getOrderId(), web.redirect);
    }

    public void testSaveFailureKeepsCartAndShowsCheckoutError() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        orders.failure = new SQLException("database unavailable");
        WebFixture web = cart(orders);
        Object originalCart = web.sessionValues.get("cart");
        OrderServlet servlet = new OrderServlet(new Games());
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertSame(originalCart, web.sessionValues.get("cart"));
        assertEquals(503, web.status);
        assertEquals("/checkout.jsp", web.forwarded);
        assertNotNull(web.attributes.get("orderError"));
        assertNull(web.redirect);
        assertNull(orders.lastSaved);
    }

    public void testUnavailableGameDoesNotSaveOrder() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        WebFixture web = cart(orders);
        Games games = new Games();
        games.available = null;
        OrderServlet servlet = new OrderServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status);
        assertNotNull(web.sessionValues.get("cart"));
        assertNull(orders.lastSaved);
    }

    public void testCatalogFailureKeepsCart() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        WebFixture web = cart(orders);
        Games games = new Games();
        games.failure = new SQLException("catalog unavailable");
        OrderServlet servlet = new OrderServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(503, web.status);
        assertNotNull(web.sessionValues.get("cart"));
        assertNull(orders.lastSaved);
    }

    public void testEmptyCartCannotCreateOrder() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        WebFixture web = cart(orders);
        web.sessionValues.remove("cart");
        OrderServlet servlet = new OrderServlet(new Games());
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(400, web.status);
        assertNull(orders.lastSaved);
    }

    public void testSuspendedGameCannotCreateOrder() throws Exception {
        Games games = new Games();
        games.available = new Game("1", "Game", 4800, "Adventure", "game.jpg", "", false);
        MemoryOrders orders = new MemoryOrders();
        WebFixture web = cart(orders);
        OrderServlet servlet = new OrderServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status);
        assertEquals(true, web.attributes.get("orderBlocked"));
        assertNull(orders.lastSaved);
    }

    public void testPriceChangeRequiresReviewBeforeSecondPost() throws Exception {
        Games games = new Games();
        games.available = new Game("1", "Game", 5900, "Adventure", "game.jpg");
        MemoryOrders orders = new MemoryOrders();
        WebFixture web = cart(orders);
        OrderServlet servlet = new OrderServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status);
        assertEquals(false, web.attributes.get("orderBlocked"));
        assertEquals(5900L, web.attributes.get("total"));
        assertNull(orders.lastSaved);
        web.parameters.put("checkoutToken", (String) web.attributes.get("checkoutToken"));
        servlet.doPost(web.request, web.response);
        assertEquals(5900, orders.lastSaved.getTotalPrice());
        assertNull(web.sessionValues.get("cart"));
    }

    public void testTransactionCatalogConflictIs409AndKeepsCart() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        orders.failure = new com.gamestore.repository.CatalogConflictException("Changed concurrently");
        WebFixture web = cart(orders);
        OrderServlet servlet = new OrderServlet(new Games());
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status);
        assertEquals(true, web.attributes.get("orderBlocked"));
        assertNotNull(web.sessionValues.get("cart"));
        assertNull(orders.lastSaved);
    }

    public void testRepeatedPostReturnsSameCompletedOrderAfterCartWasCleared() throws Exception {
        MemoryOrders orders = new MemoryOrders();
        WebFixture web = cart(orders);
        OrderServlet servlet = new OrderServlet(new Games()); servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        String first = web.redirect;
        servlet.doPost(web.request, web.response);
        assertEquals(first, web.redirect);
        assertEquals(1, orders.saves);
    }

    public void testCompletedReplayDoesNotClearANewCart() throws Exception {
        MemoryOrders orders = new MemoryOrders(); WebFixture web = cart(orders);
        OrderServlet servlet = new OrderServlet(new Games()); servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        String first = web.redirect;
        List<Game> next = new ArrayList<>(List.of(new Game("2", "New cart", 3200, "Puzzle", "next.png")));
        web.sessionValues.put("cart", next); CheckoutTokens.cartChanged(web.session);
        servlet.doPost(web.request, web.response);
        assertEquals(first, web.redirect); assertSame(next, web.sessionValues.get("cart")); assertEquals(1, orders.saves);
    }

    public void testUnknownMissingAndMalformedTokensCannotCreateOrders() throws Exception {
        for (String token : new String[] {null, "", "not-a-token", java.util.UUID.randomUUID().toString()}) {
            MemoryOrders orders = new MemoryOrders(); WebFixture web = cart(orders);
            web.parameters.put("checkoutToken", token);
            OrderServlet servlet = new OrderServlet(new Games()); servlet.init(web.config);
            servlet.doPost(web.request, web.response);
            assertEquals(CheckoutTokens.valid(token) ? 409 : 400, web.status);
            assertEquals(0, orders.saves); assertNotNull(web.sessionValues.get("cart"));
        }
    }

    public void testCartChangeInvalidatesOldConfirmationEvenIfSameGameIsReadded() throws Exception {
        MemoryOrders orders = new MemoryOrders(); WebFixture web = cart(orders);
        CheckoutTokens.cartChanged(web.session);
        OrderServlet servlet = new OrderServlet(new Games()); servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status); assertEquals(0, orders.saves); assertEquals(true, web.attributes.get("orderBlocked"));
    }

    public void testLostCommitAcknowledgementCanReplayWithoutAnotherOrder() throws Exception {
        MemoryOrders orders = new MemoryOrders() {
            boolean fail = true;
            @Override public synchronized String saveOnce(com.gamestore.model.Order order, String token) throws SQLException {
                String id = super.saveOnce(order, token);
                if (fail) { fail = false; throw new SQLException("Commit acknowledgement lost"); }
                return id;
            }
        };
        WebFixture web = cart(orders);
        OrderServlet servlet = new OrderServlet(new Games()); servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(503, web.status); assertNotNull(web.sessionValues.get("cart")); assertEquals(1, orders.saves);
        servlet.doPost(web.request, web.response);
        assertEquals("/gamestore/order/complete?id=" + orders.lastSaved.getOrderId(), web.redirect);
        assertEquals(1, orders.saves); assertNull(web.sessionValues.get("cart"));
    }

    public void testPriceReviewIssuesNewTokenAndOldTokenCannotConfirm() throws Exception {
        MemoryOrders orders = new MemoryOrders(); WebFixture web = cart(orders);
        String old = web.parameters.get("checkoutToken");
        Games games = new Games(); games.available = new Game("1", "Game", 5900, "Adventure", "game.jpg");
        OrderServlet servlet = new OrderServlet(games); servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        String updated = (String) web.attributes.get("checkoutToken");
        assertTrue(CheckoutTokens.valid(updated)); assertFalse(old.equals(updated));
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status); assertEquals(0, orders.saves);
        web.parameters.put("checkoutToken", updated);
        servlet.doPost(web.request, web.response);
        assertEquals(1, orders.saves); assertEquals(5900, orders.lastSaved.getTotalPrice());
    }
}
