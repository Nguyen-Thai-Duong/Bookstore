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

    private String status;

    private Integer publishedYear;
    private String publisher;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
    private BigDecimal thicknessCm;
    private Integer pageCount;

    private CategoryDTO category;
    private Long productTypeId;
    private boolean discontinued;
    private Long cartCleanupRemainingSeconds;

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

                book.getStatus(),

                book.getPublishedYear(),
                book.getPublisher(),
                book.getWidthCm(),
                book.getHeightCm(),
                book.getThicknessCm(),
                book.getPageCount(),

                CategoryDTO.fromEntity(book.getCategory()),
                book.getCategory() != null && book.getCategory().getProductType() != null
                        ? book.getCategory().getProductType().getId()
                        : null,
                book.isDiscontinued(),
                book.getCartCleanupRemainingSeconds()
        );
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

                status,

                publishedYear,
                publisher,
                widthCm,
                heightCm,
                thicknessCm,
                pageCount,

                category == null ? null : category.toEntity()
        );
    }

    public long getCartCleanupRemainingMinutes() {
        if (cartCleanupRemainingSeconds == null || cartCleanupRemainingSeconds <= 0) {
            return 0;
        }

        return (cartCleanupRemainingSeconds + 59) / 60;
    }
}