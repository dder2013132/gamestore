package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;

import com.gamestore.model.AccountOverview;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcAccountRepository;
import com.gamestore.repository.JdbcUserRepository;
import com.gamestore.util.AuthSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet(urlPatterns = {"/account", "/account/password"})
public class AccountServlet extends HttpServlet {
    private final JdbcAccountRepository accounts;
    private final JdbcUserRepository users;
    public AccountServlet() { this(new JdbcAccountRepository(), new JdbcUserRepository()); }
    AccountServlet(JdbcAccountRepository accounts, JdbcUserRepository users) { this.accounts = accounts; this.users = users; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        User user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        if ("/account/password".equals(request.getServletPath())) { passwordForm(request, response); return; }
        try {
            // Only the authenticated member ID is used, never an ID from the URL or a form.
            AccountOverview overview = accounts.findForUser(user.getId());
            if (overview == null) {
                request.getSession().invalidate();
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
            request.setAttribute("account", overview);
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Member overview retrieval failed", e);
            response.setStatus(503);
            request.setAttribute("error", "マイページを取得できませんでした。しばらくしてから、もう一度お試しください。");
        }
        request.getRequestDispatcher("/WEB-INF/views/account.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!"/account/password".equals(request.getServletPath())) { response.sendError(405); return; }
        User user = AuthSession.currentUser(request);
        if (user == null) { response.sendError(401); return; }
        String current = request.getParameter("currentPassword"), next = request.getParameter("newPassword");
        String confirmation = request.getParameter("passwordConfirm");
        String error = null;
        if (current == null || current.length() < 8 || current.length() > 128) {
            error = "現在のパスワードを正しく入力してください。";
        } else if (next == null || next.length() < 8 || next.length() > 128) {
            error = "新しいパスワードは8〜128文字で入力してください。";
        } else if (!next.equals(confirmation)) {
            error = "新しいパスワードと確認用パスワードが一致しません。";
        } else if (next.equals(current)) {
            error = "現在とは異なるパスワードを入力してください。";
        }
        if (error != null) {
            response.setStatus(400); request.setAttribute("error", error); passwordForm(request, response); return;
        }
        try {
            if (!users.changePassword(user.getId(), current, next)) {
                response.setStatus(400);
                request.setAttribute("error", "現在のパスワードが正しくないか、すでに変更されています。確認して、もう一度入力してください。");
                passwordForm(request, response); return;
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            getServletContext().log("Member password change failed", e);
            response.setStatus(503);
            request.setAttribute("error", "変更を確認できませんでした。時間をおいて、新しいパスワードでログインできるかご確認ください。");
            passwordForm(request, response); return;
        }
        // Rotate this session ID and CSRF token without losing the member's cart.
        AuthSession.login(request, user);
        response.sendRedirect(request.getContextPath() + "/account/password?changed=1");
    }

    private void passwordForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Never copy any submitted password into request attributes, session data or rendered values.
        request.getRequestDispatcher("/WEB-INF/views/account-password.jsp").forward(request, response);
    }
}
