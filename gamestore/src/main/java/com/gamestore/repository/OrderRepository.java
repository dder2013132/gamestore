package com.gamestore.repository;

import java.util.ArrayList;
import java.util.Comparator;
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

    public List<Order> searchByOrderId(String keyword) {

        List<Order> result = new ArrayList<>();

        for (Order order : findAll()) {
            if (order.getOrderId().contains(keyword)) {
                result.add(order);
            }
        }

        return result;
    }

    public List<Order> findAll() {
        Comparator<Order> comparator = Comparator.comparing(Order::getCreatedAt);
        List<Order> orderList = new ArrayList<>(orders.values());
        orderList.sort(comparator.reversed());
        return orderList;
    }
}
