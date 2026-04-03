package com.bookstore.dto;

import com.bookstore.model.Book;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookDTO {

    private Long id;
    private String title;
    private String author;
    private BigDecimal price;
    private Integer stock;
    private String description;
    private String imageUrl;
    private LocalDateTime createdAt;
    private CategoryDTO category;

    public static BookDTO fromEntity(Book book) {
        if (book == null) {
            return null;
        }

        return new BookDTO(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPrice(),
                book.getStock(),
                book.getDescription(),
                book.getImageUrl(),
                book.getCreatedAt(),
                CategoryDTO.fromEntity(book.getCategory()));
    }

    public Book toEntity() {
        return new Book(
                id,
                title,
                author,
                price,
                stock,
                description,
                imageUrl,
                createdAt,
                category == null ? null : category.toEntity());
    }
}