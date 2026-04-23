package com.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "PurchaseOrderDetail")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PODetailID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "POID", nullable = false)
    private PurchaseOrder purchaseOrder;

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
