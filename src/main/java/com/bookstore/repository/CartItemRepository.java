package com.bookstore.repository;

import com.bookstore.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCart_Id(Long cartId);

    Optional<CartItem> findByCart_IdAndBook_Id(Long cartId, Long bookId);

    List<CartItem> findByBook_Id(Long bookId);

    boolean existsByBook_Id(Long bookId);

    long deleteByBook_Id(Long bookId);

    void deleteByCart_Id(Long cartId);
}
