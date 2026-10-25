package com.gamestore.repository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.gamestore.model.Order;

public interface OrderRepository {
    OrderChangeResult completeDemoForUser(String orderId, long userId) throws SQLException, IOException, ClassNotFoundException;

    OrderChangeResult cancelPendingForUser(String orderId, long userId) throws SQLException, IOException, ClassNotFoundException;

    void save(Order order) throws SQLException, IOException, ClassNotFoundException;

    String saveOnce(Order order, String checkoutToken) throws SQLException, IOException, ClassNotFoundException;

    String findIdByCheckoutToken(String checkoutToken, long userId) throws SQLException, IOException, ClassNotFoundException;

    Order findByIdForUser(String orderId, long userId)
            throws SQLException, IOException, ClassNotFoundException;

    List<Order> findForUser(long userId, String keyword)
            throws SQLException, IOException, ClassNotFoundException;
}
