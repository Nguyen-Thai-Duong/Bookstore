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

        private static final long BOOK_PRODUCT_TYPE_ID = 1L;
        private static final long STATIONERY_PRODUCT_TYPE_ID = 2L;
        private final BookService bookService;
        private final CategoryService categoryService;

        @GetMapping("/")
        public String home(Model model) {
                List<BookDTO> featuredBooks = bookService.getTopSellingProductsByProductType(BOOK_PRODUCT_TYPE_ID, 4)
                                .stream()
                                .map(BookDTO::fromEntity)
                                .toList();
                List<BookDTO> featuredStationery = bookService
                                .getTopSellingProductsByProductType(STATIONERY_PRODUCT_TYPE_ID, 4)
                                .stream()
                                .map(BookDTO::fromEntity)
                                .toList();
                List<BookDTO> featuredProducts = java.util.stream.Stream.concat(featuredBooks.stream(),
                                featuredStationery.stream())
                                .toList();

                List<BookDTO> allBooks = bookService.getProductsByProductType(BOOK_PRODUCT_TYPE_ID).stream()
                                .filter(book -> book != null && !book.isDiscontinued())
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

                model.addAttribute("featuredProducts", featuredProducts);
                model.addAttribute("featuredBooks", featuredBooks);
                model.addAttribute("featuredStationery", featuredStationery);
                model.addAttribute("allBooksCount", allBooks.size());
                model.addAttribute("categories", categories);
                return "index";
        }
}
