package com.gamestore.filter;

import com.gamestore.model.User;
import com.gamestore.support.WebFixture;
import com.gamestore.repository.JdbcUserRepository;
import java.sql.SQLException;

import junit.framework.TestCase;

public class AuthFilterTest extends TestCase {
    private static class Users extends JdbcUserRepository {
        User current;
        SQLException failure;
        @Override
        public User findById(long id) throws SQLException {
            if (failure != null) { throw failure; }
            return current;
        }
    }

    public void testGuestAdministratorPageRedirectsToLogin() throws Exception {
        WebFixture web = new WebFixture();
        web.path = "/admin/games";
        filter(web);
        assertEquals("/gamestore/login", web.redirect);
        assertEquals("/admin/games", web.sessionValues.get("loginDestination"));
        assertFalse(web.chained);
    }

    public void testOrdinaryMemberAndRevokedAdministratorAreDenied() throws Exception {
        for (String sessionRole : new String[] { "USER", "ADMIN" }) {
            WebFixture web = new WebFixture();
            web.path = "/admin/games";
            web.sessionExists = true;
            web.sessionValues.put("loginUser", new User(1L, "alice", sessionRole));
            Users users = new Users();
            users.current = new User(1L, "alice", "USER");
            new AuthFilter(users).doFilter(web.request, web.response, (req, res) -> web.chained = true);
            assertEquals(403, web.status);
            assertFalse(web.chained);
        }
    }

    public void testAdministratorCanViewAndPostWithCsrf() throws Exception {
        for (String method : new String[] { "GET", "POST" }) {
            WebFixture web = new WebFixture();
            web.path = "/admin/games";
            web.method = method;
            web.sessionExists = true;
            web.sessionValues.put("loginUser", new User(1L, "alice", "USER"));
            web.sessionValues.put("csrfToken", "token");
            web.parameters.put("csrfToken", "token");
            Users users = new Users();
            users.current = new User(1L, "alice", "ADMIN");
            new AuthFilter(users).doFilter(web.request, web.response, (req, res) -> web.chained = true);
            assertTrue(web.chained);
            assertTrue(((User) web.sessionValues.get("loginUser")).isAdmin());
        }
    }

