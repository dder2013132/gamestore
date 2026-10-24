package com;

import java.util.List;
import java.util.UUID;

import com.gamestore.model.Game;
import com.gamestore.model.Order;
import com.gamestore.repository.JdbcGameRepository;

public class JdbcOrderRepositoryTest {
    public static void main(String[] args) throws Exception {
        JdbcGameRepository gameRepo = new JdbcGameRepository();
        Game game1 = gameRepo.findById("1");
        Game game2 = gameRepo.findById("2");
        if (game1 == null || game2 == null) {
            throw new IllegalStateException("테스트용 게임을 찾을 수 없습니다.");
        }
        String orderId = UUID.randomUUID().toString();

        List<Game> games = List.of(game1, game2);
        Integer totalPrice = game1.getPrice() + game2.getPrice();
        Order order = new Order(orderId, games, totalPrice);
    }
}
