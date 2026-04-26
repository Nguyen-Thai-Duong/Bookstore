package com.bookstore.repository;

import com.bookstore.model.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
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

    long countByUser_IdAndStatusIgnoreCase(Long userId, String status);

    @Query("""
            select o.user.id, count(o)
            from Order o
            where lower(o.status) = lower(:status)
              and o.user.id in :userIds
            group by o.user.id
            """)
    List<Object[]> countOrdersByUserIdsAndStatusIgnoreCase(@Param("userIds") List<Long> userIds,
                                                          @Param("status") String status);

    @Query("""
            select o.user.id, count(o)
            from Order o
            where lower(o.status) = lower(:status)
              and o.user.id in :userIds
              and o.orderDate >= :startDate
            group by o.user.id
            """)
    List<Object[]> countOrdersByUserIdsAndStatusIgnoreCaseAndDateAfter(@Param("userIds") List<Long> userIds,
                                                                      @Param("status") String status,
                                                                      @Param("startDate") LocalDateTime startDate);
}
