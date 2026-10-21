package com.gamestore.repository;

import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.Game;

public class GameRepository {

    private List<Game> games = new ArrayList<>();

    public List<Game> findAll() {
        return games;
    }

    public Game findById(String id) {
        for (Game game : games) {
            if (game.getId().equals(id)) {
                return game;
            }
        }
        return null;
    }

    public GameRepository() {
        games.add(new Game(
                "G001",
                "Stellar Odyssey",
                7980,
                "RPG",
                "/images/stellar_odyssey.jpg"));

        games.add(new Game(
                "G002",
                "Neon Drift",
                4980,
                "Racing",
                "/images/neon_drift.jpg"));
        
        games.add(new Game(
                "G003",
                "Kingdom of Ash",
                6480,
                "Action",
                "/images/kingdom_of_ash.jpg"));
    }
}