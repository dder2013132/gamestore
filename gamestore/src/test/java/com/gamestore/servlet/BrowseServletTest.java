package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.List;
import com.gamestore.model.Game;
import com.gamestore.model.CatalogPage;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.support.WebFixture;
import jakarta.servlet.ServletException;
import junit.framework.TestCase;

public class BrowseServletTest extends TestCase {
    private static class Games extends JdbcGameRepository {
        CatalogPage result = new CatalogPage(List.of(new Game("1", "Moon", 3000, "Adventure", "images/a.png")),
                List.of("Adventure", "Puzzle"), 12, 1, 1, 9);
        String query, genre, sort;
        int page, size;
        SQLException failure;
        @Override public List<Game> findAll() { throw new AssertionError("Browse must not fetch the full catalog"); }
        @Override public CatalogPage findPage(String query, String genre, String sort, int page, int size) throws SQLException {
            if (failure != null) { throw failure; }
            this.query=query; this.genre=genre; this.sort=sort; this.page=page; this.size=size;
            return result;
        }
    }
    private void render(WebFixture web, Games games) throws Exception {
        BrowseServlet servlet = new BrowseServlet(games);
        servlet.init(web.config);
        servlet.doGet(web.request, web.response);
    }
    public void testDefaultRequestsNineGamesAndForwardsDatabaseResult() throws Exception {
        Games games=new Games(); WebFixture web=new WebFixture();
        render(web,games);
        assertEquals(9,games.size); assertEquals(1,games.page); assertEquals("added",games.sort);
        assertEquals(games.result.games(),web.attributes.get("games"));
        assertEquals(12,web.attributes.get("catalogCount")); assertEquals(1,web.attributes.get("resultCount"));
        assertEquals(false,web.attributes.get("filtering"));
        assertEquals("/WEB-INF/views/browse.jsp",web.forwarded);
    }
    public void testConditionsAreTrimmedAndPassedTogetherWithPage() throws Exception {
        Games games=new Games(); WebFixture web=new WebFixture();
        web.parameters.put("q"," MOON "); web.parameters.put("genre"," Adventure ");
        web.parameters.put("sort"," price_asc "); web.parameters.put("page"," 2 ");
        render(web,games);
        assertEquals("MOON",games.query); assertEquals("Adventure",games.genre);
        assertEquals("price_asc",games.sort); assertEquals(2,games.page);
        assertEquals("MOON",web.attributes.get("query")); assertEquals("Adventure",web.attributes.get("selectedGenre"));
        assertEquals("price_asc",web.attributes.get("sort")); assertEquals(true,web.attributes.get("filtering"));
    }
    public void testInvalidPagesDefaultToFirstAndCannotChangePageSize() throws Exception {
        for(String page:new String[]{"0","-1","abc","999999999999999999999"}) {
            Games games=new Games(); WebFixture web=new WebFixture();
            web.parameters.put("page",page); web.parameters.put("pageSize","999999");
            render(web,games);
            assertEquals(1,games.page); assertEquals(9,games.size);
        }
    }
    public void testSortIsWhitelisted() throws Exception {
        for(String sort:new String[]{"added","price_asc","price_desc","title","price; DROP TABLE games"}) {
            Games games=new Games(); WebFixture web=new WebFixture();web.parameters.put("sort",sort);
            render(web,games);
            assertEquals(sort.contains(";")?"added":sort,games.sort);
        }
    }
    public void testPagerUsesClampedPageAndTotalFromDatabase() throws Exception {
        Games games=new Games();
        games.result=new CatalogPage(List.of(new Game("1","Last",1000,"Puzzle","a.png")),List.of("Puzzle"),19,19,3,9);
        WebFixture web=new WebFixture();web.parameters.put("page","999");
        render(web,games);
        assertEquals(999,games.page); assertEquals(3,web.attributes.get("pageNumber"));
        assertEquals(3,web.attributes.get("pageCount")); assertEquals(19,web.attributes.get("firstResult"));
        assertEquals(19,web.attributes.get("lastResult")); assertEquals(1,web.attributes.get("pageStart"));
        assertEquals(3,web.attributes.get("pageEnd"));
    }
    public void testEmptyResultHasNoInvalidDisplayRange() throws Exception {
        Games games=new Games();games.result=new CatalogPage(List.of(),List.of("Puzzle"),12,0,1,9);
        WebFixture web=new WebFixture();web.parameters.put("q","missing");
        render(web,games);
        assertEquals(0,web.attributes.get("firstResult"));assertEquals(0,web.attributes.get("lastResult"));
        assertEquals(1,web.attributes.get("pageCount"));assertEquals(12,web.attributes.get("catalogCount"));
        assertEquals(List.of("Puzzle"),web.attributes.get("genres"));
    }
    public void testDatabaseFailureIsNotShownAsAnEmptyCatalog() throws Exception {
        Games games=new Games();games.failure=new SQLException("unavailable");
        try {render(new WebFixture(),games);fail("Expected database failure");}
        catch(ServletException expected){assertSame(games.failure,expected.getCause());}
    }
}