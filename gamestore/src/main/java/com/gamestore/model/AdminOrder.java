package com.gamestore.model;

/** Order information exposed only by administrator routes. */
public record AdminOrder(Order order, String username, int itemCount) {
    public Order getOrder() { return order; }
    public String getUsername() { return username; }
    public int getItemCount() { return itemCount; }
}
