package com.bookstore.repository;

import com.bookstore.model.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByTitleContainingIgnoreCase(String title);

    List<Book> findByAuthorContainingIgnoreCase(String author);

    List<Book> findByCategoryId(Long categoryId);

    List<Book> findByStockGreaterThan(Integer stock);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id in :ids")
    List<Book> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    long countByCategory_Id(Long categoryId);

    // Lọc theo ProductTypeID (1: Book, 2: Stationery)
    @Query("SELECT b FROM Book b WHERE b.category.productType.id = :typeId")
    List<Book> findByProductType(@Param("typeId") Long typeId);
}
