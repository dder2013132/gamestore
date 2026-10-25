package com.gamestore.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Member dashboard data; never contains authentication secrets. */
public record AccountOverview(String username, LocalDateTime joinedAt, long orderCount, long wishlistCount, long libraryCount,
        List<Order> recentOrders, List<Game> recentWishes) {
    public AccountOverview {
        recentOrders = List.copyOf(recentOrders);
        recentWishes = List.copyOf(recentWishes);
    }
    public String getUsername() { return username; }
    public String getFormattedJoinedAt() { return joinedAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")); }
    public long getOrderCount() { return orderCount; }
    public long getWishlistCount() { return wishlistCount; }
    public long getLibraryCount() { return libraryCount; }
    public List<Order> getRecentOrders() { return recentOrders; }
    public List<Game> getRecentWishes() { return recentWishes; }
}
