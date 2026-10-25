package com.gamestore.servlet;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.gamestore.model.*;
import com.gamestore.repository.JdbcAdminOrderRepository;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class AdminOrderServletTest extends TestCase {
    private static class Orders extends JdbcAdminOrderRepository {
        String query, id;
        String status;
        LocalDate from, to;
        int requestedPage, calls;
        SQLException failure;
        AdminOrder detail;
        @Override public AdminOrderPage findPage(String query, LocalDate from, LocalDate to, String status, int page, int size) throws SQLException {
            calls++;
            if (failure != null) { throw failure; }
            this.query = query; this.from = from; this.to = to; this.status = status; requestedPage = page;
            return new AdminOrderPage(List.of(), 0, 0L, 1, size);
        }
        @Override public AdminOrder findById(String id) throws SQLException {
            calls++;
            if (failure != null) { throw failure; }
            this.id = id; return detail;
        }
    }
    private WebFixture web;
    private Orders orders;
    private AdminOrderServlet servlet;
    @Override protected void setUp() throws Exception {
        web = new WebFixture(); web.path = "/admin/orders";
        orders = new Orders(); servlet = new AdminOrderServlet(orders); servlet.init(web.config);
    }

    public void testSearchConditionsAndPaginationArePassedToRepository() throws Exception {
        web.parameters.put("q", "  Manager  "); web.parameters.put("from", "2026-10-24");
        web.parameters.put("to", "2026-10-25"); web.parameters.put("page", "3");
        servlet.doGet(web.request, web.response);
        assertEquals("Manager", orders.query); assertEquals(LocalDate.of(2026, 10, 24), orders.from);
        assertEquals(LocalDate.of(2026, 10, 25), orders.to); assertEquals(3, orders.requestedPage);
        assertEquals("/WEB-INF/views/admin-orders.jsp", web.forwarded);
        assertEquals(0, web.attributes.get("resultCount")); assertNull(web.attributes.get("error"));
    }

    public void testStatusFilterIsValidatedAndPassedToRepository() throws Exception {
        web.parameters.put("status", "CANCELLED"); servlet.doGet(web.request, web.response);
        assertEquals("CANCELLED", orders.status); assertEquals("CANCELLED", web.attributes.get("selectedStatus"));
        setUp(); web.parameters.put("status", "' OR TRUE --"); servlet.doGet(web.request, web.response);
        assertEquals(400, web.status); assertEquals(0, orders.calls);
    }
    public void testInvalidDatesAndReversedRangeDoNotQueryDatabase() throws Exception {
        for (String[] range : new String[][] {{"2026-02-30", ""}, {"0000-01-01", ""}, {"2026-1-1", ""},
                {"2026-10-25", "2026-10-24"}, {"", "invalid"}}) {
            setUp(); web.parameters.put("from", range[0]); web.parameters.put("to", range[1]);
            servlet.doGet(web.request, web.response);
            assertEquals(400, web.status); assertEquals(0, orders.calls);
            assertNotNull(web.attributes.get("error")); assertNull(web.attributes.get("resultCount"));
            assertEquals(range[0], web.attributes.get("fromDate"));
        }
    }
    public void testMalformedPageFallsBackAndExcessiveKeywordIsRejected() throws Exception {
        web.parameters.put("page", "2147483648"); servlet.doGet(web.request, web.response);
        assertEquals(1, orders.requestedPage);
        setUp(); web.parameters.put("q", "x".repeat(101)); servlet.doGet(web.request, web.response);
        assertEquals(400, web.status); assertEquals(0, orders.calls);
    }
    public void testDatabaseFailureShowsErrorInsteadOfEmptyList() throws Exception {
        orders.failure = new SQLException("private database detail"); servlet.doGet(web.request, web.response);
        assertEquals(503, web.status); assertNotNull(web.attributes.get("error"));
        assertFalse(web.attributes.get("error").toString().contains("private database"));
        assertNull(web.attributes.get("resultCount")); assertEquals("/WEB-INF/views/admin-orders.jsp", web.forwarded);
    }
    public void testDetailIncludesBuyerAndRetainsReturnFilters() throws Exception {
        web.path = "/admin/orders/detail"; web.parameters.put("id", "order-123");
        web.parameters.put("q", "alice"); web.parameters.put("from", "2026-10-24"); web.parameters.put("page", "2");
        orders.detail = new AdminOrder(new Order("order-123", List.of(), 4800, 1L, LocalDateTime.now()), "alice", 1);
        servlet.doGet(web.request, web.response);
        assertEquals("order-123", orders.id); assertSame(orders.detail, web.attributes.get("adminOrder"));
        assertEquals("alice", web.attributes.get("query")); assertEquals("2", web.attributes.get("requestedPage"));
        assertEquals("/WEB-INF/views/admin-order-detail.jsp", web.forwarded);
    }
    public void testMissingOrInvalidDetailIdIsRejected() throws Exception {
        for (String id : new String[] {"", "x".repeat(37)}) {
            setUp(); web.path = "/admin/orders/detail"; web.parameters.put("id", id);
            servlet.doGet(web.request, web.response); assertEquals(400, web.status); assertEquals(0, orders.calls);
        }
        setUp(); web.path = "/admin/orders/detail"; web.parameters.put("id", "missing");
        servlet.doGet(web.request, web.response); assertEquals(404, web.status); assertNull(web.forwarded);
    }
    public void testDetailDatabaseFailureRendersRetryAndReturnLink() throws Exception {
        web.path = "/admin/orders/detail"; web.parameters.put("id", "order-123"); orders.failure = new SQLException();
        servlet.doGet(web.request, web.response);
        assertEquals(503, web.status); assertEquals("/WEB-INF/views/admin-order-detail.jsp", web.forwarded);
        assertNotNull(web.attributes.get("error")); assertNull(web.attributes.get("adminOrder"));
    }
}
