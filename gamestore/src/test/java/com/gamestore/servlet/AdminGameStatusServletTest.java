package com.gamestore.servlet;

import java.sql.SQLException;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class AdminGameStatusServletTest extends TestCase {
    private static class Games extends JdbcGameRepository {
        int updates;
        boolean active;
        SQLException failure;
        @Override public boolean setActive(String id, boolean active) throws SQLException {
            if (failure != null) { throw failure; }
            if (!"3".equals(id)) { return false; }
            updates++;
            this.active = active;
            return true;
        }
    }
    private void post(WebFixture web, Games games) throws Exception {
        AdminGameStatusServlet servlet = new AdminGameStatusServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
    }
    public void testExplicitPauseAndResumeAreRepeatable() throws Exception {
        for (String active : new String[] {"false", "false", "true", "true"}) {
            Games games = new Games();
            WebFixture web = new WebFixture();
            web.parameters.put("id", "3");
            web.parameters.put("active", active);
            post(web, games);
            assertEquals(1, games.updates);
            assertEquals(Boolean.parseBoolean(active), games.active);
            assertEquals("/gamestore/admin/games?statusUpdated=1", web.redirect);
        }
    }
    public void testMissingOrInvalidStatusIsRejected() throws Exception {
        for (String value : new String[] {null, "", "yes", "1"}) {
            Games games = new Games();
            WebFixture web = new WebFixture();
            web.parameters.put("active", value);
            post(web, games);
            assertEquals(400, web.status);
            assertEquals(0, games.updates);
        }
    }
    public void testMissingGameDoesNotReportSuccess() throws Exception {
        WebFixture web = new WebFixture();
        web.parameters.put("active", "false");
        post(web, new Games());
        assertEquals(404, web.status);
        assertNull(web.redirect);
    }
    public void testDatabaseFailureReturns503() throws Exception {
        Games games = new Games();
        games.failure = new SQLException("unavailable");
        WebFixture web = new WebFixture();
        web.parameters.put("id", "3");
        web.parameters.put("active", "false");
        post(web, games);
        assertEquals(503, web.status);
        assertNull(web.redirect);
    }
}
