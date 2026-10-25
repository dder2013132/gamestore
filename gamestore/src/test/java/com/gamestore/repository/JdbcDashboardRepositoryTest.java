package com.gamestore.repository;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

public class JdbcDashboardRepositoryTest {
    private static String schema; private static boolean created;
    private static final LocalDate TODAY=LocalDate.of(2026,10,25);
    private static Connection connection() throws SQLException,java.io.IOException,ClassNotFoundException {
        Connection conn=DBConnection.getConnection(); try { conn.setSchema(schema); return conn; } catch(SQLException e) { conn.close(); throw e; }
    }
    private AdminDashboard report(LocalDate from,LocalDate to,int page) throws Exception { return new JdbcDashboardRepository(JdbcDashboardRepositoryTest::connection).findDashboard(from,to,page,TODAY); }
    @BeforeClass public static void setup() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("gamestore.dbTest")); schema="gamestore_dashboard_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection conn=DBConnection.getConnection();Statement stmt=conn.createStatement()) { stmt.execute("CREATE SCHEMA "+schema); created=true; }
        try(Connection conn=connection();Statement stmt=conn.createStatement()) {
            stmt.execute("CREATE TABLE games (game_id INTEGER PRIMARY KEY,title TEXT,price INTEGER,genre TEXT,image_url TEXT,is_active BOOLEAN NOT NULL DEFAULT TRUE)");
            for(String file:List.of("006_create_users.sql","004_create_orders.sql","005_create_order_items.sql","007_add_order_user.sql","016_order_status.sql")) stmt.execute(Files.readString(Path.of("sql",file)));
        }
    }
    @AfterClass public static void cleanup() throws Exception {
        if(!created) return; if(!schema.matches("gamestore_dashboard_test_[a-f0-9]{32}")) throw new IllegalStateException("Unexpected schema");
        try(Connection conn=DBConnection.getConnection();Statement stmt=conn.createStatement()) { stmt.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
    @Before public void reset() throws Exception {
        try(Connection conn=connection();Statement stmt=conn.createStatement()) {
            stmt.execute("TRUNCATE users,games RESTART IDENTITY CASCADE");
            stmt.executeUpdate("INSERT INTO users (username,password_hash) SELECT 'dashboard_member_'||n,'unused' FROM generate_series(1,3) n");
            stmt.executeUpdate("INSERT INTO games (game_id,title,price,genre,image_url) SELECT n,'Game '||n,n*100,'Adventure','images/aetherbound.png' FROM generate_series(1,12) n");
        }
    }
    private record Item(int game,int price) { }
    private String order(long user,String status,LocalDateTime createdAt,int... ids) throws Exception {
        return orderWithItems(user,status,createdAt,Arrays.stream(ids).sum()*100,Arrays.stream(ids).mapToObj(id->new Item(id,id*100)).toArray(Item[]::new));
    }
    private String orderWithItems(long user,String status,LocalDateTime createdAt,int total,Item... items) throws Exception {
        String id=UUID.randomUUID().toString();
        try(Connection conn=connection();PreparedStatement stmt=conn.prepareStatement("INSERT INTO orders (order_id,user_id,total_price,created_at,status,completed_at,cancelled_at) VALUES (?,?,?,?,?,?,?)")) {
            stmt.setString(1,id); stmt.setLong(2,user); stmt.setInt(3,total); stmt.setObject(4,createdAt); stmt.setString(5,status);
            stmt.setObject(6,status.equals("COMPLETED")?createdAt.plusDays(40):null); stmt.setObject(7,status.equals("CANCELLED")?createdAt.plusDays(1):null); stmt.executeUpdate();
            try(PreparedStatement item=conn.prepareStatement("INSERT INTO order_items (order_id,game_id,price) VALUES (?,?,?)")) {
                for(Item entry:items) { item.setString(1,id); item.setInt(2,entry.game()); item.setInt(3,entry.price()); item.executeUpdate(); }
            }
        }
        return id;
    }
    private List<String> ids(AdminDashboard report) { return report.games().stream().map(sale->sale.game().getId()).toList(); }
    @Test public void multipleItemsDoNotMultiplyCompletedOrderAmountsAndOtherStatesStaySeparate() throws Exception {
        LocalDateTime now=TODAY.atTime(12,0); order(1,"COMPLETED",now,1,2); order(1,"COMPLETED",now,3); order(2,"COMPLETED",now,1);
        order(2,"PENDING",now,4,5); order(3,"CANCELLED",now,12);
        var s=report(null,null,1).summary(); assertEquals(3,s.completedCount()); assertEquals(700,s.completedAmount()); assertEquals(4,s.soldUnits()); assertEquals(2,s.buyerCount());
        assertEquals(1,s.pendingCount()); assertEquals(900,s.pendingAmount()); assertEquals(1,s.cancelledCount()); assertEquals(1200,s.cancelledAmount());
    }
    @Test public void dateRangeIncludesBothBoundaryDaysAndUsesOrderCreationNotCompletionDate() throws Exception {
        LocalDate from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,2);
        order(1,"COMPLETED",from.minusDays(1).atTime(23,59,59,999999000),1); order(1,"COMPLETED",from.atStartOfDay(),2);
        order(2,"COMPLETED",to.atTime(23,59,59,999999000),3); order(2,"COMPLETED",to.plusDays(1).atStartOfDay(),4);
        order(3,"PENDING",from.atTime(12,0),5); order(3,"CANCELLED",to.atTime(12,0),6);
        AdminDashboard report=report(from,to,1); assertEquals(2,report.summary().completedCount()); assertEquals(500,report.summary().completedAmount());
        assertEquals(500,report.summary().pendingAmount()); assertEquals(600,report.summary().cancelledAmount());
        assertEquals(2,report.days().size()); assertEquals(from,report.days().get(0).date()); assertEquals(200,report.days().get(0).amount()); assertEquals(300,report.days().get(1).amount());
    }
    @Test public void gameSalesUsePaidPricesAndKeepPausedAndZeroSaleGames() throws Exception {
        LocalDateTime now=TODAY.atTime(12,0); order(1,"COMPLETED",now,1,2); order(1,"COMPLETED",now,1); order(2,"COMPLETED",now,1);
        try(Connection conn=connection();Statement stmt=conn.createStatement()) { stmt.executeUpdate("UPDATE games SET price=7880,title='Updated <b>Game</b>',is_active=FALSE WHERE game_id=1"); }
        AdminDashboard report=report(null,null,1); assertEquals(12,report.gameCount()); assertEquals("1",report.games().get(0).game().getId());
        GameSales first=report.games().get(0); assertEquals(3,first.units()); assertEquals(2,first.buyers()); assertEquals(300,first.amount()); assertFalse(first.game().isActive());
        assertEquals("Updated <b>Game</b>",first.game().getTitle()); assertEquals(7880,first.game().getPrice()); assertEquals(500,report.summary().completedAmount());
        assertTrue(report.games().stream().anyMatch(sale->sale.units()==0&&sale.amount()==0));
    }
    @Test public void largeAmountsStayExactBeyondIntegerRangeInSummaryGameAndChart() throws Exception {
        for(int member=1;member<=3;member++) orderWithItems(member,"COMPLETED",TODAY.atTime(12,0),Integer.MAX_VALUE,new Item(1,Integer.MAX_VALUE));
        AdminDashboard report=report(null,null,1); long expected=3L*Integer.MAX_VALUE;
        assertEquals(expected,report.summary().completedAmount()); assertEquals(expected,report.games().get(0).amount());
        assertEquals(expected,report.days().get(13).amount()); assertEquals(100,report.days().get(13).percent()); assertEquals(3,report.summary().buyerCount());
    }
    @Test public void gamePaginationHasStableTiesAndPreservesFullPeriodTotals() throws Exception {
        try(Connection conn=connection();Statement stmt=conn.createStatement()) { stmt.executeUpdate("INSERT INTO games (game_id,title,price,genre,image_url) SELECT n,'Game '||n,100,'Puzzle','images/tideglass.png' FROM generate_series(13,23) n"); }
        order(1,"COMPLETED",TODAY.atTime(12,0),1,2); Set<String> seen=new HashSet<>();
        for(int page=1;page<=3;page++) {
            AdminDashboard report=report(null,null,page); assertEquals(23,report.gameCount()); assertEquals(3,report.getPageCount()); assertEquals(300,report.summary().completedAmount());
            for(String id:ids(report)) assertTrue(seen.add(id));
        }
        assertEquals(23,seen.size()); assertEquals(List.of("2","1","23","22","21","20","19","18","17","16"),ids(report(null,null,-1)));
        AdminDashboard last=report(null,null,Integer.MAX_VALUE); assertEquals(3,last.page()); assertEquals(21,last.getFirstResult()); assertEquals(23,last.getLastResult());
    }
    @Test public void dailyTrendFillsMissingDaysAndShowsLatestFourteenDaysOnly() throws Exception {
        order(1,"COMPLETED",TODAY.minusDays(14).atTime(12,0),1); order(1,"COMPLETED",TODAY.minusDays(13).atStartOfDay(),2); order(2,"COMPLETED",TODAY.atTime(12,0),3);
        order(3,"PENDING",TODAY.atTime(12,0),4); order(3,"CANCELLED",TODAY.atTime(12,0),5);
        AdminDashboard report=report(null,null,1); assertEquals(600,report.summary().completedAmount()); assertEquals(14,report.days().size());
        assertEquals(TODAY.minusDays(13),report.days().get(0).date()); assertEquals(200,report.days().get(0).amount()); assertEquals(67,report.days().get(0).percent());
        assertEquals(0,report.days().get(1).amount()); assertEquals(0,report.days().get(1).orderCount()); assertEquals(300,report.days().get(13).amount()); assertEquals(100,report.days().get(13).percent());
    }
    @Test public void customPastFutureAndExtremeDateWindowsAreBoundedAndValidated() throws Exception {
        AdminDashboard past=report(null,LocalDate.of(2026,10,10),1); assertEquals("2026-09-27",past.getChartFrom()); assertEquals("2026-10-10",past.getChartTo());
        AdminDashboard future=report(LocalDate.of(2027,1,2),null,1); assertEquals(1,future.days().size()); assertEquals("2027-01-02",future.getChartFrom());
        assertEquals(1,report(null,LocalDate.of(1,1,1),1).days().size()); assertEquals(14,report(null,LocalDate.of(9999,12,31),1).days().size());
        try { report(TODAY,TODAY.minusDays(1),1); fail("Invalid period must be rejected"); } catch(IllegalArgumentException expected) { }
    }
    @Test public void emptyDataHasRealZerosAndMissingCatalogHasNoPhantomGame() throws Exception {
        AdminDashboard empty=report(null,null,1); assertEquals(new DashboardSummary(0,0,0,0,0,0,0,0),empty.summary()); assertEquals(12,empty.gameCount());
        assertTrue(empty.games().stream().allMatch(sale->sale.units()==0&&sale.amount()==0)); assertTrue(empty.days().stream().allMatch(day->day.amount()==0));
        try(Connection conn=connection();Statement stmt=conn.createStatement()) { stmt.executeUpdate("DELETE FROM games"); }
        AdminDashboard noGames=report(null,null,5); assertEquals(0,noGames.gameCount()); assertTrue(noGames.games().isEmpty()); assertEquals(1,noGames.page()); assertEquals(0,noGames.getFirstResult());
    }
    @Test public void freeCompletedOrdersStillCountAsCompletedSales() throws Exception {
        orderWithItems(1,"COMPLETED",TODAY.atTime(12,0),0,new Item(1,0)); AdminDashboard report=report(null,null,1);
        assertEquals(1,report.summary().completedCount()); assertEquals(0,report.summary().completedAmount()); assertEquals(1,report.summary().soldUnits());
        assertEquals(1,report.days().get(13).orderCount()); assertEquals(0,report.days().get(13).percent()); assertEquals(0,report.getPeakAmount());
    }
    @Test public void aConcurrentPaymentCannotMixSummaryTableAndDailySnapshots() throws Exception {
        String id=order(1,"PENDING",TODAY.atTime(12,0),1,2); boolean[] wrote={false};
        JdbcDashboardRepository reader=new JdbcDashboardRepository(()->{
            Connection original=connection();
            return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("prepareStatement")&&args[0] instanceof String sql&&sql.startsWith("SELECT COUNT(*) FILTER")&&!wrote[0]) {
                    wrote[0]=true;
                    try(Connection writer=connection();PreparedStatement stmt=writer.prepareStatement("UPDATE orders SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE order_id=?")) { stmt.setString(1,id); stmt.executeUpdate(); }
                }
                try { return method.invoke(original,args); } catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
            });
        });
        AdminDashboard old=reader.findDashboard(null,null,1,TODAY); assertTrue(wrote[0]); assertEquals(0,old.summary().completedCount()); assertEquals(1,old.summary().pendingCount()); assertEquals(0,old.summary().soldUnits());
        assertTrue(old.games().stream().allMatch(sale->sale.amount()==0)); assertTrue(old.days().stream().allMatch(day->day.orderCount()==0));
        AdminDashboard updated=report(null,null,1); assertEquals(1,updated.summary().completedCount()); assertEquals(2,updated.summary().soldUnits()); assertEquals(300,updated.summary().completedAmount());
    }
}
