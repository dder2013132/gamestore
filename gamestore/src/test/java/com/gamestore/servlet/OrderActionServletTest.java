package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.List;
import com.gamestore.model.*;
import com.gamestore.support.*;
import junit.framework.TestCase;

public class OrderActionServletTest extends TestCase {
    public void testRepurchaseConflictKeepsPendingOrderAndOffersCancellation() throws Exception {
        MemoryOrders guarded = new MemoryOrders() {
            @Override public com.gamestore.repository.OrderChangeResult completeDemoForUser(String id, long userId) {
                return com.gamestore.repository.OrderChangeResult.ALREADY_OWNED;
            }
        };
        guarded.save(new Order("alice-order", List.of(), 100, 1L)); web.contextValues.put("orderRepository", guarded);
        servlet.doPost(web.request, web.response);
        assertEquals(409, web.status); assertEquals(true, web.attributes.get("repurchaseBlocked"));
        assertTrue(((Order) web.attributes.get("order")).isPending()); assertEquals("/order-complete.jsp", web.forwarded);
        web.path = "/order/cancel"; servlet.doPost(web.request, web.response);
        assertEquals(302, web.status); assertTrue(guarded.findByIdForUser("alice-order", 1L).isCancelled());
    }
    private WebFixture web;
    private MemoryOrders orders;
    private OrderActionServlet servlet;
    @Override protected void setUp() throws Exception {
        web = new WebFixture(); web.sessionExists = true; web.method = "POST"; web.path = "/order/demo-pay";
        web.sessionValues.put("loginUser", new User(1L, "alice")); web.parameters.put("id", "alice-order");
        orders = new MemoryOrders(); orders.save(new Order("alice-order", List.of(), 100, 1L));
        orders.save(new Order("bobby-order", List.of(), 200, 2L));
        web.contextValues.put("orderRepository", orders); servlet = new OrderActionServlet(); servlet.init(web.config);
    }
    public void testDemoPaymentCompletesOnlyOwnPendingOrder() throws Exception {
        web.parameters.put("userId", "2"); web.parameters.put("status", "CANCELLED"); servlet.doPost(web.request, web.response);
        assertEquals("/gamestore/order/complete?id=alice-order", web.redirect);
        assertTrue(orders.findByIdForUser("alice-order", 1L).isCompleted());
        assertTrue(orders.findByIdForUser("bobby-order", 2L).isPending());
    }
    public void testCancelKeepsHistoryAndDoesNotChangeCart() throws Exception {
        web.path = "/order/cancel"; Object cart = List.of("new-game"); web.sessionValues.put("cart", cart);
        servlet.doPost(web.request, web.response);
        assertEquals(302, web.status); assertTrue(orders.findByIdForUser("alice-order", 1L).isCancelled());
        assertSame(cart, web.sessionValues.get("cart")); assertEquals(1, orders.findForUser(1L, null).size());
    }
    public void testRepeatingSameActionIsSuccessfulAndKeepsTimestamp() throws Exception {
        for (String path : new String[] {"/order/demo-pay", "/order/cancel"}) {
            setUp(); web.path = path; servlet.doPost(web.request, web.response);
            var first = orders.findByIdForUser("alice-order", 1L); servlet.doPost(web.request, web.response);
            var second = orders.findByIdForUser("alice-order", 1L);
            assertEquals(302, web.status); assertEquals(first.getCompletedAt(), second.getCompletedAt());
            assertEquals(first.getCancelledAt(), second.getCancelledAt());
        }
    }
    public void testOppositeActionIsConflictAndRendersCurrentState() throws Exception {
        for (String first : new String[] {"/order/demo-pay", "/order/cancel"}) {
            setUp(); web.path = first; servlet.doPost(web.request, web.response);
            web.path = first.endsWith("cancel") ? "/order/demo-pay" : "/order/cancel";
            servlet.doPost(web.request, web.response);
            assertEquals(409, web.status); assertNotNull(web.attributes.get("actionError"));
            assertEquals("/order-complete.jsp", web.forwarded);
            assertFalse(((Order) web.attributes.get("order")).isPending());
        }
    }
    public void testAnotherMembersOrderAndUnknownIdAreNotFoundForBothActions() throws Exception {
        for (String path : new String[] {"/order/demo-pay", "/order/cancel"}) {
            for (String id : new String[] {"bobby-order", "missing"}) {
                setUp(); web.path = path; web.parameters.put("id", id); servlet.doPost(web.request, web.response);
                assertEquals(404, web.status); assertTrue(orders.findByIdForUser("bobby-order", 2L).isPending());
            }
        }
    }
    public void testInvalidIdsDoNotChangeOrder() throws Exception {
        for (String id : new String[] {null, "", " ", "x".repeat(37)}) {
            setUp(); web.parameters.put("id", id); servlet.doPost(web.request, web.response);
            assertEquals(400, web.status); assertTrue(orders.findByIdForUser("alice-order", 1L).isPending());
        }
    }
    public void testDatabaseFailureShowsUncertainResultWithoutLeakingSql() throws Exception {
        orders.failure = new SQLException("internal database details"); servlet.doPost(web.request, web.response);
        assertEquals(503, web.status); assertEquals("/order-complete.jsp", web.forwarded);
        assertNotNull(web.attributes.get("pageError")); assertFalse(web.attributes.get("pageError").toString().contains("internal"));
    }
    public void testGetCannotMutateAndMissingLoginCannotPost() throws Exception {
        web.method = "GET"; servlet.service(web.request, web.response);
        assertEquals(405, web.status); assertTrue(orders.findByIdForUser("alice-order", 1L).isPending());
        web.sessionValues.remove("loginUser"); servlet.doPost(web.request, web.response); assertEquals(401, web.status);
    }
}
