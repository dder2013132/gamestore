package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;

import com.gamestore.model.User;
import com.gamestore.repository.JdbcUserRepository;
import com.gamestore.util.AuthSession;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private final JdbcUserRepository users;

    public LoginServlet() { this(new JdbcUserRepository()); }
    LoginServlet(JdbcUserRepository users) { this.users = users; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AuthSession.currentUser(request) != null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AuthSession.currentUser(request) != null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        String username = JdbcUserRepository.normalizeUsername(request.getParameter("username"));
        String password = request.getParameter("password");
        request.setAttribute("username", username);
        User user = null;
        try {
            if (username.matches("[a-z0-9_]{4,30}") && password != null
                    && password.length() >= 8 && password.length() <= 128) {
                user = users.authenticate(username, password);
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Login database access failed", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("error", "現在ログインできません。しばらくしてからお試しください。");
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            return;
        }
        if (user == null) {
            request.setAttribute("error", "ユーザー名またはパスワードが正しくありません。");
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            return;
        }
        AuthSession.login(request, user);
        response.sendRedirect(request.getContextPath() + AuthSession.takeDestination(request.getSession()));
    }
}
