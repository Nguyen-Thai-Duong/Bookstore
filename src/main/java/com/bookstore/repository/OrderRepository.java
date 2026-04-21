package com.bookstore.repository;

import com.bookstore.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    List<Order> findByUser_IdOrderByOrderDateDesc(Long userId);

    Optional<Order> findByIdAndUser_Id(Long id, Long userId);

    List<Order> findByStatus(String status);

    List<Order> findByOrderDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    boolean existsByVoucher_Id(Long voucherId);
}
