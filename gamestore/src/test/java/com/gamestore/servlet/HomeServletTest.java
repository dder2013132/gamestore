package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.support.WebFixture;
import jakarta.servlet.ServletException;
import junit.framework.TestCase;

public class HomeServletTest extends TestCase {
    private static class Games extends JdbcGameRepository {
        List<Game> catalog = List.of(new Game("1", "Aetherbound", 4800, "Adventure", "images/aetherbound.png"),
                new Game("2", "Moonlit Harvest", 3200, "Simulation", "images/moonlit_harvest.png"),
                new Game("3", "Ember Express", 2800, "Adventure", "images/ember_express.png"));
        SQLException failure;
        @Override public List<Game> findAll() throws SQLException {
            if (failure != null) { throw failure; }
            return catalog;
        }
    }

    private void render(WebFixture web, Games games) throws Exception {
        HomeServlet servlet = new HomeServlet(games);
        servlet.init(web.config);
        servlet.doGet(web.request, web.response);
    }

    public void testSectionsUseRealCatalogAndPreferCozyCover() throws Exception {
        WebFixture web = new WebFixture();
        render(web, new Games());
        assertEquals("Ember Express", ((Game) web.attributes.get("spotlight")).getTitle());
        assertEquals(3, web.attributes.get("catalogCount"));
        assertEquals(3, ((List<?>) web.attributes.get("picks")).size());
        assertNull(web.attributes.get("games"));
        assertNull(web.attributes.get("genres"));
    }

    public void testLegacySearchRedirectsWithEncodedConditionsBeforeQueryingDatabase() throws Exception {
        WebFixture web = new WebFixture();
        web.parameters.put("q", "月 & Moon");
        web.parameters.put("genre", "Action/Adventure");
        web.parameters.put("sort", "price_asc");
        Games games = new Games();
        games.failure = new SQLException("Must not query before redirect");
        render(web, games);
        assertEquals("/gamestore/browse?q=%E6%9C%88+%26+Moon&genre=Action%2FAdventure&sort=price_asc", web.redirect);
        assertNull(web.forwarded);
    }

    public void testHomeLimitsPicksWhileCountUsesWholeCatalog() throws Exception {
        Games games = new Games();
        games.catalog = List.of(new Game("1", "One", 100, "Adventure", "images/a.png"),
                new Game("2", "Two", 200, "Adventure", "images/b.png"),
                new Game("3", "Three", 300, "Adventure", "images/c.png"),
                new Game("4", "Four", 400, "Adventure", "images/d.png"));
        WebFixture web = new WebFixture();
        render(web, games);
        assertEquals(4, web.attributes.get("catalogCount"));
        assertEquals(3, ((List<?>) web.attributes.get("picks")).size());
    }

    public void testEmptyCatalogCanRenderWithoutSpotlight() throws Exception {
        WebFixture web = new WebFixture();
        Games games = new Games();
        games.catalog = List.of();
        render(web, games);
        assertNull(web.attributes.get("spotlight"));
        assertTrue(((List<?>) web.attributes.get("picks")).isEmpty());
        assertEquals("/index.jsp", web.forwarded);
    }

    public void testDatabaseFailureIsNotShownAsAnEmptyCatalog() throws Exception {
        Games games = new Games();
        games.failure = new SQLException("unavailable");
        try {
            render(new WebFixture(), games);
            fail("Expected database failure");
        } catch (ServletException expected) {
            assertSame(games.failure, expected.getCause());
        }
    }

    public void testSuspendedGameIsExcludedFromSpotlightPicksAndCount() throws Exception {
        Games games = new Games();
        games.catalog = List.of(new Game("1", "Paused", 4800, "Adventure", "images/ember_express.png", "", false),
                new Game("2", "Available", 3200, "Simulation", "images/moonlit_harvest.png"));
        WebFixture web = new WebFixture();
        render(web, games);
        assertEquals(1, web.attributes.get("catalogCount"));
        assertEquals("2", ((Game)web.attributes.get("spotlight")).getId());
        assertEquals(1, ((List<?>)web.attributes.get("picks")).size());
    }
}
