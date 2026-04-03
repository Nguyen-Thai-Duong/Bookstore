package com.bookstore.controller;

import com.bookstore.dto.BookDTO;
import com.bookstore.dto.CategoryDTO;
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
                List<BookDTO> allBooks = bookService.getAllBooks().stream()
                                .map(BookDTO::fromEntity)
                                .toList();
                List<BookDTO> books = bookService.getTopSellingBooksByCategory().stream()
                                .map(BookDTO::fromEntity)
                                .toList();

                Set<Long> categoryIdsWithBooks = allBooks.stream()
                                .map(BookDTO::getCategory)
                                .filter(Objects::nonNull)
                                .map(CategoryDTO::getId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());

                List<CategoryDTO> categories = categoryService.getAllCategories().stream()
                                .map(CategoryDTO::fromEntity)
                                .filter(category -> category != null && category.getId() != null
                                                && categoryIdsWithBooks.contains(category.getId()))
                                .toList();

                model.addAttribute("books", books);
                model.addAttribute("allBooksCount", allBooks.size());
                model.addAttribute("categories", categories);
                return "index";
        }
}
