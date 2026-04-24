package com.bookstore.repository;

import com.bookstore.model.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ImportRepository extends JpaRepository<Import, Integer> {
    List<Import> findByStatus(String status);
    List<Import> findAllByOrderByIdDesc();
}
