package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.support.WebFixture;

import junit.framework.TestCase;

public class AdminGameServletTest extends TestCase {
    private static class Games extends JdbcGameRepository {
        int created;
        Game last;
        SQLException failure;
        Game existing = new Game("3", "Existing", 4800, "Adventure", "images/aetherbound.png", "Old description", false);
        int updated;
        @Override public Game findById(String id) { return "3".equals(id) ? existing : null; }
        @Override public boolean update(String id, String title, int price, String genre, String image, String description) throws SQLException {
            if (failure != null) { throw failure; }
            updated++;
            last = new Game(id, title, price, genre, image, description, existing.isActive());
            return true;
        }
        @Override public List<Game> findAll() { return List.of(); }
        @Override public Game create(String title, int price, String genre, String image, String description) throws SQLException {
            if (failure != null) { throw failure; }
            created++;
            last = new Game("3", title, price, genre, image, description);
            return last;
        }
    }

    private WebFixture form() {
        WebFixture web = new WebFixture();
        web.parameters.put("title", " New Game ");
        web.parameters.put("price", "4800");
        web.parameters.put("genre", "Adventure");
        web.parameters.put("imagePath", "images/aetherbound.png");
        web.parameters.put("description", "  月明かりの町へ。\n友達と冒険しよう！  ");
        return web;
    }

    public void testValidGameIsSavedAndRedirectsToGet() throws Exception {
        Games games = new Games();
        WebFixture web = form();
        AdminGameServlet servlet = new AdminGameServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(1, games.created);
        assertEquals("New Game", games.last.getTitle());
        assertEquals(4800, games.last.getPrice());
        assertEquals("月明かりの町へ。\n友達と冒険しよう！", games.last.getDescription());
        assertEquals("/gamestore/admin/games?created=1", web.redirect);
    }

    public void testInvalidFieldsAndUnlistedImageCannotBeSaved() throws Exception {
        for (String[] field : new String[][] { {"title", " "}, {"title", "x".repeat(201)},
                {"price", "-1"}, {"price", "4.5"}, {"price", "2147483648"},
                {"genre", ""}, {"genre", "x".repeat(51)},
                {"description", "あ".repeat(Game.DESCRIPTION_MAX_LENGTH + 1)},
                {"imagePath", "../WEB-INF/web.xml"}, {"imagePath", "images/missing.png"},
                {"imagePath", "https://example.com/image.png"} }) {
            Games games = new Games();
            WebFixture web = form();
            web.parameters.put(field[0], field[1]);
            AdminGameServlet servlet = new AdminGameServlet(games);
            servlet.init(web.config);
            servlet.doPost(web.request, web.response);
            assertEquals(400, web.status);
            assertEquals(0, games.created);
            assertNotNull(web.attributes.get("error"));
            assertEquals(web.parameters.get("description").trim(), web.attributes.get("formDescription"));
            assertEquals("/WEB-INF/views/admin-games.jsp", web.forwarded);
        }
    }

    public void testDatabaseFailurePreservesFormValues() throws Exception {
        Games games = new Games();
        games.failure = new SQLException("private SQL detail");
        WebFixture web = form();
        AdminGameServlet servlet = new AdminGameServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(503, web.status);
        assertEquals("New Game", web.attributes.get("formTitle"));
        assertEquals("月明かりの町へ。\n友達と冒険しよう！", web.attributes.get("formDescription"));
        assertNotNull(web.attributes.get("error"));
        assertFalse(web.attributes.get("error").toString().contains("private SQL detail"));
    }

    public void testOnlyLocalImageResourcesAreOffered() throws Exception {
        Games games = new Games();
        WebFixture web = form();
        web.imagePaths = java.util.Set.of("/images/aetherbound.png", "/images/nested/", "/images/readme.txt");
        AdminGameServlet servlet = new AdminGameServlet(games);
        servlet.init(web.config);
        servlet.doGet(web.request, web.response);
        assertEquals(List.of("images/aetherbound.png"), web.attributes.get("imageOptions"));
    }

    public void testDescriptionIsOptionalAndAcceptsLimit() throws Exception {
        for (String description : new String[] {null, " ", "あ".repeat(Game.DESCRIPTION_MAX_LENGTH)}) {
            Games games = new Games();
            WebFixture web = form();
            web.parameters.put("description", description);
            AdminGameServlet servlet = new AdminGameServlet(games);
            servlet.init(web.config);
            servlet.doPost(web.request, web.response);
            assertEquals(1, games.created);
            assertEquals(description == null ? "" : description.trim(), games.last.getDescription());
        }
    }

    public void testEditLoadsExistingFieldsAndSuspendedState() throws Exception {
        WebFixture web = form();
        web.path = "/admin/games/edit";
        web.parameters.put("id", "3");
        Games games = new Games();
        AdminGameServlet servlet = new AdminGameServlet(games);
        servlet.init(web.config);
        servlet.doGet(web.request, web.response);
        assertSame(games.existing, web.attributes.get("editingGame"));
        assertEquals("Existing", web.attributes.get("formTitle"));
        assertEquals("Old description", web.attributes.get("formDescription"));
        assertEquals("/WEB-INF/views/admin-game-edit.jsp", web.forwarded);
    }

    public void testEditUpdatesExistingIdWithoutCreatingOrResumingIt() throws Exception {
        WebFixture web = form();
        web.path = "/admin/games/edit";
        web.parameters.put("id", "3");
        web.parameters.put("active", "true");
        Games games = new Games();
        AdminGameServlet servlet = new AdminGameServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(0, games.created);
        assertEquals(1, games.updated);
        assertEquals("3", games.last.getId());
        assertEquals("New Game", games.last.getTitle());
        assertFalse(games.last.isActive());
        assertEquals("/gamestore/admin/games?updated=1", web.redirect);
    }

    public void testInvalidEditPreservesAllInputsAndDoesNotUpdate() throws Exception {
        WebFixture web = form();
        web.path = "/admin/games/edit";
        web.parameters.put("id", "3");
        web.parameters.put("price", "-1");
        Games games = new Games();
        AdminGameServlet servlet = new AdminGameServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(400, web.status);
        assertEquals(0, games.updated);
        assertEquals("New Game", web.attributes.get("formTitle"));
        assertEquals("-1", web.attributes.get("formPrice"));
        assertEquals(web.parameters.get("description").trim(), web.attributes.get("formDescription"));
        assertEquals("/WEB-INF/views/admin-game-edit.jsp", web.forwarded);
    }

    public void testEditFailureRetainsInputsAndMissingIdReturns404() throws Exception {
        Games games = new Games();
        games.failure = new SQLException("private details");
        WebFixture web = form();
        web.path = "/admin/games/edit";
        web.parameters.put("id", "3");
        AdminGameServlet servlet = new AdminGameServlet(games);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(503, web.status);
        assertEquals("New Game", web.attributes.get("formTitle"));
        assertFalse(web.attributes.get("error").toString().contains("private details"));
        web = form();
        web.path = "/admin/games/edit";
        web.parameters.put("id", "missing");
        servlet.doGet(web.request, web.response);
        assertEquals(404, web.status);
        assertNull(web.forwarded);
    }
}
