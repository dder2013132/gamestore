package com.gamestore.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;

/** Refreshes session snapshots without removing unavailable products the customer may wish to inspect. */
public final class CartCatalog {
    private CartCatalog() { }

    public static List<Game> refresh(List<Game> cart, JdbcGameRepository repository)
            throws SQLException, IOException, ClassNotFoundException {
        List<Game> current = new ArrayList<>();
        if (cart == null) { return current; }
        for (Game saved : cart) {
            Game game = repository.findById(saved.getId());
            current.add(game != null ? game : new Game(saved.getId(), saved.getTitle(), saved.getPrice(),
                    saved.getGenre(), saved.getImagePath(), saved.getDescription(), false));
        }
        return current;
    }

    public static boolean blocked(List<Game> cart) {
        return cart.stream().anyMatch(game -> !game.isActive())
                || cart.stream().mapToLong(Game::getPrice).sum() > Integer.MAX_VALUE;
    }
}
