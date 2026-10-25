package com.gamestore.repository;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.*;
import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

public class JdbcHomeRepositoryTest {
    private static String schema; private static boolean created;
    private static Connection connection() throws SQLException, java.io.IOException, ClassNotFoundException {
        Connection conn = DBConnection.getConnection(); try { conn.setSchema(schema); return conn; } catch (SQLException e) { conn.close(); throw e; }
    }
    private HomePage page() throws Exception { return new JdbcHomeRepository(JdbcHomeRepositoryTest::connection).findHome(); }
    @BeforeClass public static void setup() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("gamestore.dbTest")); schema = "gamestore_home_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) { stmt.execute("CREATE SCHEMA " + schema); created = true; }
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE games (game_id INTEGER PRIMARY KEY, title TEXT, price INTEGER, genre TEXT, image_url TEXT, is_active BOOLEAN NOT NULL DEFAULT TRUE)");
            for (String file : List.of("006_create_users.sql", "004_create_orders.sql", "005_create_order_items.sql", "007_add_order_user.sql", "016_order_status.sql", "017_create_reviews.sql")) stmt.execute(Files.readString(Path.of("sql", file)));
        }
    }
    @AfterClass public static void cleanup() throws Exception {
        if (!created) return;
        if (!schema.matches("gamestore_home_test_[a-f0-9]{32}")) throw new IllegalStateException("Unexpected test schema");
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) { stmt.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
    @Before public void reset() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE users, games RESTART IDENTITY CASCADE");
            stmt.executeUpdate("INSERT INTO users (username,password_hash) SELECT 'home_member_' || n,'unused' FROM generate_series(1,8) n");
            stmt.executeUpdate("INSERT INTO games (game_id,title,price,genre,image_url) SELECT n,'Game ' || n,n*100,'Adventure','images/aetherbound.png' FROM generate_series(1,8) n");
        }
    }
    private String purchase(long user, String status, int... gameIds) throws Exception {
        String id = UUID.randomUUID().toString();
        try (Connection conn = connection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO orders (order_id,user_id,total_price,status,completed_at,cancelled_at) VALUES (?,?,?,?,?,?)")) {
            stmt.setString(1,id); stmt.setLong(2,user); stmt.setInt(3,Arrays.stream(gameIds).sum()*100); stmt.setString(4,status);
            stmt.setObject(5,status.equals("COMPLETED") ? LocalDateTime.of(2020,1,1,12,0) : null); stmt.setObject(6,status.equals("CANCELLED") ? LocalDateTime.now() : null); stmt.executeUpdate();
            try (PreparedStatement item = conn.prepareStatement("INSERT INTO order_items (order_id,game_id,price) VALUES (?,?,?)")) {
                for (int game : gameIds) { item.setString(1,id); item.setInt(2,game); item.setInt(3,game*100); item.executeUpdate(); }
            }
        }
        return id;
    }
    private void review(long user, int game, int rating) throws Exception {
        purchase(user,"COMPLETED",game);
        try (Connection conn = connection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO reviews (user_id,game_id,rating,body) VALUES (?,?,?,'An actual purchaser review')")) {
            stmt.setLong(1,user); stmt.setInt(2,game); stmt.setInt(3,rating); stmt.executeUpdate();
        }
    }
    private List<String> ids(List<HomeGame> games) { return games.stream().map(entry -> entry.game().getId()).toList(); }
    @Test public void newArrivalsUseRegistrationIdsAndActiveCatalogCountNotFixedCovers() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active=FALSE WHERE game_id=8"); stmt.executeUpdate("UPDATE games SET title='新しい <b>物語</b>',price=7880,genre='Puzzle',image_url='images/tideglass.png' WHERE game_id=7"); }
        HomePage page = page(); assertEquals(7,page.catalogCount()); assertEquals(List.of("7","6","5","4"),ids(page.newGames()));
        assertEquals("7",page.getSpotlight().getId()); assertEquals("新しい <b>物語</b>",page.getSpotlight().getTitle()); assertEquals(7880,page.getSpotlight().getPrice());
        assertTrue(page.popularGames().isEmpty()); assertTrue(page.topRatedGames().isEmpty());
    }
    @Test public void buyersCountDistinctCompletedMembersWithoutMultiplyingReviewJoins() throws Exception {
        purchase(1,"COMPLETED",1,1); purchase(1,"COMPLETED",1); purchase(2,"COMPLETED",1); purchase(3,"PENDING",1); purchase(4,"CANCELLED",1);
        review(1,1,5); review(2,1,3); purchase(5,"PENDING",2); purchase(6,"CANCELLED",3);
        HomePage page = page(); assertEquals(List.of("1"),ids(page.popularGames())); HomeGame game=page.popularGames().get(0);
        assertEquals(2,game.buyerCount()); assertEquals(2,game.reviewCount()); assertEquals(4.0,game.averageRating(),0.0001);
        assertEquals(8,page.catalogCount());
    }
    @Test public void popularOrderingUsesBuyerCountThenIdAndLimitsToFour() throws Exception {
        for(int game=1;game<=6;game++) purchase(1,"COMPLETED",game);
        purchase(2,"COMPLETED",2); purchase(3,"COMPLETED",2); purchase(2,"COMPLETED",4); purchase(3,"COMPLETED",4);
        purchase(2,"COMPLETED",3);
        assertEquals(List.of("4","2","3","6"),ids(page().popularGames()));
    }
    @Test public void ratingsUseAverageThenReviewCountThenIdAndNeverIncludeUnreviewedGames() throws Exception {
        review(1,1,5); review(1,2,5); review(2,2,5); review(1,3,5); review(2,3,5); review(1,4,4); review(1,5,3); review(2,5,5);
        HomePage page=page(); assertEquals(List.of("3","2","1","5"),ids(page.topRatedGames()));
        assertEquals(5.0,page.topRatedGames().get(0).averageRating(),0.0001); assertEquals(2,page.topRatedGames().get(0).reviewCount());
        assertEquals(4.0,page.topRatedGames().get(3).averageRating(),0.0001); assertFalse(ids(page.topRatedGames()).contains("8"));
    }
    @Test public void pausedGamesDisappearFromEverySectionAndReappearWithOriginalStatistics() throws Exception {
        review(1,8,5); review(2,8,5);
        try (Connection conn=connection(); Statement stmt=conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active=FALSE WHERE game_id=8"); }
        HomePage paused=page(); assertEquals(7,paused.catalogCount()); assertFalse(ids(paused.newGames()).contains("8")); assertTrue(paused.popularGames().isEmpty()); assertTrue(paused.topRatedGames().isEmpty());
        try (Connection conn=connection(); Statement stmt=conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active=TRUE WHERE game_id=8"); }
        HomePage active=page(); assertEquals(8,active.catalogCount()); assertEquals(2,active.popularGames().get(0).buyerCount()); assertEquals(2,active.topRatedGames().get(0).reviewCount());
    }
    @Test public void paymentAndReviewChangesAreVisibleOnNextRequestWithoutCaching() throws Exception {
        String pending=purchase(1,"PENDING",1),cancel=purchase(2,"PENDING",2); assertTrue(page().popularGames().isEmpty());
        try (Connection conn=connection(); Statement stmt=conn.createStatement()) {
            stmt.executeUpdate("UPDATE orders SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE order_id='"+pending+"'");
            stmt.executeUpdate("UPDATE orders SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP WHERE order_id='"+cancel+"'");
            stmt.executeUpdate("INSERT INTO reviews (user_id,game_id,rating,body) VALUES (1,1,5,'Great')");
        }
        assertEquals(List.of("1"),ids(page().popularGames())); assertEquals(5.0,page().topRatedGames().get(0).averageRating(),0.0001);
        try(Connection conn=connection();Statement stmt=conn.createStatement()) { stmt.executeUpdate("UPDATE reviews SET rating=2"); }
        assertEquals(2.0,page().topRatedGames().get(0).averageRating(),0.0001);
        try(Connection conn=connection();Statement stmt=conn.createStatement()) { stmt.executeUpdate("DELETE FROM reviews"); }
        assertTrue(page().topRatedGames().isEmpty()); assertEquals(List.of("1"),ids(page().popularGames()));
    }
    @Test public void allEmptyOrSingleGameCatalogsReturnHonestEmptySections() throws Exception {
        try(Connection conn=connection();Statement stmt=conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active=FALSE"); }
        HomePage empty=page(); assertEquals(0,empty.catalogCount()); assertNull(empty.getSpotlight()); assertTrue(empty.newGames().isEmpty()); assertTrue(empty.popularGames().isEmpty()); assertTrue(empty.topRatedGames().isEmpty());
        try(Connection conn=connection();Statement stmt=conn.createStatement()) { stmt.executeUpdate("UPDATE games SET is_active=TRUE WHERE game_id=1"); }
        HomePage one=page(); assertEquals(1,one.catalogCount()); assertEquals(List.of("1"),ids(one.newGames())); assertTrue(one.popularGames().isEmpty()); assertTrue(one.topRatedGames().isEmpty());
    }
}
