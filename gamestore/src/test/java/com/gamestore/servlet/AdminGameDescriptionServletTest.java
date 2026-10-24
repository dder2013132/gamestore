package com.gamestore.servlet;

import java.sql.SQLException;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.support.WebFixture;

import junit.framework.TestCase;

public class AdminGameDescriptionServletTest extends TestCase {
    private static class Games extends JdbcGameRepository {
        Game game = new Game("8", "Ember Express", 7880, "Adventure", "images/ember_express.png", "既存の説明");
        SQLException loadFailure;
        SQLException saveFailure;
        boolean updated = true;
        int saves;
        String description;
        @Override public Game findById(String id) throws SQLException {
            if (loadFailure != null) { throw loadFailure; }
            return "8".equals(id) ? game : null;
        }
        @Override public boolean updateDescription(String id, String text) throws SQLException {
            if (saveFailure != null) { throw saveFailure; }
            assertEquals("8", id);
            saves++;
            description = text;
            return updated;
        }
    }

    private WebFixture form() {
        WebFixture web = new WebFixture();
        web.parameters.put("id", "8");
        web.parameters.put("description", "  森を走る列車。\n<script>文字として保存</script>  ");
        return web;
    }

    private void call(WebFixture web, Games games, boolean post) throws Exception {
        AdminGameDescriptionServlet servlet = new AdminGameDescriptionServlet(games);
        servlet.init(web.config);
        if (post) { servlet.doPost(web.request, web.response); }
        else { servlet.doGet(web.request, web.response); }
    }

    public void testEditorLoadsExistingDescription() throws Exception {
        Games games = new Games();
        WebFixture web = form();
        call(web, games, false);
        assertSame(games.game, web.attributes.get("game"));
        assertEquals("既存の説明", web.attributes.get("formDescription"));
        assertEquals(Game.DESCRIPTION_MAX_LENGTH, web.attributes.get("descriptionLimit"));
        assertEquals("/WEB-INF/views/admin-game-description.jsp", web.forwarded);
    }

    public void testSavePreservesUnicodeNewlinesAndLiteralHtml() throws Exception {
        Games games = new Games();
        WebFixture web = form();
        call(web, games, true);
        assertEquals(1, games.saves);
        assertEquals("森を走る列車。\n<script>文字として保存</script>", games.description);
        assertEquals("/gamestore/admin/games/description?id=8&saved=1", web.redirect);
    }

    public void testBlankClearsDescriptionAndLimitIsAccepted() throws Exception {
        for (String text : new String[] {"   ", "あ".repeat(Game.DESCRIPTION_MAX_LENGTH)}) {
            Games games = new Games();
            WebFixture web = form();
            web.parameters.put("description", text);
            call(web, games, true);
            assertEquals(1, games.saves);
            assertEquals(text.trim(), games.description);
        }
    }

    public void testTooLongDescriptionIsRejectedAndPreservedForCorrection() throws Exception {
        Games games = new Games();
        WebFixture web = form();
        String text = "あ".repeat(Game.DESCRIPTION_MAX_LENGTH + 1);
        web.parameters.put("description", text);
        call(web, games, true);
        assertEquals(400, web.status);
        assertEquals(0, games.saves);
        assertEquals(text, web.attributes.get("formDescription"));
        assertNotNull(web.attributes.get("error"));
        assertEquals("/WEB-INF/views/admin-game-description.jsp", web.forwarded);
    }

    public void testMissingDescriptionDoesNotClearStoredValue() throws Exception {
        Games games = new Games();
        WebFixture web = form();
        web.parameters.remove("description");
        call(web, games, true);
        assertEquals(400, web.status);
        assertEquals(0, games.saves);
    }

    public void testMissingAndInvalidGameIdsReturn404WithoutSaving() throws Exception {
        for (String id : new String[] {null, "0", "' OR 1=1", "99999"}) {
            for (boolean post : new boolean[] {false, true}) {
                Games games = new Games();
                WebFixture web = form();
                web.parameters.put("id", id);
                call(web, games, post);
                assertEquals(404, web.status);
                assertEquals(0, games.saves);
            }
        }
    }

    public void testGameRemovedBeforeSaveReturns404() throws Exception {
        Games games = new Games();
        games.updated = false;
        WebFixture web = form();
        call(web, games, true);
        assertEquals(404, web.status);
        assertNull(web.redirect);
    }

    public void testSaveFailurePreservesInputAndHidesDatabaseDetails() throws Exception {
        Games games = new Games();
        games.saveFailure = new SQLException("private SQL details");
        WebFixture web = form();
        call(web, games, true);
        assertEquals(503, web.status);
        assertEquals(web.parameters.get("description").trim(), web.attributes.get("formDescription"));
        assertFalse(web.attributes.get("error").toString().contains("private SQL"));
        assertNull(web.redirect);
    }

    public void testLoadFailureDoesNotRenderAnEmptyEditor() throws Exception {
        Games games = new Games();
        games.loadFailure = new SQLException("unavailable");
        WebFixture web = form();
        call(web, games, false);
        assertEquals(503, web.status);
        assertNull(web.forwarded);
    }
}
