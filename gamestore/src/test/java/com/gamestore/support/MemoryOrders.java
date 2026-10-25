package com.gamestore.support;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamestore.model.Order;
import com.gamestore.model.OrderStatus;
import com.gamestore.repository.OrderChangeResult;
import com.gamestore.repository.OrderRepository;

/** Test double only. Production always uses PostgreSQL. */
public class MemoryOrders implements OrderRepository {
    @Override
    public synchronized OrderChangeResult completeDemoForUser(String id, long userId) throws SQLException {
        return transition(id, userId, OrderStatus.COMPLETED);
    }
    @Override
    public synchronized OrderChangeResult cancelPendingForUser(String id, long userId) throws SQLException {
        return transition(id, userId, OrderStatus.CANCELLED);
    }
    private OrderChangeResult transition(String id, long userId, OrderStatus target) throws SQLException {
        Order order = findByIdForUser(id, userId);
        if (order == null) { return OrderChangeResult.NOT_FOUND; }
        if (order.getStatus() == target) { return OrderChangeResult.ALREADY_APPLIED; }
        if (!order.isPending()) { return OrderChangeResult.CONFLICT; }
        var now = java.time.LocalDateTime.now();
        orders.put(id, new Order(id, order.getGames(), order.getTotalPrice(), userId, order.getCreatedAt(), target,
                target == OrderStatus.COMPLETED ? now : null, target == OrderStatus.CANCELLED ? now : null));
        return OrderChangeResult.CHANGED;
    }
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
