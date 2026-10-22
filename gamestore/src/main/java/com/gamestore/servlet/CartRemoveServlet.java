package com.gamestore.servlet;

import java.io.IOException;
import java.util.List;

import com.gamestore.model.Game;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/cart/remove")
public class CartRemoveServlet extends HttpServlet {
    // doPost() 구현
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        String gameId = request.getParameter("gameId");

        if (gameId != null && !gameId.isBlank()) {
            HttpSession session = request.getSession(false);

            if (session != null) {
                @SuppressWarnings("unchecked")
                List<Game> cart = (List<Game>) session.getAttribute("cart");
                if (cart != null) {
                    cart.removeIf(game -> game.getId().equals(gameId));
                    session.setAttribute("cart", cart);
                }
            }
        }
        response.sendRedirect(request.getContextPath() + "/cart");
    }
}