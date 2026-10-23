package com.gamestore.repository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.Game;
import com.gamestore.util.DBConnection;

public class JdbcGameRepository {

    public List<Game> findAll() {
        List<Game> games = new ArrayList<>();

        String sql = "SELECT game_id, title, price, genre, image_url FROM games";

try (Connection conn = DBConnection.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql);
     ResultSet rs = stmt.executeQuery()) {

    while (rs.next()) {
        String id = rs.getString("game_id");
        String title = rs.getString("title");
        int price = rs.getInt("price");
        String genre = rs.getString("genre");
        String imagePath = rs.getString("image_url");

        Game game = new Game(id, title, price, genre, imagePath);
        games.add(game);
    }

} catch (SQLException | IOException e) {
    e.printStackTrace();
}

        return games;
    }
}
