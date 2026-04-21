package com.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Voucher")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VoucherID")
    private Long id;

    @Column(name = "Code", unique = true, length = 50)
    private String code;

    @Column(name = "DiscountPercent")
    private Integer discountPercent;

    @Column(name = "StartDate")
    private LocalDateTime startDate;

    @Column(name = "EndDate")
    private LocalDateTime endDate;

    @Column(name = "Quantity")
    private Integer quantity;

    @Column(name = "Status", length = 20)
    private String status;

    @OneToMany(mappedBy = "voucher")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Order> orders;
}
