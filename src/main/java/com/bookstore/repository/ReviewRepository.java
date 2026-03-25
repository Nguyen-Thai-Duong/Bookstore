package com.bookstore.repository;

import com.bookstore.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByBook_IdOrderByCreatedAtDesc(Long bookId);

    Optional<Review> findByBook_IdAndUser_Id(Long bookId, Long userId);

    List<Review> findAllByOrderByCreatedAtDesc();
}
