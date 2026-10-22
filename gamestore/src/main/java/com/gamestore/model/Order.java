package com.gamestore.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Order {
   private final String orderId;
   private final List<Game> games;
   private final int totalPrice;
   private final LocalDateTime createdAt;
   
   public String getOrderId() {
       return orderId;
   }

   public List<Game> getGames() {
       return new ArrayList<>(games);
   }

   public int getTotalPrice() {
       return totalPrice;
   }

   public LocalDateTime getCreatedAt() {
       return createdAt;
   }

   public String getFormattedCreatedAt() {
       DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
       return createdAt.format(formatter);
   }

   public Order(String orderId, List<Game> games, int totalPrice) {
       this.orderId = orderId;
       this.games = new ArrayList<>(games);
       this.totalPrice = totalPrice;
       this.createdAt = LocalDateTime.now();
   }
}
