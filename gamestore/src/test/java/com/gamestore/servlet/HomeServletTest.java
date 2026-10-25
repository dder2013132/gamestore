package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import com.gamestore.model.*;
import com.gamestore.repository.*;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class HomeServletTest extends TestCase {
    private static class Home extends JdbcHomeRepository {
        Game game = new Game("4", "Newest game", 2800, "Adventure", "images/aetherbound.png");
        HomePage page = new HomePage(List.of(new HomeGame(game, 0, 0, 0)), List.of(), List.of(), 12);
        Exception failure;
        @Override public HomePage findHome() throws SQLException, IOException, ClassNotFoundException {
            if (failure instanceof SQLException e) throw e;
            if (failure instanceof IOException e) throw e;
            if (failure instanceof ClassNotFoundException e) throw e;
            return page;
        }
    }
    private void render(WebFixture web, Home home, JdbcWishlistRepository wishes) throws Exception {
        HomeServlet servlet = new HomeServlet(home, wishes); servlet.init(web.config); servlet.doGet(web.request, web.response);
    }
    public void testHomeForwardsActualRankedDataWithoutFixedCoverPreference() throws Exception {
        WebFixture web = new WebFixture(); Home home = new Home();
        render(web, home, new JdbcWishlistRepository());
        assertSame(home.page, web.attributes.get("home")); assertSame(home.game, web.attributes.get("spotlight"));
        assertEquals(12, ((HomePage) web.attributes.get("home")).catalogCount()); assertNull(web.attributes.get("homeError"));
        assertEquals("/index.jsp", web.forwarded);
    }
    public void testLegacySearchRedirectsWithEncodedConditionsBeforeQueryingDatabase() throws Exception {
        WebFixture web = new WebFixture(); web.parameters.put("q", "月 & Moon"); web.parameters.put("genre", "Action/Adventure"); web.parameters.put("sort", "price_asc");
        Home home = new Home(); home.failure = new SQLException("Must not query");
        render(web, home, new JdbcWishlistRepository());
        assertEquals("/gamestore/browse?q=%E6%9C%88+%26+Moon&genre=Action%2FAdventure&sort=price_asc", web.redirect);
        assertNull(web.forwarded); assertNull(web.attributes.get("homeError"));
    }
    public void testEmptyCatalogAndNoReviewsAreValidEmptySections() throws Exception {
        WebFixture web = new WebFixture(); Home home = new Home(); home.page = new HomePage(List.of(), List.of(), List.of(), 0);
        render(web, home, new JdbcWishlistRepository()); assertEquals(200, web.status); assertNull(web.attributes.get("spotlight"));
        assertSame(home.page, web.attributes.get("home")); assertNull(web.attributes.get("homeError"));
    }
    public void testDatabaseFailuresShow503InsteadOfFabricatingZeroRanks() throws Exception {
        for (Exception failure : List.of(new SQLException("private database details"), new IOException("private config"), new ClassNotFoundException("private driver"))) {
            WebFixture web = new WebFixture(); Home home = new Home(); home.failure = failure;
            render(web, home, new JdbcWishlistRepository()); assertEquals(503, web.status); assertNull(web.attributes.get("home"));
            assertNull(web.attributes.get("spotlight")); assertNotNull(web.attributes.get("homeError"));
            assertFalse(web.attributes.get("homeError").toString().contains("private")); assertEquals("/index.jsp", web.forwarded);
        }
    }
    public void testMemberWishlistAndOwnershipStateArePreservedForAllSections() throws Exception {
        WebFixture web = new WebFixture(); web.path = "/home"; web.sessionExists = true; web.sessionValues.put("loginUser", new User(7L, "alice"));
        web.parameters.put("userId", "8"); Object owned = Map.of("4", true); web.attributes.put("ownedIds", owned);
        JdbcWishlistRepository wishes = new JdbcWishlistRepository() {
            @Override public Set<String> findIdsForUser(long id) { assertEquals(7L, id); return Set.of("4"); }
        };
        render(web, new Home(), wishes); assertSame(owned, web.attributes.get("ownedIds")); assertEquals(Map.of("4", true), web.attributes.get("wishlistIds"));
        assertEquals("/home", web.attributes.get("wishlistReturnTo"));
    }
    public void testWishlistFailureDoesNotRenderPartialMemberCards() throws Exception {
        WebFixture web = new WebFixture(); web.sessionExists = true; web.sessionValues.put("loginUser", new User(7L, "alice"));
        JdbcWishlistRepository wishes = new JdbcWishlistRepository() {
            @Override public Set<String> findIdsForUser(long id) throws SQLException { throw new SQLException("private"); }
        };
        render(web, new Home(), wishes); assertEquals(503, web.status); assertNotNull(web.attributes.get("homeError")); assertNull(web.attributes.get("home"));
    }
    public void testHomePostCannotPerformAnAction() throws Exception {
        WebFixture web = new WebFixture(); web.method = "POST";
        HomeServlet servlet = new HomeServlet(new Home(), new JdbcWishlistRepository()); servlet.init(web.config); servlet.service(web.request, web.response);
        assertEquals(405, web.status); assertNull(web.attributes.get("home"));
    }
}
