package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import com.gamestore.repository.JdbcLibraryRepository;
import com.gamestore.service.ReviewView;
import com.gamestore.util.AuthSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet("/library")
public class LibraryServlet extends HttpServlet {
    private final JdbcLibraryRepository library;
    public LibraryServlet() { this(new JdbcLibraryRepository()); }
    LibraryServlet(JdbcLibraryRepository library) { this.library = library; }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        var user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        String query = request.getParameter("q"); query = query == null ? "" : query.strip();
        request.setAttribute("query", query);
        if (query.length() > 100) {
            response.setStatus(400); request.setAttribute("libraryError", "検索キーワードは100文字以内で入力してください。");
        } else {
            try { request.setAttribute("library", library.findForUser(user.getId(), query, ReviewView.page(request.getParameter("page")))); }
            catch (SQLException | IOException | ClassNotFoundException e) {
                getServletContext().log("Member library retrieval failed", e); response.setStatus(503);
                request.setAttribute("libraryError", "ライブラリを取得できませんでした。しばらくしてから、もう一度お試しください。");
            }
        }
        request.getRequestDispatcher("/WEB-INF/views/library.jsp").forward(request, response);
    }
}
