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
   private final Long userId;
   private final OrderStatus status;
   private final LocalDateTime completedAt;
   private final LocalDateTime cancelledAt;

   public OrderStatus getStatus() { return status; }
   public boolean isPending() { return status == OrderStatus.PENDING; }
   public boolean isCompleted() { return status == OrderStatus.COMPLETED; }
   public boolean isCancelled() { return status == OrderStatus.CANCELLED; }
   public LocalDateTime getCompletedAt() { return completedAt; }
   public LocalDateTime getCancelledAt() { return cancelledAt; }
   public String getFormattedCompletedAt() { return format(completedAt); }
   public String getFormattedCancelledAt() { return format(cancelledAt); }
   private static String format(LocalDateTime time) {
       return time == null ? "" : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
   }

   public Long getUserId() {
       return userId;
   }
   
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
       this(orderId, games, totalPrice, null);
   }

   public Order(String orderId, List<Game> games, int totalPrice, Long userId) {
       this(orderId, games, totalPrice, userId, LocalDateTime.now());
   }

   public Order(String orderId, List<Game> games, int totalPrice, Long userId, LocalDateTime createdAt) {
       this(orderId, games, totalPrice, userId, createdAt, OrderStatus.PENDING, null, null);
   }

   public Order(String orderId, List<Game> games, int totalPrice, Long userId, LocalDateTime createdAt,
           OrderStatus status, LocalDateTime completedAt, LocalDateTime cancelledAt) {
       this.orderId = orderId;
       this.games = new ArrayList<>(games);
       this.totalPrice = totalPrice;
       this.createdAt = createdAt;
       this.userId = userId;
       this.status = java.util.Objects.requireNonNull(status);
       this.completedAt = completedAt;
       this.cancelledAt = cancelledAt;
   }
}
