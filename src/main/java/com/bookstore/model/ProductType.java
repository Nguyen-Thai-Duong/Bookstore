package com.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Entity
@Table(name = "ProductType")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ProductTypeID")
    private Long id;

    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @OneToMany(mappedBy = "productType")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Category> categories;
}