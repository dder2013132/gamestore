package com.gamestore.servlet;

import java.util.List;

import com.gamestore.model.Order;
import com.gamestore.model.User;
import com.gamestore.repository.OrderRepository;
import com.gamestore.support.WebFixture;
import com.gamestore.support.MemoryOrders;

import junit.framework.TestCase;

public class OrderAccessTest extends TestCase {
    private OrderRepository orders() throws Exception {
        OrderRepository orders = new MemoryOrders();
        orders.save(new Order("alice-order", List.of(), 100, 1L));
        orders.save(new Order("bob-order", List.of(), 200, 2L));
        orders.save(new Order("legacy-order", List.of(), 300));
        return orders;
    }

    private WebFixture loggedIn(OrderRepository orders) {
        WebFixture web = new WebFixture();
        web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(1L, "alice"));
        web.contextValues.put("orderRepository", orders);
        return web;
    }

    public void testListOnlyIncludesCurrentMembersOrders() throws Exception {
        WebFixture web = loggedIn(orders());
        OrderListServlet servlet = new OrderListServlet();
        servlet.init(web.config);
        servlet.doGet(web.request, web.response);
        List<?> result = (List<?>) web.attributes.get("orders");
        assertEquals(1, result.size());
        assertEquals("alice-order", ((Order) result.get(0)).getOrderId());
        web.parameters.put("orderId", "bob");
        servlet.doGet(web.request, web.response);
        assertTrue(((List<?>) web.attributes.get("orders")).isEmpty());
    }

    public void testAnotherMembersOrderAndLegacyOrderReturn404() throws Exception {
        for (String id : new String[] { "bob-order", "legacy-order", "nonexistent" }) {
            WebFixture web = loggedIn(orders());
            web.parameters.put("id", id);
            OrderCompleteServlet servlet = new OrderCompleteServlet();
            servlet.init(web.config);
            servlet.doGet(web.request, web.response);
            assertEquals(404, web.status);
            assertNull(web.attributes.get("order"));
        }
    }

    public void testOwnOrderCanBeViewed() throws Exception {
        WebFixture web = loggedIn(orders());
        web.parameters.put("id", "alice-order");
        OrderCompleteServlet servlet = new OrderCompleteServlet();
        servlet.init(web.config);
        servlet.doGet(web.request, web.response);
        assertEquals(200, web.status);
        assertEquals("/order-complete.jsp", web.forwarded);
        assertEquals("alice-order", ((Order) web.attributes.get("order")).getOrderId());
    }

    public void testOrderLookupFailureShowsRetryInsteadOfSuccess() throws Exception {
        MemoryOrders orders = new MemoryOrders(); orders.failure = new java.sql.SQLException("private database detail");
        WebFixture web = loggedIn(orders); web.parameters.put("id", "alice-order");
        OrderCompleteServlet servlet = new OrderCompleteServlet(); servlet.init(web.config); servlet.doGet(web.request, web.response);
        assertEquals(503, web.status); assertNotNull(web.attributes.get("pageError")); assertNull(web.attributes.get("order"));
        assertEquals("/order-complete.jsp", web.forwarded);
    }
}
