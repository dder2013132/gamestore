package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;
import com.gamestore.repository.JdbcWishlistRepository;
import com.gamestore.service.WishlistView;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/home")
public class HomeServlet extends HttpServlet {
    private final JdbcGameRepository gameRepository;
    private final JdbcWishlistRepository wishes;

    public HomeServlet() { this(new JdbcGameRepository()); }
    HomeServlet(JdbcGameRepository gameRepository) { this(gameRepository, new JdbcWishlistRepository()); }
    HomeServlet(JdbcGameRepository gameRepository, JdbcWishlistRepository wishes) { this.gameRepository = gameRepository; this.wishes = wishes; }
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        // Preserve old bookmarked search URLs while keeping home focused on discovery.
        List<String> legacyQuery = new ArrayList<>();
        for (String name : List.of("q", "genre", "sort")) {
            String value = request.getParameter(name);
            if (value != null) {
                legacyQuery.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        if (!legacyQuery.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/browse?" + String.join("&", legacyQuery));
            return;
        }
        List<Game> games;

        try {
            games = gameRepository.findAll().stream().filter(Game::isActive).toList();
            WishlistView.populate(request, wishes);
        } catch (SQLException | ClassNotFoundException e) {
            throw new ServletException("게임 목록 조회 실패", e);
        }

        // Highlight actual catalog products, preferring the cozy covers already prepared for the store.
        List<Game> picks = new ArrayList<>(games);
        picks.sort(java.util.Comparator.comparingInt(game -> String.valueOf(game.getImagePath()).contains("ember_express") ? 0
                : String.valueOf(game.getImagePath()).contains("moonlit_harvest") ? 1 : String.valueOf(game.getImagePath()).contains("aetherbound") ? 2 : 3));
        request.setAttribute("spotlight", picks.isEmpty() ? null : picks.get(0));
        request.setAttribute("picks", picks.stream().limit(3).toList());
        request.setAttribute("catalogCount", games.size());
        request.getRequestDispatcher("/index.jsp")
                .forward(request, response);
    }
}
