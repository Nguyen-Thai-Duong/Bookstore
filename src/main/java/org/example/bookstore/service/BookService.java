package org.example.bookstore.service;

import org.example.bookstore.model.Book;
import org.example.bookstore.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Book getBookById(int id) {
        return bookRepository.findById(id).orElse(null);
    }

    public List<Book> searchBooks(String q, Integer categoryId, Double minPrice, Double maxPrice, String sort) {
        String keyword = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();
        Integer category = (categoryId != null && categoryId > 0) ? categoryId : null;
        Double min = (minPrice != null && minPrice >= 0) ? minPrice : null;
        Double max = (maxPrice != null && maxPrice >= 0) ? maxPrice : null;

        Sort sortOption = switch (sort == null ? "" : sort) {
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            case "newest" -> Sort.by("createdAt").descending();
            default -> Sort.by("title").ascending();
        };

        return bookRepository.searchBooks(keyword, category, min, max, sortOption);
    }
}
