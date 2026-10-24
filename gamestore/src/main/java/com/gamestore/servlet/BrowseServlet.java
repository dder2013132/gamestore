package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import com.gamestore.model.CatalogPage;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.repository.JdbcWishlistRepository;
import com.gamestore.service.WishlistView;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/browse")
public class BrowseServlet extends HttpServlet {
    private static final Set<String> SORT_OPTIONS = Set.of("added", "price_asc", "price_desc", "title");
    private static final int PAGE_SIZE = 9;
    private final JdbcGameRepository games;
    private final JdbcWishlistRepository wishes;
    public BrowseServlet() { this(new JdbcGameRepository()); }
    BrowseServlet(JdbcGameRepository games) { this(games, new JdbcWishlistRepository()); }
    BrowseServlet(JdbcGameRepository games, JdbcWishlistRepository wishes) { this.games = games; this.wishes = wishes; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String query = parameter(request, "q");
        String genre = parameter(request, "genre");
        String requestedSort = parameter(request, "sort");
        String sort = SORT_OPTIONS.contains(requestedSort) ? requestedSort : "added";
        int requestedPage = 1;
        try { requestedPage = Math.max(1, Integer.parseInt(parameter(request, "page"))); }
        catch (NumberFormatException ignored) { }
        CatalogPage result;
        try { result = games.findPage(query, genre, sort, requestedPage, PAGE_SIZE); WishlistView.populate(request, wishes); }
        catch (SQLException | IOException | ClassNotFoundException e) { throw new ServletException("Could not retrieve the game catalog", e); }
        request.setAttribute("games", result.games());
        request.setAttribute("genres", result.genres());
        request.setAttribute("catalogCount", result.catalogCount());
        request.setAttribute("resultCount", result.resultCount());
        request.setAttribute("pageNumber", result.page());
        request.setAttribute("pageCount", result.pageCount());
        request.setAttribute("firstResult", result.firstResult());
        request.setAttribute("lastResult", result.lastResult());
        request.setAttribute("pageStart", Math.max(1, result.page() - 2));
        request.setAttribute("pageEnd", Math.min(result.pageCount(), result.page() + 2));
        request.setAttribute("query", query);
        request.setAttribute("selectedGenre", genre);
        request.setAttribute("sort", sort);
        request.setAttribute("filtering", !query.isEmpty() || !genre.isEmpty());
        request.getRequestDispatcher("/WEB-INF/views/browse.jsp").forward(request, response);
    }
    private String parameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }
}
