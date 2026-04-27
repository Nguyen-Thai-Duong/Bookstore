package com.bookstore.repository;

import com.bookstore.model.ImportDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportDetailRepository extends JpaRepository<ImportDetail, Integer> {
    boolean existsByProduct_Id(Long productId);
}
