package com.bookstore.repository;

import com.bookstore.model.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    List<OrderDetail> findByOrderId(Long orderId);

    List<OrderDetail> findByOrder_Id(Long orderId);

    List<OrderDetail> findByBookId(Long bookId);

    List<OrderDetail> findByOrder_User_IdAndBook_Id(Long userId, Long bookId);

    boolean existsByBook_Id(Long bookId);
}
