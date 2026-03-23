package org.example.bookstore.repository;

import org.example.bookstore.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
    List<CartItem> findByCartCartID(int cartId);
    Optional<CartItem> findByCartCartIDAndBookBookID(int cartId, int bookId);
    void deleteByCartCartID(int cartId);
}
