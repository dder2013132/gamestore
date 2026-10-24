package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import com.gamestore.model.CatalogPage;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcWishlistRepository;
import com.gamestore.service.WishlistView;
import com.gamestore.util.AuthSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet(urlPatterns = {"/wishlist", "/wishlist/add", "/wishlist/remove"})
public class WishlistServlet extends HttpServlet {
    private final JdbcWishlistRepository wishes;
    public WishlistServlet() { this(new JdbcWishlistRepository()); }
    WishlistServlet(JdbcWishlistRepository wishes) { this.wishes = wishes; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!"/wishlist".equals(request.getServletPath())) { response.sendError(405); return; }
        User user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        int page = 1;
        try { page = Math.max(1, Integer.parseInt(request.getParameter("page"))); } catch (NumberFormatException ignored) { }
        try {
            CatalogPage result = wishes.findPageForUser(user.getId(), page, 9);
            WishlistView.populate(request, wishes);
            request.setAttribute("games", result.games());
            request.setAttribute("resultCount", result.resultCount());
            request.setAttribute("pageNumber", result.page());
            request.setAttribute("pageCount", result.pageCount());
            request.setAttribute("firstResult", result.firstResult());
            request.setAttribute("lastResult", result.lastResult());
            request.setAttribute("pageStart", Math.max(1, result.page() - 2));
            request.setAttribute("pageEnd", Math.min(result.pageCount(), result.page() + 2));
            request.setAttribute("paginationPath", "/wishlist");
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Wishlist retrieval failed", e);
            response.setStatus(503);
            request.setAttribute("wishlistError", "お気に入りを取得できませんでした。しばらくしてからもう一度お試しください。");
        }
        request.getRequestDispatcher("/WEB-INF/views/wishlist.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getServletPath();
        if (!"/wishlist/add".equals(path) && !"/wishlist/remove".equals(path)) { response.sendError(405); return; }
        User user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        int id;
        try { id = Integer.parseInt(request.getParameter("gameId")); if (id <= 0) { throw new NumberFormatException(); } }
        catch (NumberFormatException e) { response.sendError(400, "Invalid game ID"); return; }
        try {
            if ("/wishlist/add".equals(path)) {
                if (!wishes.add(user.getId(), id)) { response.sendError(404); return; }
            } else { wishes.remove(user.getId(), id); }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Wishlist update failed", e);
            response.sendError(503, "Unable to update wishlist");
            return;
        }
        response.sendRedirect(request.getContextPath() + WishlistView.safeReturn(request.getParameter("returnTo")));
    }
}
