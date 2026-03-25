package com.bookstore.controller;

import com.bookstore.model.Book;
import com.bookstore.model.User;
import com.bookstore.repository.ReviewRepository;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final CategoryService categoryService;
    private final ReviewRepository reviewRepository;
    private static final int BOOKS_PER_PAGE = 12;

    @GetMapping
    public String listBooks(@RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String categoryId,
            Model model) {
        Long normalizedCategoryId = parseCategoryId(categoryId);
        List<Book> books = filterBooks("", "", normalizedCategoryId);
        addPaginationAttributes(model, books, page, null, null, normalizedCategoryId);
        return "books/list";
    }

    @GetMapping("/{id}")
    public String viewBook(@PathVariable Long id, HttpSession session, Model model) {
        var bookOpt = bookService.getBookById(id);
        if (bookOpt.isEmpty()) {
            return "redirect:/books";
        }
        
        var book = bookOpt.get();
        var reviews = reviewRepository.findByBook_IdOrderByCreatedAtDesc(book.getId());
        model.addAttribute("book", book);
        model.addAttribute("reviews", reviews);

            // Calculate average rating
            if (!reviews.isEmpty()) {
                double avgRating = reviews.stream()
                        .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                        .average()
                        .orElse(0.0);
                model.addAttribute("avgRating", avgRating);
            }

            User user = (User) session.getAttribute("loggedInUser");
            if (user != null) {
                reviewRepository.findByBook_IdAndUser_Id(book.getId(), user.getId())
                        .ifPresent(review -> model.addAttribute("myReview", review));
            }
        
        return "books/view";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("book", new Book());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "books/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(book -> {
            model.addAttribute("book", book);
            model.addAttribute("categories", categoryService.getAllCategories());
        });
        return "books/form";
    }

    @PostMapping
    public String saveBook(@ModelAttribute Book book) {
        bookService.saveBook(book);
        return "redirect:/books";
    }

    @GetMapping("/delete/{id}")
    public String deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return "redirect:/books";
    }

    @GetMapping("/search")
    public String searchBooks(@RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedAuthor = author == null ? "" : author.trim();
        Long normalizedCategoryId = parseCategoryId(categoryId);

        List<Book> filteredBooks = filterBooks(normalizedTitle, normalizedAuthor, normalizedCategoryId);

        addPaginationAttributes(model, filteredBooks, page,
                normalizedTitle.isBlank() ? null : normalizedTitle,
            normalizedAuthor.isBlank() ? null : normalizedAuthor,
                normalizedCategoryId);
        return "books/list";
    }

    private Long parseCategoryId(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(categoryId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<com.bookstore.model.Category> getCategoriesWithBooks() {
        return bookService.getAllBooks().stream()
                .map(Book::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        com.bookstore.model.Category::getId,
                        category -> category,
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

        private List<Book> filterBooks(String title, String author, Long categoryId) {
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase();
        String normalizedAuthor = author == null ? "" : author.trim().toLowerCase();

        return bookService.getAllBooks().stream()
            .filter(book -> normalizedTitle.isBlank() || (book.getTitle() != null
                && book.getTitle().toLowerCase().contains(normalizedTitle)))
            .filter(book -> normalizedAuthor.isBlank() || (book.getAuthor() != null
                && book.getAuthor().toLowerCase().contains(normalizedAuthor)))
            .filter(book -> categoryId == null || (book.getCategory() != null
                && categoryId.equals(book.getCategory().getId())))
            .toList();
        }

    private void addPaginationAttributes(Model model,
            List<Book> books,
            int requestedPage,
            String title,
            String author,
            Long categoryId) {
        int totalItems = books.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / BOOKS_PER_PAGE));
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int fromIndex = (page - 1) * BOOKS_PER_PAGE;
        int toIndex = Math.min(fromIndex + BOOKS_PER_PAGE, totalItems);
        List<Book> pageItems = fromIndex < toIndex ? books.subList(fromIndex, toIndex) : List.of();

        boolean isSearch = (title != null && !title.isBlank())
            || (author != null && !author.isBlank())
            || categoryId != null;

        model.addAttribute("books", pageItems);
        model.addAttribute("categories", getCategoriesWithBooks());
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("hasPrevious", page > 1);
        model.addAttribute("hasNext", page < totalPages);
        model.addAttribute("title", title == null ? "" : title);
        model.addAttribute("author", author == null ? "" : author);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("isSearch", isSearch);
    }

    @GetMapping("/suggest")
    @ResponseBody
    public List<Map<String, Object>> suggestBooks(@RequestParam(name = "q", required = false) String q) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            return List.of();
        }

        var uniqueBooks = Stream.concat(
                        bookService.searchBooksByTitle(keyword).stream(),
                        bookService.searchBooksByAuthor(keyword).stream())
                .collect(Collectors.toMap(
                        Book::getId,
                        book -> book,
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values();

        return uniqueBooks.stream()
                .limit(8)
            .map(book -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", book.getId());
                item.put("title", book.getTitle() == null ? "" : book.getTitle());
                item.put("author", book.getAuthor() == null ? "" : book.getAuthor());
                return item;
            })
                .toList();
    }
}
