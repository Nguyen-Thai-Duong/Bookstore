package com.bookstore.service;

import com.bookstore.model.Book;
import java.util.List;
import java.util.Optional;

public interface BookService {
    List<Book> getAllBooks();

    List<Book> getProductsByProductType(Long typeId);

    Optional<Book> getBookById(Long id);

    List<Book> searchBooksByTitle(String title);

    List<Book> searchBooksByAuthor(String author);

    List<Book> getBooksByCategory(Long categoryId);

    List<Book> getAvailableBooks();

    List<Book> getTopSellingBooksByCategory();

    List<Book> getTopSellingProductsByProductType(Long productTypeId, int limit);

    Book saveBook(Book book);

    void deleteBook(Long id);
}
