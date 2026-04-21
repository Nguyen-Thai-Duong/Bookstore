package com.bookstore.repository;

import com.bookstore.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);

    // Lọc danh mục theo loại sản phẩm
    @Query("SELECT c FROM Category c WHERE c.productType.id = :typeId")
    List<Category> findByProductType(@Param("typeId") Long typeId);
}
