package com.bookstore.controller;

import com.bookstore.model.Book;
import com.bookstore.model.Category;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final BookService bookService;
    private final CategoryService categoryService;

    @GetMapping("/")
    public String home(Model model) {
        List<Book> allBooks = bookService.getAllBooks();
        List<Book> books = bookService.getTopSellingBooksByCategory();

        Set<Long> categoryIdsWithBooks = allBooks.stream()
                .map(Book::getCategory)
                .filter(Objects::nonNull)
                .map(Category::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Category> categories = categoryService.getAllCategories().stream()
                .filter(category -> category != null && category.getId() != null
                        && categoryIdsWithBooks.contains(category.getId()))
                .toList();

        model.addAttribute("books", books);
        model.addAttribute("allBooksCount", allBooks.size());
        model.addAttribute("categories", categories);
        return "index";
    }
}
