package org.example.bookstore.controller;

import jakarta.servlet.http.HttpSession;
import org.example.bookstore.model.Review;
import org.example.bookstore.model.User;
import org.example.bookstore.repository.ReviewRepository;
import org.example.bookstore.service.BookService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.example.bookstore.model.Book;

import java.util.List;

@Controller
public class BookController {

    private final BookService bookService;
    private final ReviewRepository reviewRepository;

    // Constructor Injection
    public BookController(BookService bookService, ReviewRepository reviewRepository) {
        this.bookService = bookService;
        this.reviewRepository = reviewRepository;
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
        String keyword = q == null ? null : q.trim();
        if (keyword != null && keyword.length() > 100) {
            keyword = keyword.substring(0, 100);
        }
        Double safeMin = (minPrice != null && minPrice >= 0) ? minPrice : null;
        Double safeMax = (maxPrice != null && maxPrice >= 0) ? maxPrice : null;

        model.addAttribute("books", bookService.searchBooks(keyword, categoryId, safeMin, safeMax, sort));
        model.addAttribute("q", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("minPrice", safeMin);
        model.addAttribute("maxPrice", safeMax);
        model.addAttribute("sort", sort);
        return "books-list";
    }

    @GetMapping("/books/{id}")
    public String bookDetail(@PathVariable int id, Model model, HttpSession session) {
        if (id <= 0) {
            return "redirect:/books";
        }
        Book book = bookService.getBookById(id);
        if (book == null) {
            return "redirect:/books";
        }
        model.addAttribute("book", book);
        List<Review> reviews = reviewRepository.findByBookBookIDOrderByCreatedAtDesc(id);
        model.addAttribute("reviews", reviews);
        User user = (User) session.getAttribute("loggedInUser");
        if (user != null) {
            Review myReview = reviews.stream()
                    .filter(review -> review.getUser().getUserID() == user.getUserID())
                    .findFirst()
                    .orElse(null);
            model.addAttribute("myReview", myReview);
        }

        return "book-detail";
    }
}
