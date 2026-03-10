package org.example.bookstore.repository;

import org.example.bookstore.model.Book;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Integer> {
    @Query("""
            SELECT b FROM Book b
            WHERE (:q IS NULL OR LOWER(b.title) LIKE CONCAT('%', :q, '%') OR LOWER(b.author) LIKE CONCAT('%', :q, '%'))
              AND (:categoryId IS NULL OR b.categoryID = :categoryId)
              AND (:minPrice IS NULL OR b.price >= :minPrice)
              AND (:maxPrice IS NULL OR b.price <= :maxPrice)
            """)
    List<Book> searchBooks(
            @Param("q") String q,
            @Param("categoryId") Integer categoryId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Sort sort
    );
}
