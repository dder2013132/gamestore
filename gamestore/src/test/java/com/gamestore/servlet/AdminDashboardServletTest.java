package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import com.gamestore.model.*;
import com.gamestore.repository.JdbcDashboardRepository;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class AdminDashboardServletTest extends TestCase {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"),ZoneId.of("Asia/Seoul"));
    private static class Dashboard extends JdbcDashboardRepository {
        LocalDate from,to,today; int page,calls; Exception failure;
        AdminDashboard result=new AdminDashboard(new DashboardSummary(0,0,0,0,0,0,0,0),List.of(),List.of(),0,1,10);
        @Override public AdminDashboard findDashboard(LocalDate from,LocalDate to,int page,LocalDate today) throws SQLException,IOException,ClassNotFoundException {
            calls++; this.from=from; this.to=to; this.page=page; this.today=today;
            if(failure instanceof SQLException e) throw e;
            if(failure instanceof IOException e) throw e;
            if(failure instanceof ClassNotFoundException e) throw e;
            return result;
        }
    }
    private void render(WebFixture web,Dashboard dashboard) throws Exception {
        AdminDashboardServlet servlet=new AdminDashboardServlet(dashboard,CLOCK); servlet.init(web.config); servlet.doGet(web.request,web.response);
    }
    public void testAllTimeSummaryAndShortcutDatesArePassedToView() throws Exception {
        WebFixture web=new WebFixture(); web.path="/admin/dashboard"; Dashboard dashboard=new Dashboard(); render(web,dashboard);
        assertNull(dashboard.from); assertNull(dashboard.to); assertEquals(1,dashboard.page); assertEquals(LocalDate.of(2026,10,25),dashboard.today);
        assertSame(dashboard.result,web.attributes.get("dashboard")); assertEquals(LocalDate.of(2026,9,26),web.attributes.get("last30Date"));
        assertEquals("/WEB-INF/views/admin-dashboard.jsp",web.forwarded); assertEquals(200,web.status);
    }
    public void testDateRangeAndPageAreTrimmedAndForwardedWithoutMemberFilter() throws Exception {
        WebFixture web=new WebFixture(); web.parameters.put("from"," 2026-10-01 "); web.parameters.put("to","2026-10-25"); web.parameters.put("page","2"); web.parameters.put("userId","999");
        Dashboard dashboard=new Dashboard(); render(web,dashboard);
        assertEquals(LocalDate.of(2026,10,1),dashboard.from); assertEquals(LocalDate.of(2026,10,25),dashboard.to); assertEquals(2,dashboard.page);
        assertEquals("2026-10-01",web.attributes.get("fromDate")); assertEquals("2026-10-25",web.attributes.get("toDate"));
    }
    public void testInvalidDatesAndReversedRangesDoNotQueryDatabase() throws Exception {
        for(String[] range:new String[][] {{"2026-02-29",""},{"2026-2-01",""},{"0000-01-01",""},{"10000-01-01",""},{"","<script>bad</script>"},{"2026-11-01","2026-10-01"}}) {
            WebFixture web=new WebFixture(); web.parameters.put("from",range[0]); web.parameters.put("to",range[1]); Dashboard dashboard=new Dashboard(); render(web,dashboard);
            assertEquals(400,web.status); assertEquals(0,dashboard.calls); assertNull(web.attributes.get("dashboard")); assertNotNull(web.attributes.get("dashboardError"));
            assertEquals(range[0],web.attributes.get("fromDate")); assertEquals(range[1],web.attributes.get("toDate"));
        }
    }
    public void testBadPageValuesUseFirstPageAndDateBoundariesAreAccepted() throws Exception {
        for(String page:new String[] {"","abc","2147483648","1; DROP TABLE orders"}) {
            WebFixture web=new WebFixture(); web.parameters.put("page",page); web.parameters.put("from","0001-01-01"); web.parameters.put("to","9999-12-31"); Dashboard dashboard=new Dashboard(); render(web,dashboard);
            assertEquals(200,web.status); assertEquals(1,dashboard.page);
        }
    }
    public void testFailuresShow503AndNeverFabricateZeroStatistics() throws Exception {
        for(Exception failure:List.of(new SQLException("private SQL"),new IOException("private config"),new ClassNotFoundException("private driver"))) {
            WebFixture web=new WebFixture(); Dashboard dashboard=new Dashboard(); dashboard.failure=failure; render(web,dashboard);
            assertEquals(503,web.status); assertNull(web.attributes.get("dashboard")); assertNotNull(web.attributes.get("dashboardError"));
            assertFalse(web.attributes.get("dashboardError").toString().contains("private"));
        }
    }
    public void testAdminAliasRedirectsToDashboardWithoutQuery() throws Exception {
        WebFixture web=new WebFixture(); web.path="/admin"; Dashboard dashboard=new Dashboard(); render(web,dashboard);
        assertEquals("/gamestore/admin/dashboard",web.redirect); assertEquals(0,dashboard.calls); assertNull(web.forwarded);
    }
    public void testPostCannotChangeReportOrOrders() throws Exception {
        WebFixture web=new WebFixture(); web.method="POST"; Dashboard dashboard=new Dashboard();
        AdminDashboardServlet servlet=new AdminDashboardServlet(dashboard,CLOCK); servlet.init(web.config); servlet.service(web.request,web.response);
        assertEquals(405,web.status); assertEquals(0,dashboard.calls);
    }
}
