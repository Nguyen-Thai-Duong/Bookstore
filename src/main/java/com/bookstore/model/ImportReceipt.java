package com.bookstore.model;

import com.bookstore.model.enums.ImportStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ImportReceipt")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ImportID")
    private Long id;

    @Column(name = "Username", nullable = false)
    private String username; // Tên user WAREHOUSE hoặc ADMIN tạo đơn

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "Status")
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "TotalQuantity")
    private Integer totalQuantity = 0;

    @Column(name = "TotalAmount", precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "importReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImportDetail> details = new ArrayList<>();
}