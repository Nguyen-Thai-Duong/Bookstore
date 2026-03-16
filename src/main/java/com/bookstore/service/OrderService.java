package com.bookstore.service;

import com.bookstore.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OrderService {
    List<Order> getAllOrders();

    Optional<Order> getOrderById(Long id);

    List<Order> getOrdersByUserId(Long userId);

    List<Order> getOrdersByStatus(String status);

    List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    Order saveOrder(Order order);

    void deleteOrder(Long id);

    // Statistics methods
    Map<String, Object> getRevenueStatistics(String period, LocalDateTime startDate, LocalDateTime endDate);
}
