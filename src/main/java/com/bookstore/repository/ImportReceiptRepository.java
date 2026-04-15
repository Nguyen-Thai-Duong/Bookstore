package com.bookstore.repository;

import com.bookstore.model.ImportReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ImportReceiptRepository extends JpaRepository<ImportReceipt, Long> {

    // Câu lệnh truy vấn để lọc và phân trang tự động
    @Query("SELECT i FROM ImportReceipt i WHERE " +
            "(:search IS NULL OR LOWER(i.username) LIKE LOWER(CONCAT('%', :search, '%')) OR CAST(i.id AS string) LIKE CONCAT('%', :search, '%')) AND " +
            "(:status IS NULL OR CAST(i.status AS string) = :status) AND " +
            "(:minPrice IS NULL OR i.totalAmount >= :minPrice) AND " +
            "(:maxPrice IS NULL OR i.totalAmount <= :maxPrice)")
    Page<ImportReceipt> searchImports(
            @Param("search") String search,
            @Param("status") String status,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);
}