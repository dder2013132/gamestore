package com.gamestore.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gamestore.model.Order;

public class OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public boolean save(Order order) {
        Order existing = orders.putIfAbsent(
                order.getOrderId(),
                order);

        return existing == null;
    }

    public Order findById(String orderId) {
        return orders.get(orderId);
    }

    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }
}
