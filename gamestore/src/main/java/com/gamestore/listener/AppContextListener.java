package com.gamestore.listener;

import com.gamestore.repository.OrderRepository;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        OrderRepository repository = new OrderRepository();

        event.getServletContext()
             .setAttribute("orderRepository", repository);
    }
}