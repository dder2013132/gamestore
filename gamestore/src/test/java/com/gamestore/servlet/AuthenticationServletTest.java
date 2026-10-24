package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.gamestore.model.User;
import com.gamestore.repository.JdbcUserRepository;
import com.gamestore.support.WebFixture;
import com.gamestore.util.AuthSession;

import junit.framework.TestCase;

public class AuthenticationServletTest extends TestCase {
    private static class Users extends JdbcUserRepository {
        User authenticated;
        SQLException failure;
        int creates;
        String username;

        @Override
        public User authenticate(String username, String password) throws SQLException {
            this.username = username;
            if (failure != null) { throw failure; }
            return authenticated;
        }

        @Override
        public User create(String username, String password) throws SQLException {
            this.username = username;
            creates++;
            if (failure != null) { throw failure; }
            return new User(1L, username);
        }
    }

    private WebFixture form() {
        WebFixture web = new WebFixture();
        web.sessionExists = true;
        web.parameters.put("username", " Alice ");
        web.parameters.put("password", "correct password");
        web.parameters.put("passwordConfirm", "correct password");
        return web;
    }

    public void testLoginRotatesSessionAndTokenAndPreservesCart() throws Exception {
        Users users = new Users();
        users.authenticated = new User(1L, "alice");
        WebFixture web = form();
        Object cart = List.of("game");
        web.sessionValues.put("cart", cart);
        web.sessionValues.put("csrfToken", "old-token");
        web.sessionValues.put("loginDestination", "/checkout");
        LoginServlet servlet = new LoginServlet(users);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals("alice", users.username);
        assertSame(users.authenticated, web.sessionValues.get("loginUser"));
        assertSame(cart, web.sessionValues.get("cart"));
        assertTrue(web.sessionIdChanged);
        assertFalse("old-token".equals(web.sessionValues.get("csrfToken")));
        assertEquals(1800, web.timeout);
        assertEquals("/gamestore/checkout", web.redirect);
        assertNull(web.sessionValues.get("loginDestination"));
    }

    public void testInvalidLoginShowsGenericErrorWithoutAuthenticating() throws Exception {
        WebFixture web = form();
        LoginServlet servlet = new LoginServlet(new Users());
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals("/WEB-INF/views/login.jsp", web.forwarded);
        assertNotNull(web.attributes.get("error"));
        assertNull(web.sessionValues.get("loginUser"));
        assertFalse(web.sessionIdChanged);
        assertNull(web.attributes.get("password"));
    }

    public void testLoginDatabaseFailureDoesNotExposeSql() throws Exception {
        Users users = new Users();
        users.failure = new SQLException("internal db detail", "08001");
        WebFixture web = form();
        LoginServlet servlet = new LoginServlet(users);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(503, web.status);
        assertEquals("/WEB-INF/views/login.jsp", web.forwarded);
        assertFalse(web.attributes.get("error").toString().contains("internal db detail"));
        assertNull(web.sessionValues.get("loginUser"));
    }

    public void testRegistrationRedirectsToLoginWithoutAutoLogin() throws Exception {
        Users users = new Users();
        WebFixture web = form();
        RegisterServlet servlet = new RegisterServlet(users);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(1, users.creates);
        assertEquals("alice", users.username);
        assertEquals("/gamestore/login?registered=1", web.redirect);
        assertNull(web.sessionValues.get("loginUser"));
    }

    public void testRegistrationValidatesUsernameLengthAndConfirmationOnServer() throws Exception {
        for (String[] change : new String[][] {
                { "username", "bad'name" }, { "username", "abc" },
                { "password", "short" }, { "password", "x".repeat(129) },
                { "passwordConfirm", "different" } }) {
            Users users = new Users();
            WebFixture web = form();
            web.parameters.put(change[0], change[1]);
            RegisterServlet servlet = new RegisterServlet(users);
            servlet.init(web.config);
            servlet.doPost(web.request, web.response);
            assertEquals(0, users.creates);
            assertNotNull(web.attributes.get("error"));
            assertEquals("/WEB-INF/views/register.jsp", web.forwarded);
        }
    }

    public void testDuplicateRegistrationShowsFormError() throws Exception {
        Users users = new Users();
        users.failure = new SQLException("unique violation", "23505");
        WebFixture web = form();
        RegisterServlet servlet = new RegisterServlet(users);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals("/WEB-INF/views/register.jsp", web.forwarded);
        assertNotNull(web.attributes.get("error"));
        assertEquals(200, web.status);
    }

    public void testRegistrationDatabaseFailureShowsUnavailable() throws Exception {
        Users users = new Users();
        users.failure = new SQLException("internal detail", "08001");
        WebFixture web = form();
        RegisterServlet servlet = new RegisterServlet(users);
        servlet.init(web.config);
        servlet.doPost(web.request, web.response);
        assertEquals(503, web.status);
        assertEquals("/WEB-INF/views/register.jsp", web.forwarded);
    }

    public void testLogoutInvalidatesSession() throws IOException {
        WebFixture web = form();
        web.sessionValues.put("loginUser", new User(1L, "alice"));
        web.sessionValues.put("cart", List.of("game"));
        new LogoutServlet().doPost(web.request, web.response);
        assertTrue(web.invalidated);
        assertTrue(web.sessionValues.isEmpty());
        assertEquals("/gamestore/home", web.redirect);
    }

    public void testLoginDestinationCannotRedirectOffSite() {
        WebFixture web = form();
        web.sessionValues.put("loginDestination", "https://example.com");
        assertEquals("/home", AuthSession.takeDestination(web.session));
    }

    public void testLoginReturnsToCartOrTheSelectedProduct() throws Exception {
        for (String destination : new String[] { "/cart", "/games?id=8" }) {
            Users users = new Users();
            users.authenticated = new User(1L, "alice");
            WebFixture web = form();
            web.sessionValues.put("loginDestination", destination);
            LoginServlet servlet = new LoginServlet(users);
            servlet.init(web.config);
            servlet.doPost(web.request, web.response);
            assertEquals("/gamestore" + destination, web.redirect);
            assertNull(web.sessionValues.get("loginDestination"));
        }
    }

    public void testProductDestinationAcceptsOnlyValidLocalGameIds() {
        for (String id : new String[] { "0", "-1", "2147483648", "8&next=https://example.com", "//example.com", "8/../../admin" }) {
            WebFixture web = form();
            web.sessionValues.put("loginDestination", "/games?id=" + id);
            assertEquals("/cart", AuthSession.takeDestination(web.session));
        }
    }
}
