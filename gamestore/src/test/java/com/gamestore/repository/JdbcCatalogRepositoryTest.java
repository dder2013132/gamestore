package com.gamestore.repository;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gamestore.model.Game;
import com.gamestore.model.Order;
import com.gamestore.model.User;
import com.gamestore.model.CatalogPage;
import com.gamestore.util.DBConnection;

public class JdbcCatalogRepositoryTest {
    private static String schema;
    private static boolean created;

    private static Connection connection() throws SQLException, IOException, ClassNotFoundException {
        Connection conn = DBConnection.getConnection();
        try {
            conn.setSchema(schema);
            return conn;
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
    }

    private JdbcGameRepository games() { return new JdbcGameRepository(JdbcCatalogRepositoryTest::connection); }
    private JdbcUserRepository users() { return new JdbcUserRepository(JdbcCatalogRepositoryTest::connection); }
    private JdbcOrderRepository orders() { return new JdbcOrderRepository(JdbcCatalogRepositoryTest::connection); }
    private JdbcWishlistRepository wishes() { return new JdbcWishlistRepository(JdbcCatalogRepositoryTest::connection); }

    @BeforeClass
    public static void setup() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("gamestore.dbTest"));
        schema = "gamestore_catalog_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA " + schema);
            created = true;
        }
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE games (game_id INTEGER PRIMARY KEY, title VARCHAR(200), price INTEGER, genre VARCHAR(50), image_url VARCHAR(500), description TEXT)");
            stmt.execute("INSERT INTO games (game_id, title, price, genre, image_url) VALUES (7, 'Existing Game', 1000, 'Adventure', 'images/existing.jpg')");
            for (String file : List.of("006_create_users.sql", "004_create_orders.sql", "005_create_order_items.sql",
                    "007_add_order_user.sql", "008_add_user_role.sql", "012_add_game_sale_status.sql", "014_order_checkout_tokens.sql", "015_create_wishlist.sql")) {
                stmt.execute(Files.readString(Path.of("sql", file)));
            }
        }
    }

    @AfterClass
    public static void cleanup() throws Exception {
        if (!created) { return; }
        if (!schema.matches("gamestore_catalog_test_[a-f0-9]{32}")) { throw new IllegalStateException("Unexpected schema"); }
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Before
    public void clear() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM order_items");
            stmt.executeUpdate("DELETE FROM orders");
            stmt.executeUpdate("DELETE FROM games WHERE game_id <> 7");
            stmt.executeUpdate("DELETE FROM users");
        }
    }

    @Test
    public void registrationIsOrdinaryAndPromotionRequiresDatabaseUpdate() throws Exception {
        User user = users().create("fixture_user", "long-test-password");
        assertEquals("USER", user.getRole());
        assertFalse(users().authenticate("fixture_user", "long-test-password").isAdmin());
        try (Connection conn = connection(); PreparedStatement stmt = conn.prepareStatement("UPDATE users SET role = 'ADMIN' WHERE user_id = ?")) {
            stmt.setLong(1, user.getId());
            stmt.executeUpdate();
        }
        assertTrue(users().findById(user.getId()).isAdmin());
        assertTrue(users().authenticate("fixture_user", "long-test-password").isAdmin());
    }

    @Test
    public void generatedGameIdFollowsExistingIdsAndStoresTextLiterally() throws Exception {
        Game game = games().create("Signal's <b>Garden</b>", 2400, "Adventure", "images/aetherbound.png");
        assertTrue(Integer.parseInt(game.getId()) > 7);
        Game restored = games().findById(game.getId());
        assertEquals("Signal's <b>Garden</b>", restored.getTitle());
        assertEquals(2400, restored.getPrice());
        assertEquals(2, games().findAll().size());
        assertNull(games().findById("' OR 1=1"));
        assertNull(games().findById("2147483648"));
    }

    @Test
    public void descriptionIsStoredAndReturnedByNewRepositoryInstances() throws Exception {
        String description = "月明かりの町へ。\n友達と冒険！\n<script>literal</script> It's a story.";
        Game game = games().create("Moon Garden", 2400, "Adventure", "images/aetherbound.png", description);
        assertEquals(description, game.getDescription());
        assertEquals(description, games().findById(game.getId()).getDescription());
        assertEquals(description, games().findAll().stream().filter(item -> game.getId().equals(item.getId())).findFirst().orElseThrow().getDescription());
    }

    @Test
    public void editingAndClearingDescriptionKeepsOtherGameFields() throws Exception {
        Game original = games().create("Moon Garden", 2400, "Adventure", "images/aetherbound.png");
        assertTrue(games().updateDescription(original.getId(), "新しい物語\n次のページへ"));
        Game restored = games().findById(original.getId());
        assertEquals("新しい物語\n次のページへ", restored.getDescription());
        assertEquals(original.getTitle(), restored.getTitle());
        assertEquals(original.getPrice(), restored.getPrice());
        assertEquals(original.getGenre(), restored.getGenre());
        assertEquals(original.getImagePath(), restored.getImagePath());
        assertTrue(games().updateDescription(original.getId(), ""));
        assertEquals("", games().findById(original.getId()).getDescription());
    }

    @Test
    public void descriptionUpdateRejectsInvalidAndMissingIds() throws Exception {
        for (String id : new String[] {null, "0", "-1", "' OR 1=1", "2147483648", "2147483647"}) {
            assertFalse(games().updateDescription(id, "Should not be saved"));
        }
        assertNull(games().findById("7").getDescription());
    }

    @Test
    public void editingFieldsKeepsIdAndSaleStatusAndDescriptionEditDoesNotResume() throws Exception {
        Game game = games().create("Original", 2400, "Adventure", "images/aetherbound.png", "Old");
        assertTrue(game.isActive());
        assertTrue(games().setActive(game.getId(), false));
        assertTrue(games().update(game.getId(), "新しい <b>物語</b>", 3900, "Simulation", "images/moonlit_harvest.png", "新しい説明\n二行目"));
        Game saved = games().findById(game.getId());
        assertEquals(game.getId(), saved.getId());
        assertEquals("新しい <b>物語</b>", saved.getTitle());
        assertEquals(3900, saved.getPrice());
        assertEquals("Simulation", saved.getGenre());
        assertEquals("images/moonlit_harvest.png", saved.getImagePath());
        assertEquals("新しい説明\n二行目", saved.getDescription());
        assertFalse(saved.isActive());
        assertTrue(games().updateDescription(game.getId(), "Description only"));
        assertFalse(games().findById(game.getId()).isActive());
        assertTrue(games().setActive(game.getId(), true));
        assertTrue(games().findById(game.getId()).isActive());
    }

    @Test
    public void migrationCanRepeatWithoutResumingSuspendedGames() throws Exception {
        assertTrue(games().findById("7").isActive());
        Game game = games().create("Paused", 1000, "Adventure", "images/aetherbound.png");
        games().setActive(game.getId(), false);
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute(Files.readString(Path.of("sql/012_add_game_sale_status.sql")));
        }
        assertFalse(games().findById(game.getId()).isActive());
    }

    @Test
    public void editAndStatusRejectMissingOrInvalidIds() throws Exception {
        for (String id : new String[] {null, "0", "' OR 1=1", "2147483648", "2147483647"}) {
            assertFalse(games().setActive(id, false));
            assertFalse(games().update(id, "Missing", 1000, "Adventure", "images/a.png", ""));
        }
        assertTrue(games().findById("7").isActive());
    }

    private void pagedCatalog() throws Exception {
        for (int i=0; i<20; i++) {
            Game game=games().create(String.format(java.util.Locale.ROOT,"Sample %02d",i),1000+(i%4)*100,
                    i%2==0?"Adventure":"Puzzle","images/paged_"+i+".png","Description");
            if(i==19){ games().setActive(game.getId(),false); }
        }
    }

    @Test
    public void pagedCatalogHasNineRowsNoOverlapAndExcludesSuspendedProducts() throws Exception {
        pagedCatalog();
        List<String> ids=new ArrayList<>();
        for(int page=1;page<=3;page++) {
            CatalogPage result=games().findPage("","","added",page,9);
            assertEquals(20,result.catalogCount());assertEquals(20,result.resultCount());assertEquals(3,result.pageCount());
            assertEquals(page==3?2:9,result.games().size());
            assertEquals((page-1)*9+1,result.firstResult());assertEquals(page==3?20:page*9,result.lastResult());
            result.games().forEach(game->{ assertTrue(game.isActive());ids.add(game.getId()); });
        }
        assertEquals(20,new java.util.HashSet<>(ids).size());
        List<String> expected=games().findAll().stream().filter(Game::isActive)
                .sorted(Comparator.comparingInt((Game game)->Integer.parseInt(game.getId())).reversed()).map(Game::getId).toList();
        assertEquals(expected,ids);
    }

    @Test
    public void filtersAndAvailableGenresRemainCorrectAcrossPages() throws Exception {
        pagedCatalog();
        CatalogPage result=games().findPage("sAmPlE","Adventure","price_asc",2,9);
        assertEquals(20,result.catalogCount());assertEquals(10,result.resultCount());assertEquals(2,result.pageCount());
        assertEquals(1,result.games().size());assertEquals(List.of("Adventure","Puzzle"),result.genres());
        assertTrue(result.games().stream().allMatch(game->game.getTitle().startsWith("Sample")&&game.getGenre().equals("Adventure")));
        assertEquals(9,games().findPage("pUZZle","","added",1,9).resultCount());
    }

    @Test
    public void sortingIsStableEvenWhenEqualPricesCrossPageBoundaries() throws Exception {
        pagedCatalog();
        Comparator<Game> newest=Comparator.comparingInt((Game game)->Integer.parseInt(game.getId())).reversed();
        for(String sort:List.of("price_asc","price_desc","title","not-a-sort")) {
            Comparator<Game> order=switch(sort) {
                case "price_asc"->Comparator.comparingInt(Game::getPrice).thenComparing(newest);
                case "price_desc"->Comparator.comparingInt(Game::getPrice).reversed().thenComparing(newest);
                case "title"->Comparator.comparing(Game::getTitle,String.CASE_INSENSITIVE_ORDER).thenComparing(newest);
                default->newest;
            };
            List<String> actual=new ArrayList<>();
            for(int page=1;page<=3;page++){games().findPage("","",sort,page,9).games().forEach(game->actual.add(game.getId()));}
            assertEquals(games().findAll().stream().filter(Game::isActive).sorted(order).map(Game::getId).toList(),actual);
        }
    }

    @Test
    public void outOfRangePagesClampAndEmptyResultsStayOnPageOne() throws Exception {
        pagedCatalog();
        assertEquals(3,games().findPage("","","added",Integer.MAX_VALUE,9).page());
        assertEquals(1,games().findPage("","","added",-20,9).page());
        CatalogPage empty=games().findPage("missing","","added",999,9);
        assertEquals(1,empty.page());assertEquals(1,empty.pageCount());assertEquals(0,empty.firstResult());assertEquals(0,empty.lastResult());
        assertTrue(empty.games().isEmpty());assertEquals(20,empty.catalogCount());
        try{games().findPage("","","added",1,0);fail("Invalid size");}catch(IllegalArgumentException expected){}
    }

    @Test
    public void searchTreatsWildcardsAndSqlTextLiterally() throws Exception {
        Game literal=games().create("100%_Glow",1000,"Puzzle","images/literal.png");
        games().create("100xxGlow",1000,"Puzzle","images/other.png");
        assertEquals(List.of(literal.getId()),games().findPage("%_","","added",1,9).games().stream().map(Game::getId).toList());
        assertEquals(0,games().findPage("' OR 1=1 --","","added",1,9).resultCount());
    }

    @Test
    public void expandedDemoSeedIsRepeatableAndPreservesAdministratorEdits() throws Exception {
        String seed=Files.readString(Path.of("sql/013_expand_demo_catalog.sql"));
        try(Connection conn=connection();Statement stmt=conn.createStatement()){stmt.execute(seed);stmt.execute(seed);}
        assertEquals(7,games().findAll().size());
        Game lantern=games().findAll().stream().filter(game->"Lantern Post".equals(game.getTitle())).findFirst().orElseThrow();
        assertTrue(lantern.getDescription().contains("キツネ"));assertTrue(lantern.isActive());
        games().update(lantern.getId(),"My Lantern",999,"Adventure",lantern.getImagePath(),"Custom description");
        games().setActive(lantern.getId(),false);
        try(Connection conn=connection();Statement stmt=conn.createStatement()){stmt.execute(seed);}
        assertEquals(7,games().findAll().size());
        Game preserved=games().findById(lantern.getId());
        assertEquals(999,preserved.getPrice());assertEquals("Custom description",preserved.getDescription());assertFalse(preserved.isActive());
    }

    @Test
    public void wishlistAddIsRepeatableAndPersistsAcrossRepositoryInstances() throws Exception {
        User user = users().create("wish_alice", "test-wishlist-password");
        assertTrue(wishes().add(user.getId(), 7)); assertTrue(wishes().add(user.getId(), 7));
        assertEquals(java.util.Set.of("7"), wishes().findIdsForUser(user.getId()));
        assertEquals(1, wishes().findPageForUser(user.getId(), 1, 9).resultCount());
        assertFalse(wishes().add(user.getId(), Integer.MAX_VALUE));
        assertEquals(1, wishes().findPageForUser(user.getId(), 1, 9).resultCount());
    }

    @Test
    public void wishlistReadAndDeleteStayWithinMemberEvenForSameGame() throws Exception {
        User alice = users().create("wish_alice", "test-wishlist-password");
        User bob = users().create("wish_bobby", "test-wishlist-password");
        wishes().add(alice.getId(), 7); wishes().add(bob.getId(), 7);
        wishes().remove(alice.getId(), 7); wishes().remove(alice.getId(), 7);
        assertTrue(wishes().findIdsForUser(alice.getId()).isEmpty());
        assertEquals(java.util.Set.of("7"), wishes().findIdsForUser(bob.getId()));
        assertEquals(0, wishes().findPageForUser(alice.getId(), 1, 9).resultCount());
        assertEquals(1, wishes().findPageForUser(bob.getId(), 1, 9).resultCount());
    }

    @Test
    public void wishlistPagesAndSuspendedGamesUseCurrentCatalogData() throws Exception {
        User user = users().create("wish_alice", "test-wishlist-password");
        List<Game> added = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Game game = games().create("Wishlist " + i, 1000 + i, "Adventure", "images/wish_" + i + ".png", "Description");
            added.add(game); wishes().add(user.getId(), Integer.parseInt(game.getId()));
        }
        Game newest = added.get(11);
        games().setActive(newest.getId(), false);
        games().update(newest.getId(), "Updated wishlist title", 3900, "Puzzle", "images/new.png", "New description");
        CatalogPage first = wishes().findPageForUser(user.getId(), 1, 9);
        CatalogPage last = wishes().findPageForUser(user.getId(), 999, 9);
        assertEquals(12, first.resultCount()); assertEquals(9, first.games().size()); assertEquals(3, last.games().size()); assertEquals(2, last.page());
        assertEquals(newest.getId(), first.games().get(0).getId()); assertFalse(first.games().get(0).isActive());
        assertEquals("Updated wishlist title", first.games().get(0).getTitle()); assertEquals(3900, first.games().get(0).getPrice());
        java.util.Set<String> all = new java.util.HashSet<>(); first.games().forEach(game -> all.add(game.getId())); last.games().forEach(game -> all.add(game.getId()));
        assertEquals(12, all.size());
        wishes().remove(user.getId(), Integer.parseInt(newest.getId())); assertEquals(11, wishes().findIdsForUser(user.getId()).size());
    }

    @Test
    public void simultaneousWishlistAddsRemainOneRow() throws Exception {
        User user = users().create("wish_alice", "test-wishlist-password");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Boolean>> jobs = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) { jobs.add(executor.submit(() -> { start.await(); return wishes().add(user.getId(), 7); })); }
            start.countDown(); for (var job : jobs) { assertTrue(job.get(10, java.util.concurrent.TimeUnit.SECONDS)); }
            assertEquals(java.util.Set.of("7"), wishes().findIdsForUser(user.getId()));
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    public void wishlistMigrationRepeatsWithoutLosingSavedItems() throws Exception {
        User user = users().create("wish_alice", "test-wishlist-password"); wishes().add(user.getId(), 7);
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.execute(Files.readString(Path.of("sql/015_create_wishlist.sql"))); }
        assertEquals(java.util.Set.of("7"), wishes().findIdsForUser(user.getId()));
        CatalogPage empty = wishes().findPageForUser(Long.MAX_VALUE, 999, 9);
        assertEquals(1, empty.page()); assertEquals(0, empty.resultCount()); assertTrue(empty.games().isEmpty());
    }

    @Test
    public void deletingDisposableMemberCascadesOnlyItsWishlist() throws Exception {
        User user = users().create("wish_alice", "test-wishlist-password"); wishes().add(user.getId(), 7);
        try (Connection conn = connection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) { stmt.setLong(1, user.getId()); stmt.executeUpdate(); }
        assertTrue(wishes().findIdsForUser(user.getId()).isEmpty()); assertNotNull(games().findById("7"));
    }

    @Test
    public void newlyRegisteredGameCanBeSavedAsAnOrder() throws Exception {
        User user = users().create("fixture_buyer", "long-test-password");
        Game game = games().create("New Adventure", 3200, "Adventure", "images/aetherbound.png");
        String id = UUID.randomUUID().toString();
        orders().save(new Order(id, List.of(game), 3200, user.getId()));
        Order restored = orders().findByIdForUser(id, user.getId());
        assertEquals(game.getId(), restored.getGames().get(0).getId());
        assertEquals(3200, restored.getTotalPrice());
    }

    @Test
    public void sampleSeedIsRepeatableWithoutDuplicates() throws Exception {
        String seed = Files.readString(Path.of("sql", "010_seed_games.sql"));
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute(seed);
            stmt.execute(seed);
        }
        assertEquals(4, games().findAll().size());
        assertEquals(1, games().findAll().stream().filter(game -> "Aetherbound".equals(game.getTitle())).count());
    }

    @Test
    public void administratorSqlRefusesPlaceholderAndPromotesChosenAccount() throws Exception {
        User user = users().create("fixture_admin", "long-test-password");
        String sql = Files.readString(Path.of("sql", "009_promote_admin.sql"));
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            try {
                stmt.execute(sql);
                fail("The unchanged SQL must not promote anyone");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage().contains("CHANGE_ME"));
            }
        }
        assertFalse(users().findById(user.getId()).isAdmin());
        sql = sql.replace("target_username TEXT := 'CHANGE_ME';", "target_username TEXT := 'fixture_admin';");
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) { stmt.execute(sql); }
        assertTrue(users().findById(user.getId()).isAdmin());
    }
}
