package com.gamestore.model;

import java.util.List;

public record AdminOrderPage(List<AdminOrder> orders, int resultCount, long totalPrice, int page, int pageSize) {
    public AdminOrderPage { orders = List.copyOf(orders); }
    public int pageCount() { return Math.max(1, Math.ceilDiv(resultCount, pageSize)); }
    public int firstResult() { return resultCount == 0 ? 0 : (page - 1) * pageSize + 1; }
    public int lastResult() { return resultCount == 0 ? 0 : firstResult() + orders.size() - 1; }
}