    public void testAdministratorPostWithoutCsrfIsDenied() throws Exception {
        WebFixture web = new WebFixture();
        web.path = "/admin/games";
        web.method = "POST";
        web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(1L, "alice", "ADMIN"));
        Users users = new Users();
        users.current = new User(1L, "alice", "ADMIN");
        new AuthFilter(users).doFilter(web.request, web.response, (req, res) -> web.chained = true);
        assertEquals(403, web.status);
        assertFalse(web.chained);
    }

    public void testDatabaseFailureDoesNotGrantAdministratorAccess() throws Exception {
        WebFixture web = new WebFixture();
        web.path = "/admin/games";
        web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(1L, "alice", "ADMIN"));
        Users users = new Users();
        users.failure = new SQLException("unavailable");
        new AuthFilter(users).doFilter(web.request, web.response, (req, res) -> web.chained = true);
        assertEquals(503, web.status);
        assertFalse(web.chained);
    }
    private void filter(WebFixture web) throws Exception {
        new AuthFilter().doFilter(web.request, web.response, (request, response) -> web.chained = true);
    }

    public void testDescriptionEditorRequiresCurrentAdministratorAndCsrf() throws Exception {
        WebFixture guest = new WebFixture();
        guest.path = "/admin/games/description";
        filter(guest);
        assertEquals("/gamestore/login", guest.redirect);
        assertFalse(guest.chained);
        for (String role : new String[] {"USER", "ADMIN"}) {
            for (String token : new String[] {"wrong", "expected"}) {
                WebFixture web = new WebFixture();
                web.path = "/admin/games/description";
                web.method = "POST";
                web.sessionExists = true;
                web.sessionValues.put("loginUser", new User(1L, "alice", "ADMIN"));
                web.sessionValues.put("csrfToken", "expected");
                web.parameters.put("csrfToken", token);
                Users users = new Users();
                users.current = new User(1L, "alice", role);
                new AuthFilter(users).doFilter(web.request, web.response, (req, res) -> web.chained = true);
                boolean allowed = "ADMIN".equals(role) && "expected".equals(token);
                assertEquals(allowed, web.chained);
                if (!allowed) { assertEquals(403, web.status); }
            }
        }
    }

    public void testGameEditAndSaleStatusAreAdminOnlyAndRequireCsrf() throws Exception {
        for (String path : new String[] {"/admin/games/edit", "/admin/games/status"}) {
            WebFixture guest = new WebFixture();
            guest.path = path;
            filter(guest);
            assertEquals("/gamestore/login", guest.redirect);
            for (String role : new String[] {"USER", "ADMIN"}) {
                for (String token : new String[] {"wrong", "expected"}) {
                    WebFixture web = new WebFixture();
                    web.path = path;
                    web.method = "POST";
                    web.sessionExists = true;
                    web.sessionValues.put("loginUser", new User(1L, "alice", "ADMIN"));
                    web.sessionValues.put("csrfToken", "expected");
                    web.parameters.put("csrfToken", token);
                    Users users = new Users();
                    users.current = new User(1L, "alice", role);
                    new AuthFilter(users).doFilter(web.request, web.response, (req, res) -> web.chained = true);
                    boolean allowed = "ADMIN".equals(role) && "expected".equals(token);
                    assertEquals(allowed, web.chained);
                    if (!allowed) { assertEquals(403, web.status); }
                }
            }
        }
    }

    public void testGuestCanBrowseAndReceivesFormToken() throws Exception {
        WebFixture web = new WebFixture();
        filter(web);
        assertTrue(web.chained);
        assertNotNull(web.sessionValues.get("csrfToken"));
    }

    public void testAllPrivateRoutesRequireLogin() throws Exception {
        for (String path : new String[] { "/wishlist", "/wishlist/add", "/wishlist/remove", "/cart", "/cart/remove", "/cart.jsp", "/checkout", "/order", "/orders", "/order/complete",
                "/checkout.jsp", "/orders.jsp", "/order-complete.jsp" }) {
            WebFixture web = new WebFixture();
            web.path = path;
            filter(web);
            assertEquals(path, "/gamestore/login", web.redirect);
            assertFalse(web.chained);
        }
    }

    public void testGuestOrderPostReturnsToCheckoutAfterLogin() throws Exception {
        WebFixture web = new WebFixture();
        web.path = "/order";
        web.method = "POST";
        filter(web);
        assertEquals("/checkout", web.sessionValues.get("loginDestination"));
        assertFalse(web.chained);
    }

    public void testGuestCartPathsRedirectAndCannotMutateAnExistingCart() throws Exception {
        for (String path : new String[] { "/cart", "/cart.jsp", "/cart/remove" }) {
            for (String method : new String[] { "GET", "POST" }) {
                WebFixture web = new WebFixture();
                web.path = path;
                web.method = method;
                web.sessionExists = true;
                Object cart = java.util.List.of("existing-game");
                web.sessionValues.put("cart", cart);
                web.sessionValues.put("csrfToken", "token");
                web.parameters.put("csrfToken", "token");
                filter(web);
                assertEquals("/gamestore/login", web.redirect);
                assertFalse(web.chained);
                assertSame(cart, web.sessionValues.get("cart"));
                assertEquals("/cart", web.sessionValues.get("loginDestination"));
            }
        }
    }

    public void testGuestAddReturnsToProductWithoutAddingIt() throws Exception {
        WebFixture web = new WebFixture();
        web.path = "/cart";
        web.method = "POST";
        web.parameters.put("gameId", "8");
        filter(web);
        assertEquals("/gamestore/login", web.redirect);
        assertEquals("/games?id=8", web.sessionValues.get("loginDestination"));
        assertNull(web.sessionValues.get("cart"));
        assertFalse(web.chained);
    }

    public void testMemberCartGetAndPostsKeepCsrfProtection() throws Exception {
        for (String path : new String[] { "/cart", "/cart/remove" }) {
            for (String token : new String[] { "expected", "wrong" }) {
                WebFixture web = new WebFixture();
                web.path = path;
                web.method = "POST";
                web.sessionExists = true;
                web.sessionValues.put("loginUser", new User(1L, "alice"));
                web.sessionValues.put("csrfToken", "expected");
                web.parameters.put("csrfToken", token);
                filter(web);
                assertEquals("expected".equals(token), web.chained);
                if ("wrong".equals(token)) { assertEquals(403, web.status); }
            }
        }
        WebFixture web = new WebFixture();
        web.path = "/cart";
        web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(1L, "alice"));
        filter(web);
        assertTrue(web.chained);
    }

    public void testMissingOrIncorrectCsrfBlocksPostEvenWithLogin() throws Exception {
        for (String token : new String[] { null, "wrong" }) {
            WebFixture web = new WebFixture();
            web.sessionExists = true;
            web.path = "/order";
            web.method = "POST";
            web.sessionValues.put("loginUser", new User(1L, "alice"));
            web.sessionValues.put("csrfToken", "expected");
            web.parameters.put("csrfToken", token);
            filter(web);
            assertEquals(403, web.status);
            assertFalse(web.chained);
        }
    }

    public void testValidCsrfAllowsPostAndKeepsToken() throws Exception {
        WebFixture web = new WebFixture();
        web.sessionExists = true;
        web.path = "/login";
        web.method = "POST";
        web.sessionValues.put("csrfToken", "expected");
        web.parameters.put("csrfToken", "expected");
        filter(web);
        assertTrue(web.chained);
        assertEquals("expected", web.sessionValues.get("csrfToken"));
    }

    public void testAuthenticatedMemberCanViewOrders() throws Exception {
        WebFixture web = new WebFixture();
        web.sessionExists = true;
        web.path = "/orders";
        web.sessionValues.put("loginUser", new User(1L, "alice"));
        filter(web);
        assertTrue(web.chained);
    }

    public void testWishlistGuestDestinationAndMemberCsrf() throws Exception {
        WebFixture guest = new WebFixture(); guest.path = "/wishlist"; filter(guest);
        assertEquals("/wishlist", guest.sessionValues.get("loginDestination"));
        guest = new WebFixture(); guest.path = "/wishlist/add"; guest.method = "POST"; guest.parameters.put("gameId", "12"); filter(guest);
        assertEquals("/games?id=12", guest.sessionValues.get("loginDestination")); assertFalse(guest.chained);
        for (String path : new String[] {"/wishlist/add", "/wishlist/remove"}) {
            for (String token : new String[] {"wrong", "expected"}) {
                WebFixture web = new WebFixture(); web.path = path; web.method = "POST"; web.sessionExists = true;
                web.sessionValues.put("loginUser", new User(7L, "alice")); web.sessionValues.put("csrfToken", "expected");
                web.parameters.put("csrfToken", token); filter(web);
                assertEquals("expected".equals(token), web.chained);
                if ("wrong".equals(token)) { assertEquals(403, web.status); }
            }
        }
    }
}
