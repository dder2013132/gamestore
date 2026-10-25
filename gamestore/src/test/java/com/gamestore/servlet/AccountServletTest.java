package com.gamestore.servlet;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import com.gamestore.model.AccountOverview;
import com.gamestore.model.User;
import com.gamestore.repository.*;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class AccountServletTest extends TestCase {
    private static class Accounts extends JdbcAccountRepository {
        long requestedId;
        SQLException failure;
        AccountOverview result = new AccountOverview("alice", LocalDateTime.of(2026, 10, 24, 12, 0), 0, 0, 0, List.of(), List.of());
        @Override public AccountOverview findForUser(long id) throws SQLException {
            requestedId = id; if (failure != null) { throw failure; } return result;
        }
    }
    private static class Users extends JdbcUserRepository {
        long requestedId;
        int changes;
        boolean success = true;
        SQLException failure;
        String current, next;
        @Override public boolean changePassword(long id, String current, String next) throws SQLException {
            requestedId = id; changes++; this.current = current; this.next = next;
            if (failure != null) { throw failure; } return success;
        }
    }
    private WebFixture web;
    private Accounts accounts;
    private Users users;
    private AccountServlet servlet;
    @Override protected void setUp() throws Exception {
        web = new WebFixture(); web.path = "/account"; web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(7L, "alice"));
        accounts = new Accounts(); users = new Users(); servlet = new AccountServlet(accounts, users); servlet.init(web.config);
    }
    private void passwordForm() {
        web.path = "/account/password"; web.method = "POST";
        web.parameters.put("currentPassword", "old password"); web.parameters.put("newPassword", "new password");
        web.parameters.put("passwordConfirm", "new password");
    }
    public void testDashboardUsesSessionMemberAndIgnoresUrlUserId() throws Exception {
        web.parameters.put("userId", "99"); servlet.doGet(web.request, web.response);
        assertEquals(7L, accounts.requestedId); assertSame(accounts.result, web.attributes.get("account"));
        assertEquals("/WEB-INF/views/account.jsp", web.forwarded);
    }
    public void testDashboardDatabaseErrorDoesNotFabricateZeroCounts() throws Exception {
        accounts.failure = new SQLException("internal connection data"); servlet.doGet(web.request, web.response);
        assertEquals(503, web.status); assertNull(web.attributes.get("account")); assertNotNull(web.attributes.get("error"));
        assertFalse(web.attributes.get("error").toString().contains("internal"));
    }
    public void testDeletedAccountInvalidatesSession() throws Exception {
        accounts.result = null; servlet.doGet(web.request, web.response);
        assertTrue(web.invalidated); assertEquals("/gamestore/login", web.redirect); assertNull(web.forwarded);
    }
    public void testPasswordGetDoesNotChangePasswordOrQueryDashboard() throws Exception {
        web.path = "/account/password"; servlet.doGet(web.request, web.response);
        assertEquals("/WEB-INF/views/account-password.jsp", web.forwarded); assertEquals(0, users.changes); assertEquals(0L, accounts.requestedId);
    }
    public void testPasswordValidationDoesNotCallRepositoryOrEchoSecrets() throws Exception {
        for (String[] change : new String[][] {{"currentPassword", "short"}, {"currentPassword", "x".repeat(129)},
                {"newPassword", "short"}, {"newPassword", "x".repeat(129)}, {"passwordConfirm", "different"}, {"newPassword", null},
                {"currentPassword", null}, {"passwordConfirm", null}}) {
            setUp(); passwordForm(); web.parameters.put(change[0], change[1]); servlet.doPost(web.request, web.response);
            assertEquals(400, web.status); assertEquals(0, users.changes); assertFalse(web.sessionIdChanged);
            assertEquals("/WEB-INF/views/account-password.jsp", web.forwarded);
            for (String name : new String[] {"currentPassword", "newPassword", "passwordConfirm"}) {
                assertFalse(web.attributes.containsKey(name)); assertFalse(web.sessionValues.containsKey(name));
            }
        }
        setUp(); passwordForm(); web.parameters.put("newPassword", "old password"); web.parameters.put("passwordConfirm", "old password");
        servlet.doPost(web.request, web.response); assertEquals(400, web.status); assertEquals(0, users.changes);
    }
    public void testWrongCurrentPasswordKeepsSessionAndCart() throws Exception {
        passwordForm(); users.success = false;
        Object cart = List.of("game"); web.sessionValues.put("cart", cart); web.sessionValues.put("csrfToken", "token");
        servlet.doPost(web.request, web.response);
        assertEquals(400, web.status); assertSame(cart, web.sessionValues.get("cart")); assertFalse(web.sessionIdChanged);
        assertEquals("token", web.sessionValues.get("csrfToken")); assertNotNull(web.attributes.get("error"));
    }
    public void testSuccessfulPasswordChangeUsesSessionIdAndRotatesCsrfWithoutLosingCart() throws Exception {
        passwordForm(); web.parameters.put("userId", "99");
        Object cart = List.of("game"); web.sessionValues.put("cart", cart); web.sessionValues.put("csrfToken", "before");
        servlet.doPost(web.request, web.response);
        assertEquals(7L, users.requestedId); assertEquals("old password", users.current); assertEquals("new password", users.next);
        assertTrue(web.sessionIdChanged); assertFalse("before".equals(web.sessionValues.get("csrfToken")));
        assertSame(cart, web.sessionValues.get("cart")); assertEquals(1800, web.timeout);
        assertEquals("/gamestore/account/password?changed=1", web.redirect); assertNull(web.forwarded);
    }
    public void testPasswordWhitespaceIsPreservedAndMaximumLengthAccepted() throws Exception {
        passwordForm(); String next = " " + "x".repeat(126) + " ";
        web.parameters.put("newPassword", next); web.parameters.put("passwordConfirm", next);
        servlet.doPost(web.request, web.response); assertEquals(next, users.next); assertEquals(302, web.status);
    }
    public void testPasswordDatabaseFailureShowsGenericErrorWithoutRotatingSession() throws Exception {
        passwordForm(); users.failure = new SQLException("internal credentials"); servlet.doPost(web.request, web.response);
        assertEquals(503, web.status); assertFalse(web.sessionIdChanged);
        assertFalse(web.attributes.get("error").toString().contains("internal"));
        assertEquals("/WEB-INF/views/account-password.jsp", web.forwarded);
    }
    public void testUnsupportedPostAndUnauthenticatedRequestsCannotChangePassword() throws Exception {
        servlet.doPost(web.request, web.response); assertEquals(405, web.status); assertEquals(0, users.changes);
        setUp(); web.sessionValues.clear(); servlet.doGet(web.request, web.response); assertEquals(401, web.status);
        passwordForm(); servlet.doPost(web.request, web.response); assertEquals(401, web.status); assertEquals(0, users.changes);
    }
}
