package com;

import java.io.IOException;
import java.sql.SQLException;

import com.gamestore.model.Game;
import com.gamestore.repository.JdbcGameRepository;

public class JdbcGameRepositoryTest {
    public static void main(String[] args) throws SQLException, IOException, ClassNotFoundException {
        JdbcGameRepository repository = new JdbcGameRepository();
        Game game = repository.findById("1");

        System.out.println("Game by ID:");

        if (game != null) {
            // 여기에 게임 제목 출력!
            System.out.println("Title: " + game.getTitle());
        } else {
            System.out.println("Game not found");
        }
    }
}
