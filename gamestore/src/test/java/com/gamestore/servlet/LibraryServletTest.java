package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.List;
import com.gamestore.model.*;
import com.gamestore.repository.JdbcLibraryRepository;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class LibraryServletTest extends TestCase {
    private static class Library extends JdbcLibraryRepository {
        long user; int page, calls; String query; SQLException failure;
        @Override public LibraryPage findForUser(long user, String query, int page) throws SQLException {
            calls++; if (failure != null) { throw failure; } this.user = user; this.query = query; this.page = page;
            return new LibraryPage(List.of(), 0, 0, 1, 9);
        }
    }
    private WebFixture web; private Library library; private LibraryServlet servlet;
    @Override protected void setUp() throws Exception {
        web = new WebFixture(); web.path = "/library"; web.sessionExists = true; web.sessionValues.put("loginUser", new User(7L, "alice"));
        library = new Library(); servlet = new LibraryServlet(library); servlet.init(web.config);
    }
    public void testSessionMemberOwnsQueryAndUrlUserIdIsIgnored() throws Exception {
        web.parameters.put("userId", "999"); web.parameters.put("q", "  Moon  "); web.parameters.put("page", "2");
        servlet.doGet(web.request, web.response); assertEquals(7L, library.user); assertEquals("Moon", library.query); assertEquals(2, library.page);
        assertNotNull(web.attributes.get("library")); assertEquals("/WEB-INF/views/library.jsp", web.forwarded);
    }
    public void testMalformedPageDefaultsToOneAndExcessiveQueryDoesNotReadDatabase() throws Exception {
        web.parameters.put("page", "2147483648"); servlet.doGet(web.request, web.response); assertEquals(1, library.page);
        setUp(); web.parameters.put("q", "x".repeat(101)); servlet.doGet(web.request, web.response);
        assertEquals(400, web.status); assertEquals(0, library.calls); assertNotNull(web.attributes.get("libraryError"));
    }
    public void testDatabaseErrorDoesNotPresentAnEmptyLibrary() throws Exception {
        library.failure = new SQLException("private database detail"); servlet.doGet(web.request, web.response);
        assertEquals(503, web.status); assertNull(web.attributes.get("library")); assertNotNull(web.attributes.get("libraryError"));
        assertFalse(web.attributes.get("libraryError").toString().contains("private"));
    }
    public void testGuestsAndPostCannotReadOrMutateLibrary() throws Exception {
        web.sessionValues.clear(); servlet.doGet(web.request, web.response); assertEquals(401, web.status); assertEquals(0, library.calls);
        setUp(); web.method = "POST"; servlet.service(web.request, web.response); assertEquals(405, web.status); assertEquals(0, library.calls);
    }
}
