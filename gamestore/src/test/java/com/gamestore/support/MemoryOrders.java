package com.gamestore.support;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamestore.model.Order;
import com.gamestore.repository.OrderRepository;

/** Test double only. Production always uses PostgreSQL. */
public class MemoryOrders implements OrderRepository {
    private final Map<String, Order> orders = new HashMap<>();
    private final Map<String, String> tokens = new HashMap<>();
    public int saves;
    public SQLException failure;
    public Order lastSaved;

    @Override
    public void save(Order order) throws SQLException {
        if (failure != null) { throw failure; }
        orders.put(order.getOrderId(), order);
        lastSaved = order;
        saves++;
    }

    @Override
    public synchronized String saveOnce(Order order, String token) throws SQLException {
        String key = order.getUserId() + ":" + token;
        if (tokens.containsKey(key)) { return tokens.get(key); }
        save(order);
        tokens.put(key, order.getOrderId());
        return order.getOrderId();
    }

    @Override
    public synchronized String findIdByCheckoutToken(String token, long userId) throws SQLException {
        if (failure != null) { throw failure; }
        return tokens.get(userId + ":" + token);
    }

    @Override
    public Order findByIdForUser(String id, long userId) throws SQLException {
        if (failure != null) { throw failure; }
        Order order = orders.get(id);
        return order != null && Long.valueOf(userId).equals(order.getUserId()) ? order : null;
    }

    @Override
    public List<Order> findForUser(long userId, String keyword) throws SQLException {
        if (failure != null) { throw failure; }
        List<Order> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (Long.valueOf(userId).equals(order.getUserId())
                    && (keyword == null || keyword.isBlank() || order.getOrderId().contains(keyword))) {
                result.add(order);
            }
        }
        result.sort(Comparator.comparing(Order::getCreatedAt).reversed());
        return result;
    }
}
