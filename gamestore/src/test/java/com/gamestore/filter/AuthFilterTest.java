package com.gamestore.filter;

import com.gamestore.model.User;
import com.gamestore.support.WebFixture;
import com.gamestore.repository.JdbcUserRepository;
import java.sql.SQLException;

import junit.framework.TestCase;

public class AuthFilterTest extends TestCase {
    public void testJsonWishlistGuestReturns401AndKeepsSafeLoginDestination() throws Exception {
        WebFixture web=new WebFixture();web.path="/wishlist/add";web.method="POST";web.parameters.put("gameId","12");web.requestHeaders.put("Accept","application/json");filter(web);
        assertEquals(401,web.status);assertFalse(web.chained);assertNull(web.redirect);assertEquals("application/json",web.contentType);
        assertTrue(web.responseBody.toString().contains("login_required"));assertEquals("/games?id=12",web.sessionValues.get("loginDestination"));
    }
    public void testJsonWishlistStillRequiresCsrfBeforeReachingRepository() throws Exception {
        for(String action:new String[]{"add","remove"}) for(String token:new String[]{null,"wrong","expected"}) {
            WebFixture web=new WebFixture();web.path="/wishlist/"+action;web.method="POST";web.sessionExists=true;web.sessionValues.put("loginUser",new User(7L,"alice"));
            web.sessionValues.put("csrfToken","expected");web.parameters.put("csrfToken",token);web.requestHeaders.put("Accept","application/json");filter(web);
            assertEquals("expected".equals(token),web.chained);
            if(!web.chained){assertEquals(403,web.status);assertTrue(web.responseBody.toString().contains("csrf_invalid"));assertNull(web.redirect);}
        }
    }
    public void testDashboardGuestReturnAndCurrentDatabaseRoleForBothRoutes() throws Exception {
        for(String path:new String[] {"/admin","/admin/dashboard"}) {
            WebFixture guest=new WebFixture(); guest.path=path; filter(guest);
            assertEquals("/gamestore/login",guest.redirect); assertEquals("/admin/dashboard",com.gamestore.util.AuthSession.takeDestination(guest.session));
            for(String role:new String[] {"USER","ADMIN","deleted"}) {
                WebFixture web=new WebFixture(); web.path=path; web.sessionExists=true; web.sessionValues.put("loginUser",new User(7L,"alice","ADMIN")); web.parameters.put("userId","8");
                JdbcUserRepository users=new JdbcUserRepository() {
                    @Override public User findById(long id) { assertEquals(7L,id); return role.equals("deleted")?null:new User(id,"alice",role); }
                };
                new AuthFilter(users,noOwnership()).doFilter(web.request,web.response,(req,res)->web.chained=true);
                assertEquals(role.equals("ADMIN"),web.chained); if(!web.chained) assertEquals(403,web.status);
            }
        }
    }
    public void testDashboardRoleLookupFailureDoesNotReachTheReport() throws Exception {
        WebFixture web=new WebFixture(); web.path="/admin/dashboard"; web.sessionExists=true; web.sessionValues.put("loginUser",new User(7L,"alice","ADMIN"));
        JdbcUserRepository users=new JdbcUserRepository() { @Override public User findById(long id) throws SQLException { throw new SQLException("private SQL"); } };
        new AuthFilter(users,noOwnership()).doFilter(web.request,web.response,(req,res)->web.chained=true);
        assertEquals(503,web.status); assertFalse(web.chained);
    }
    private com.gamestore.repository.JdbcOwnershipRepository noOwnership() {
        return new com.gamestore.repository.JdbcOwnershipRepository() {
            @Override public java.util.Set<String> findIdsForUser(long userId) { return java.util.Set.of(); }
        };
    }
    public void testOwnershipAlwaysUsesSessionMemberForShoppingPages() throws Exception {
        for (String path : new String[] {"/home", "/browse", "/games", "/wishlist", "/cart", "/checkout", "/order", "/order/complete", "/order/demo-pay", "/order/cancel"}) {
            WebFixture web = new WebFixture(); web.path = path; web.sessionExists = true;
            web.sessionValues.put("loginUser", new User(7L, "alice")); web.parameters.put("userId", "8");
            var ownership = new com.gamestore.repository.JdbcOwnershipRepository() {
                @Override public java.util.Set<String> findIdsForUser(long userId) { assertEquals(7L, userId); return java.util.Set.of("1"); }
            };
            new AuthFilter(new JdbcUserRepository(), ownership).doFilter(web.request, web.response, (req, res) -> web.chained = true);
            assertTrue(web.chained); assertEquals(java.util.Map.of("1", true), web.attributes.get("ownedIds"));
        }
    }
    public void testGuestPublicPageDoesNotQueryPrivateOwnership() throws Exception {
        WebFixture web = new WebFixture(); web.path = "/games";
        var ownership = new com.gamestore.repository.JdbcOwnershipRepository() {
            @Override public java.util.Set<String> findIdsForUser(long userId) { throw new AssertionError("Guest ownership query"); }
        };
        new AuthFilter(new JdbcUserRepository(), ownership).doFilter(web.request, web.response, (req, res) -> web.chained = true);
        assertTrue(web.chained); assertEquals(java.util.Map.of(), web.attributes.get("ownedIds"));
    }
    public void testOwnershipFailureStopsPurchaseInsteadOfTreatingMemberAsUnowned() throws Exception {
        WebFixture web = new WebFixture(); web.path = "/cart"; web.method = "POST"; web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(7L, "alice")); web.sessionValues.put("csrfToken", "expected"); web.parameters.put("csrfToken", "expected");
        var ownership = new com.gamestore.repository.JdbcOwnershipRepository() {
            @Override public java.util.Set<String> findIdsForUser(long userId) throws SQLException { throw new SQLException("private database details"); }
        };
        new AuthFilter(new JdbcUserRepository(), ownership).doFilter(web.request, web.response, (req, res) -> web.chained = true);
        assertEquals(503, web.status); assertFalse(web.chained); assertEquals("/WEB-INF/views/ownership-error.jsp", web.forwarded);
        assertNull(web.attributes.get("ownedIds"));
    }
    public void testCsrfIsCheckedBeforeOwnershipLookup() throws Exception {
        WebFixture web = new WebFixture(); web.path = "/cart"; web.method = "POST"; web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(7L, "alice"));
        var ownership = new com.gamestore.repository.JdbcOwnershipRepository() {
            @Override public java.util.Set<String> findIdsForUser(long userId) { throw new AssertionError("CSRF must be checked first"); }
        };
        new AuthFilter(new JdbcUserRepository(), ownership).doFilter(web.request, web.response, (req, res) -> web.chained = true);
        assertEquals(403, web.status); assertFalse(web.chained);
    }
    public void testLibraryRequiresLoginAndReturnsToLibraryAfterLogin() throws Exception {
        WebFixture guest = new WebFixture(); guest.path = "/library"; filter(guest);
        assertEquals("/gamestore/login", guest.redirect); assertFalse(guest.chained);
        assertEquals("/library", com.gamestore.util.AuthSession.takeDestination(guest.session));
        WebFixture member = new WebFixture(); member.path = "/library"; member.sessionExists = true;
        member.sessionValues.put("loginUser", new User(7L, "alice")); filter(member); assertTrue(member.chained);
    }
    public void testReviewRoutesRequireMemberAndMutationCsrf() throws Exception {
        for (String path : new String[] {"/reviews/edit", "/reviews/create", "/reviews/update", "/reviews/delete", "/account/reviews"}) {
            WebFixture guest = new WebFixture(); guest.path = path; guest.method = "POST"; guest.parameters.put("gameId", "1"); filter(guest);
            assertFalse(guest.chained); assertEquals("/gamestore/login", guest.redirect);
            assertEquals(path.equals("/account/reviews") ? "/account/reviews" : "/games?id=1", com.gamestore.util.AuthSession.takeDestination(guest.session));
        }
        for (String path : new String[] {"/reviews/create", "/reviews/update", "/reviews/delete"}) {
            for (String token : new String[] {null, "wrong", "expected"}) {
                WebFixture web = new WebFixture(); web.path = path; web.method = "POST"; web.sessionExists = true;
                web.sessionValues.put("loginUser", new User(7L, "alice")); web.sessionValues.put("csrfToken", "expected"); web.parameters.put("csrfToken", token);
                filter(web); assertEquals("expected".equals(token), web.chained);
                if (!web.chained) { assertEquals(403, web.status); }
            }
        }
    }
    public void testDemoPaymentAndCancellationRequireMemberAndCsrf() throws Exception {
        for (String path : new String[] {"/order/demo-pay", "/order/cancel"}) {
            for (String method : new String[] {"GET", "POST"}) {
                WebFixture guest = new WebFixture(); guest.path = path; guest.method = method; filter(guest);
                assertEquals("/gamestore/login", guest.redirect); assertFalse(guest.chained);
                assertEquals("/orders", guest.sessionValues.get("loginDestination"));
            }
            for (String token : new String[] {null, "wrong", "expected"}) {
                WebFixture web = new WebFixture(); web.path = path; web.method = "POST"; web.sessionExists = true;
                web.sessionValues.put("loginUser", new User(1L, "alice")); web.sessionValues.put("csrfToken", "expected");
                web.parameters.put("csrfToken", token); filter(web);
                assertEquals("expected".equals(token), web.chained);
                if (!web.chained) { assertEquals(403, web.status); }
            }
        }
    }
    public void testAccountRoutesRequireLoginAndPasswordPostsRequireCsrf() throws Exception {
        for (String path : new String[] {"/account", "/account/password"}) {
            for (String method : new String[] {"GET", "POST"}) {
                WebFixture guest = new WebFixture(); guest.path = path; guest.method = method; filter(guest);
                assertFalse(guest.chained); assertEquals("/gamestore/login", guest.redirect);
                assertEquals(path, com.gamestore.util.AuthSession.takeDestination(guest.session));
            }
        }
        for (String token : new String[] {null, "wrong", "expected"}) {
            WebFixture web = new WebFixture(); web.path = "/account/password"; web.method = "POST"; web.sessionExists = true;
            web.sessionValues.put("loginUser", new User(7L, "alice")); web.sessionValues.put("csrfToken", "expected");
            web.parameters.put("csrfToken", token); filter(web);
            assertEquals("expected".equals(token), web.chained);
            if (!web.chained) { assertEquals(403, web.status); }
        }
    }
    public void testAdminOrderRoutesRequireLiveAdministratorRole() throws Exception {
        for (String path : new String[] {"/admin/orders", "/admin/orders/detail"}) {
            WebFixture guest = new WebFixture(); guest.path = path; filter(guest);
            assertEquals("/gamestore/login", guest.redirect);
            assertEquals("/admin/orders", guest.sessionValues.get("loginDestination"));
            assertEquals("/admin/orders", com.gamestore.util.AuthSession.takeDestination(guest.session));
            for (String role : new String[] {"USER", "ADMIN"}) {
                WebFixture web = new WebFixture(); web.path = path; web.sessionExists = true;
                web.sessionValues.put("loginUser", new User(1L, "alice", "ADMIN"));
                Users users = new Users(); users.current = new User(1L, "alice", role);
                new AuthFilter(users, noOwnership()).doFilter(web.request, web.response, (req, res) -> web.chained = true);
                assertEquals("ADMIN".equals(role), web.chained);
                if (!web.chained) { assertEquals(403, web.status); }
            }
            WebFixture unavailable = new WebFixture(); unavailable.path = path; unavailable.sessionExists = true;
            unavailable.sessionValues.put("loginUser", new User(1L, "alice", "ADMIN"));
            Users users = new Users(); users.failure = new SQLException();
            new AuthFilter(users, noOwnership()).doFilter(unavailable.request, unavailable.response, (req, res) -> unavailable.chained = true);
            assertEquals(503, unavailable.status); assertFalse(unavailable.chained);
        }
    }
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
            new AuthFilter(users, noOwnership()).doFilter(web.request, web.response, (req, res) -> web.chained = true);
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
            new AuthFilter(users, noOwnership()).doFilter(web.request, web.response, (req, res) -> web.chained = true);
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
        new AuthFilter(users, noOwnership()).doFilter(web.request, web.response, (req, res) -> web.chained = true);
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
        new AuthFilter(users, noOwnership()).doFilter(web.request, web.response, (req, res) -> web.chained = true);
        assertEquals(503, web.status);
        assertFalse(web.chained);
    }
    private void filter(WebFixture web) throws Exception {
        new AuthFilter(new JdbcUserRepository(), noOwnership()).doFilter(web.request, web.response, (request, response) -> web.chained = true);
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
                new AuthFilter(users, noOwnership()).doFilter(web.request, web.response, (req, res) -> web.chained = true);
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
                    new AuthFilter(users, noOwnership()).doFilter(web.request, web.response, (req, res) -> web.chained = true);
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
