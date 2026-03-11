package com.bookstore.service;

import com.bookstore.model.Order;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderService {
    List<Order> getAllOrders();

    Optional<Order> getOrderById(Long id);

    List<Order> getOrdersByUserId(Long userId);

    List<Order> getOrdersByStatus(String status);

    List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    Order saveOrder(Order order);

    void deleteOrder(Long id);
}
