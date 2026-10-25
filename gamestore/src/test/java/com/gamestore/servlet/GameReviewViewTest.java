package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.*;
import com.gamestore.model.*;
import com.gamestore.repository.*;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class GameReviewViewTest extends TestCase {
    private JdbcGameDetailsRepository details() {
        return new JdbcGameDetailsRepository() { @Override public GameDetails findForGame(String id) { return GameDetails.EMPTY; } };
    }
    public void testDetailsFailureKeepsProductAndReviewsButShowsSeparateError() throws Exception {
        WebFixture web = new WebFixture(); web.parameters.put("id", "1");
        var broken = new JdbcGameDetailsRepository() { @Override public GameDetails findForGame(String id) throws SQLException { throw new SQLException("private"); } };
        var reviews = new JdbcReviewRepository() {
            @Override public GameReviews findForGame(int id, Long user, int page) { return new GameReviews(new ReviewPage(List.of(), 0, 0, 1, 5), null, false); }
        };
        GameDetailServlet servlet = new GameDetailServlet(game(), new JdbcWishlistRepository(), reviews, broken); servlet.init(web.config);
        servlet.doGet(web.request, web.response); assertEquals(503, web.status); assertNotNull(web.attributes.get("game"));
        assertNotNull(web.attributes.get("reviews")); assertNotNull(web.attributes.get("gameDetailsError")); assertNull(web.attributes.get("gameDetails"));
    }
    public void testGuestCanReadReviewsAndPageRequestWithoutLogin() throws Exception {
        WebFixture web = new WebFixture(); web.parameters.put("id", "1"); web.parameters.put("reviewPage", "2");
        JdbcReviewRepository reviews = new JdbcReviewRepository() {
            @Override public GameReviews findForGame(int id, Long viewer, int page) {
                assertEquals(1, id); assertNull(viewer); assertEquals(2, page);
                return new GameReviews(new ReviewPage(List.of(), 0, 0, 1, 5), null, false);
            }
        };
        GameDetailServlet servlet = new GameDetailServlet(game(), new JdbcWishlistRepository(), reviews, details()); servlet.init(web.config);
        servlet.doGet(web.request, web.response); assertEquals(200, web.status); assertNotNull(web.attributes.get("reviews"));
    }
    public void testReviewFailureDoesNotPretendGameHasZeroReviews() throws Exception {
        WebFixture web = new WebFixture(); web.parameters.put("id", "1");
        JdbcReviewRepository reviews = new JdbcReviewRepository() {
            @Override public GameReviews findForGame(int id, Long viewer, int page) throws SQLException { throw new SQLException("private detail"); }
        };
        GameDetailServlet servlet = new GameDetailServlet(game(), new JdbcWishlistRepository(), reviews, details()); servlet.init(web.config);
        servlet.doGet(web.request, web.response); assertEquals(503, web.status); assertNotNull(web.attributes.get("game"));
        assertNull(web.attributes.get("reviews")); assertNotNull(web.attributes.get("reviewError")); assertEquals("/game-detail.jsp", web.forwarded);
    }
    private JdbcGameRepository game() {
        return new JdbcGameRepository() { @Override public Game findById(String id) { return new Game("1", "Game", 1000, "Adventure", "images/aetherbound.png"); } };
    }
}
