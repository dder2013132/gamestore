package com.gamestore;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.gamestore.util.DBConnection;

public class GameSelectTest {

    public static void main(String[] args) {

        String sql = "SELECT game_id, title, price FROM games";

        try (Connection conn = DBConnection.getConnection()) {

            System.out.println("DB connection successful");

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                System.out.println("SQL prepared successfully");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("game_id");
                        String title = rs.getString("title");
                        int price = rs.getInt("price");
                        System.out.println("ID: " + id + ", Title: " + title + ", Price: " + price);
                    }
                }

            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }
}