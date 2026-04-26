package com.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ImportDetail")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ImportDetailID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ImportID", nullable = false)
    private Import importOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductID", nullable = false)
    private Book product;

    @Column(name = "OrderedQuantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "ReceivedQuantity")
    private Integer receivedQuantity;

    @Column(name = "UnitPrice", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;
}
