package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;

import com.gamestore.repository.JdbcUserRepository;
import com.gamestore.util.AuthSession;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    private final JdbcUserRepository users;

    public RegisterServlet() { this(new JdbcUserRepository()); }
    RegisterServlet(JdbcUserRepository users) { this.users = users; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (AuthSession.currentUser(request) != null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        showForm(request, response);
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
        String confirmation = request.getParameter("passwordConfirm");
        request.setAttribute("username", username);
        if (!username.matches("[a-z0-9_]{4,30}")) {
            request.setAttribute("error", "ユーザー名は半角英数字と _ を使い、4〜30文字で入力してください。");
        } else if (password == null || password.length() < 8 || password.length() > 128) {
            request.setAttribute("error", "パスワードは8〜128文字で入力してください。");
        } else if (!password.equals(confirmation)) {
            request.setAttribute("error", "確認用パスワードが一致しません。");
        } else {
            try {
                users.create(username, password);
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    request.setAttribute("error", "このユーザー名はすでに使われています。");
                } else {
                    databaseError(request, response, e);
                }
            } catch (IOException | ClassNotFoundException e) {
                databaseError(request, response, e);
            }
            if (request.getAttribute("error") == null) {
                response.sendRedirect(request.getContextPath() + "/login?registered=1");
                return;
            }
        }
        showForm(request, response);
    }

    private void databaseError(HttpServletRequest request, HttpServletResponse response, Exception e) {
        getServletContext().log("Registration database access failed", e);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        request.setAttribute("error", "現在登録できません。しばらくしてからお試しください。");
    }

    private void showForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(request, response);
    }
}
