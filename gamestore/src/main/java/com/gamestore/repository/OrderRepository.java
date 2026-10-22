package com.gamestore.repository;

import java.util.ArrayList;
import java.util.List;

import com.gamestore.model.Order;

public class OrderRepository {

    private List<Order> orders = new ArrayList<>();

    public void save(Order order) {
        // 주문 저장
        orders.add(order);
    }

    public Order findById(String orderId) {
        // 주문 ID로 검색
        for (Order order : orders) {
            if (order.getOrderId().equals(orderId)) {
                return order;
            }
        }
        return null;
    }
}
