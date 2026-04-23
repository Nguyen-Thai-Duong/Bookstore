package com.bookstore.service;

import com.bookstore.model.Book;
import com.bookstore.model.OrderDetail;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.OrderDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Override
    public List<Book> getAllBooks() {
        return bookRepository.findAll().stream()
                .filter(book -> book != null && !book.isDiscontinued())
                .toList();
    }

    @Override
    public List<Book> getProductsByProductType(Long typeId) {
        return bookRepository.findByProductType(typeId);
    }

    @Override
    public Optional<Book> getBookById(Long id) {
        return bookRepository.findById(id);
    }

    @Override
    public List<Book> searchBooksByTitle(String title) {
        return bookRepository.findByTitleContainingIgnoreCase(title).stream()
                .filter(book -> !book.isDiscontinued())
                .toList();
    }

    @Override
    public List<Book> searchBooksByAuthor(String author) {
        return bookRepository.findByAuthorContainingIgnoreCase(author).stream()
                .filter(book -> !book.isDiscontinued())
                .toList();
    }

    @Override
    public List<Book> getBooksByCategory(Long categoryId) {
        return bookRepository.findByCategoryId(categoryId).stream()
                .filter(book -> !book.isDiscontinued())
                .toList();
    }

    @Override
    public List<Book> getAvailableBooks() {
        return bookRepository.findByStockGreaterThan(0).stream()
                .filter(book -> !book.isDiscontinued())
                .toList();
    }

    @Override
    public List<Book> getTopSellingBooksByCategory() {
        List<Book> allBooks = bookRepository.findAll().stream()
                .filter(book -> book != null && !book.isDiscontinued())
                .toList();
        if (allBooks.isEmpty()) {
            return List.of();
        }

        // Sum sold quantity per book, ignoring canceled orders.
        Map<Long, Integer> soldQuantityByBookId = new HashMap<>();
        for (OrderDetail detail : orderDetailRepository.findAll()) {
            if (detail == null || detail.getBook() == null || detail.getBook().getId() == null) {
                continue;
            }

            Integer quantity = detail.getQuantity();
            if (quantity == null || quantity <= 0) {
                continue;
            }

            String status = detail.getOrder() != null && detail.getOrder().getStatus() != null
                    ? detail.getOrder().getStatus().trim().toLowerCase()
                    : "";
            if (status.contains("cancel") || status.contains("huy")) {
                continue;
            }

            soldQuantityByBookId.merge(detail.getBook().getId(), quantity, Integer::sum);
        }

        Map<Long, List<Book>> booksByCategory = allBooks.stream()
                .filter(book -> book.getCategory() != null && book.getCategory().getId() != null)
                .collect(Collectors.groupingBy(book -> book.getCategory().getId()));

        return booksByCategory.values().stream()
                .map(categoryBooks -> categoryBooks.stream()
                        .max(Comparator
                                .comparingInt((Book b) -> soldQuantityByBookId.getOrDefault(b.getId(), 0))
                                .thenComparing(Book::getId))
                        .orElse(null))
                .filter(book -> book != null)
                .sorted(Comparator.comparing(book -> book.getCategory().getId()))
                .toList();
    }

    @Override
    public List<Book> getTopSellingProductsByProductType(Long productTypeId, int limit) {
        if (productTypeId == null || limit <= 0) {
            return List.of();
        }

        List<Book> productsOfType = bookRepository.findByProductType(productTypeId).stream()
                .filter(p -> p != null && !p.isDiscontinued() && "Active".equalsIgnoreCase(p.getStatus()))
                .toList();
        if (productsOfType.isEmpty()) {
            return List.of();
        }

        Set<Long> productIds = productsOfType.stream()
                .map(Book::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> soldQtyByProductId = new HashMap<>();
        for (OrderDetail detail : orderDetailRepository.findAll()) {
            if (detail == null || detail.getBook() == null || detail.getBook().getId() == null) {
                continue;
            }

            Long pid = detail.getBook().getId();
            if (!productIds.contains(pid)) {
                continue;
            }

            Integer quantity = detail.getQuantity();
            if (quantity == null || quantity <= 0) {
                continue;
            }

            String status = detail.getOrder() != null && detail.getOrder().getStatus() != null
                    ? detail.getOrder().getStatus()
                    : "";
            String normalized = status.trim().toLowerCase(Locale.ROOT);
            boolean completed = normalized.contains("completed")
                    || normalized.contains("hoan thanh")
                    || normalized.contains("da giao")
                    || normalized.contains("delivered");
            if (!completed) {
                continue;
            }

            soldQtyByProductId.merge(pid, quantity, Integer::sum);
        }

        return productsOfType.stream()
                .sorted(Comparator
                        .comparingInt((Book p) -> soldQtyByProductId.getOrDefault(p.getId(), 0))
                        .reversed()
                        .thenComparing(Book::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public Book saveBook(Book book) {
        return bookRepository.save(book);
    }

    @Override
    public void deleteBook(Long id) {
        bookRepository.deleteById(id);
    }
}
