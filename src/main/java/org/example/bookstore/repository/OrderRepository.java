package org.example.bookstore.repository;

import org.example.bookstore.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByUserUserIDOrderByOrderDateDesc(int userId);
    Optional<Order> findByOrderID(int orderId);
    Optional<Order> findByOrderIDAndUserUserID(int orderId, int userId);
}
