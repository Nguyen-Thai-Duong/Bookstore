package com.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "Supplier")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SupplierID")
    private Integer id;

    @Column(name = "SupplierName", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "Phone", length = 20)
    private String phone;

    @Column(name = "Address", length = 255)
    private String address;

    @Column(name = "Status", length = 20)
    private String status = "Active";

    @OneToMany(mappedBy = "supplier")
    private List<PurchaseOrder> purchaseOrders;
}
