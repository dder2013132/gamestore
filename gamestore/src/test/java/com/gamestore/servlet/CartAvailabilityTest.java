package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.support.WebFixture;
import jakarta.servlet.ServletException;
import junit.framework.TestCase;

public class CartAvailabilityTest extends TestCase {
    private static class Games extends JdbcGameRepository {
        Game current = new Game("1", "Updated title", 5900, "Adventure", "updated.png");
        SQLException failure;
        @Override public Game findById(String id) throws SQLException {
            if (failure != null) { throw failure; }
            return current;
        }
    }
    private WebFixture cart() {
        WebFixture web = new WebFixture();
        web.sessionExists = true;
        web.sessionValues.put("loginUser", new com.gamestore.model.User(7L, "alice"));
        web.sessionValues.put("cart", new ArrayList<>(List.of(new Game("1", "Old title", 4800, "Adventure", "old.png"))));
        return web;
    }
    public void testCartRefreshesCurrentPriceTitleAndImage() throws Exception {
        WebFixture web = cart();
        CartServlet servlet = new CartServlet(new Games());
        servlet.doGet(web.request, web.response);
        Game game = (Game)((List<?>)web.attributes.get("cart")).get(0);
        assertEquals("Updated title", game.getTitle());
        assertEquals("updated.png", game.getImagePath());
        assertEquals(5900L, web.attributes.get("totalPrice"));
        assertEquals(false, web.attributes.get("cartBlocked"));
        assertEquals(web.attributes.get("cart"), web.sessionValues.get("cart"));
    }
    public void testSuspendedAndMissingCartItemsStayVisibleAndBlockCheckout() throws Exception {
        for (Game value : new Game[] {null, new Game("1", "Paused", 4800, "Adventure", "old.png", "", false)}) {
            Games games = new Games();
            games.current = value;
            WebFixture web = cart();
            CartServlet servlet = new CartServlet(games);
            servlet.doGet(web.request, web.response);
            assertEquals(true, web.attributes.get("cartBlocked"));
            assertEquals(1, ((List<?>)web.attributes.get("cart")).size());
            assertFalse(((Game)((List<?>)web.attributes.get("cart")).get(0)).isActive());
        }
    }
    public void testSuspendedProductCannotBeAddedEvenByDirectPost() throws Exception {
        Games games = new Games();
        games.current = new Game("1", "Paused", 4800, "Adventure", "old.png", "", false);
        WebFixture web = new WebFixture();
        web.parameters.put("gameId", "1");
        CartServlet servlet = new CartServlet(games);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status);
        assertNull(web.sessionValues.get("cart"));
        assertEquals("/game-detail.jsp", web.forwarded);
    }
    public void testActiveProductCanStillBeAdded() throws Exception {
        WebFixture web = new WebFixture();
        web.parameters.put("gameId", "1");
        CartServlet servlet = new CartServlet(new Games());
        servlet.doPost(web.request, web.response);
        assertEquals(1, ((List<?>)web.sessionValues.get("cart")).size());
        assertEquals("/gamestore/cart", web.redirect);
    }
    public void testCheckoutRefreshesPriceAndRefusesSuspendedItem() throws Exception {
        Games games = new Games();
        WebFixture web = cart();
        CheckoutServlet servlet = new CheckoutServlet(games);
        servlet.doGet(web.request, web.response);
        assertEquals(5900L, web.attributes.get("total"));
        assertEquals("/checkout.jsp", web.forwarded);
        games.current = new Game("1", "Paused", 5900, "Adventure", "old.png", "", false);
        web = cart();
        servlet.doGet(web.request, web.response);
        assertEquals("/gamestore/cart", web.redirect);
        assertNotNull(web.sessionValues.get("cartMessage"));
        assertNull(web.forwarded);
    }
    public void testDatabaseFailureDoesNotShowCheckoutWithStalePrice() throws Exception {
        Games games = new Games();
        games.failure = new SQLException("unavailable");
        WebFixture web = cart();
        Object original = web.sessionValues.get("cart");
        try {
            new CheckoutServlet(games).doGet(web.request, web.response);
            fail("Expected failure");
        } catch (ServletException expected) { assertSame(games.failure, expected.getCause()); }
        assertNull(web.forwarded);
        assertSame(original, web.sessionValues.get("cart"));
    }
}
