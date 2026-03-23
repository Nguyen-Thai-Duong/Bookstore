package org.example.bookstore.repository;

import org.example.bookstore.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    List<Review> findByBookBookIDOrderByCreatedAtDesc(int bookId);
    Optional<Review> findByBookBookIDAndUserUserID(int bookId, int userId);
    List<Review> findAllByOrderByCreatedAtDesc();
}
