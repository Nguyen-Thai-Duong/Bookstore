package org.example.bookstore.controller;

import org.example.bookstore.service.BookService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.example.bookstore.model.Book;

@Controller
public class BookController {

    private final BookService bookService;

    // Constructor Injection
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/books")
    public String getBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false, defaultValue = "title_asc") String sort,
            Model model
    ) {
        model.addAttribute("books", bookService.searchBooks(q, categoryId, minPrice, maxPrice, sort));
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("sort", sort);
        return "books-list";
    }

    @GetMapping("/books/{id}")
    public String bookDetail(@PathVariable int id, Model model) {

        Book book = bookService.getBookById(id);
        model.addAttribute("book", book);

        return "book-detail";
    }
}
