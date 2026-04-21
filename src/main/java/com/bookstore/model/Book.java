package com.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Entity
@Table(name = "Product")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    public static final String DISCONTINUED_MARKER = "[DISCONTINUED]";
    private static final String DISCONTINUED_AT_PREFIX = "[DISCONTINUED_AT=";
    private static final Duration CART_CLEANUP_DELAY = Duration.ofMinutes(2);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ProductID")
    private Long id;

    @Column(name = "Name", nullable = false, length = 200)
    private String title;

    @Column(name = "Author", length = 100)
    private String author;

    @Column(name = "Price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "StockQuantity")
    private Integer stock = 0;

    @Column(name = "Description", length = 500)
    private String description;

    @Column(name = "ImageURL", length = 255)
    private String imageUrl;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "PublishedYear")
    private Integer publishedYear;

    @Column(name = "Publisher", length = 150)
    private String publisher;

    @Column(name = "WidthCm", precision = 5, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "HeightCm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "ThicknessCm", precision = 5, scale = 2)
    private BigDecimal thicknessCm;

    @Column(name = "PageCount")
    private Integer pageCount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CategoryID", nullable = false, referencedColumnName = "CategoryID")
    private Category category;

    @Transient
    public boolean isDiscontinued() {
        return description != null && description.contains(DISCONTINUED_MARKER);
    }

    @Transient
    public LocalDateTime getDiscontinuedAt() {
        if (description == null) {
            return null;
        }

        int start = description.indexOf(DISCONTINUED_AT_PREFIX);
        if (start < 0) {
            return null;
        }

        int valueStart = start + DISCONTINUED_AT_PREFIX.length();
        int end = description.indexOf(']', valueStart);
        if (end < 0) {
            return null;
        }

        String raw = description.substring(valueStart, end).trim();
        if (raw.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    @Transient
    public long getCartCleanupRemainingSeconds() {
        LocalDateTime discontinuedAt = getDiscontinuedAt();
        if (discontinuedAt == null) {
            return 0;
        }

        LocalDateTime cleanupAt = discontinuedAt.plus(CART_CLEANUP_DELAY);
        long seconds = Duration.between(LocalDateTime.now(), cleanupAt).getSeconds();
        return Math.max(seconds, 0);
    }

    @Transient
    public boolean isCartCleanupWindowActive() {
        return isDiscontinued() && getCartCleanupRemainingSeconds() > 0;
    }

    @Transient
    public boolean isReadyForHardDelete() {
        return isDiscontinued() && !isCartCleanupWindowActive();
    }

    @Transient
    public boolean shouldRemoveFromCartNow() {
        return isDiscontinued() && !isCartCleanupWindowActive();
    }

    public void markDiscontinued() {
        if (isDiscontinued()) {
            stock = 0;
            if (getDiscontinuedAt() == null) {
                appendDiscontinuedTimestamp(LocalDateTime.now());
            }
            return;
        }

        String currentDescription = description == null ? "" : description;
        description = currentDescription + " " + DISCONTINUED_MARKER;
        appendDiscontinuedTimestamp(LocalDateTime.now());
        stock = 0;
    }

    private void appendDiscontinuedTimestamp(LocalDateTime timestamp) {
        if (description == null || description.contains(DISCONTINUED_AT_PREFIX)) {
            return;
        }

        description = description + " " + DISCONTINUED_AT_PREFIX
                + timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "]";
    }
}
