package com.gamestore.model;

import java.util.ArrayList;
import java.util.List;

public class Order {
   private String orderId;
   private List<Game> games;
   private int totalPrice;
   
   public String getOrderId() {
       return orderId;
   }

   public List<Game> getGames() {
       return new ArrayList<>(games);
   }

   public int getTotalPrice() {
       return totalPrice;
   }

   public Order(String orderId, List<Game> games, int totalPrice) {
       this.orderId = orderId;
       this.games = new ArrayList<>(games);
       this.totalPrice = totalPrice;
   }
}
