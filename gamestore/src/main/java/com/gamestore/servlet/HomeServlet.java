package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.gamestore.model.HomePage;
import com.gamestore.repository.JdbcHomeRepository;
import com.gamestore.repository.JdbcWishlistRepository;
import com.gamestore.service.WishlistView;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/home")
public class HomeServlet extends HttpServlet {
    private final JdbcHomeRepository home;
    private final JdbcWishlistRepository wishes;

    public HomeServlet() { this(new JdbcHomeRepository(), new JdbcWishlistRepository()); }
    HomeServlet(JdbcHomeRepository home, JdbcWishlistRepository wishes) { this.home = home; this.wishes = wishes; }
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
        try {
            HomePage result = home.findHome();
            WishlistView.populate(request, wishes);
            request.setAttribute("home", result);
            request.setAttribute("spotlight", result.getSpotlight());
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Home discovery could not be retrieved", e);
            response.setStatus(503);
            request.setAttribute("homeError", "ゲームの情報を取得できませんでした。少し時間をおいて、もう一度お試しください。");
        }
        request.getRequestDispatcher("/index.jsp")
                .forward(request, response);
    }
}
