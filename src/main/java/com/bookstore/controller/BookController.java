package com.bookstore.controller;

import com.bookstore.dto.BookDTO;
import com.bookstore.dto.CategoryDTO;
import com.bookstore.dto.ReviewDTO;
import com.bookstore.model.Book;
import com.bookstore.model.User;
import com.bookstore.repository.OrderDetailRepository;
import com.bookstore.repository.ReviewRepository;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private static final long BOOK_PRODUCT_TYPE_ID = 1L;
    private final BookService bookService;
    private final CategoryService categoryService;
    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;
    private static final int BOOKS_PER_PAGE = 12;

    @GetMapping
    public String listBooks(@RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            Model model) {
        Long normalizedCategoryId = parseCategoryId(categoryId);
        BigDecimal normalizedMinPrice = parsePrice(minPrice);
        BigDecimal normalizedMaxPrice = parsePrice(maxPrice);
        List<BookDTO> books = filterBooks("", "", normalizedCategoryId, normalizedMinPrice, normalizedMaxPrice);
        addPaginationAttributes(model, books, page, null, null, normalizedCategoryId, normalizedMinPrice,
                normalizedMaxPrice);
        return "user/books/book-list";
    }

    @GetMapping("/{id}")
    public String viewBook(@PathVariable Long id,
            @RequestParam(required = false) Integer ratingFilter,
            @RequestParam(defaultValue = "1") int reviewPage,
            HttpSession session, Model model) {
        var bookOpt = bookService.getBookById(id);
        if (bookOpt.isEmpty()) {
            return "redirect:/books";
        }

        var book = bookOpt.get();
        if (book.isDiscontinued() || !isBookProduct(book)) {
            return "redirect:/books";
        }

        Comparator<ReviewDTO> reviewComparator = Comparator
                .comparing((ReviewDTO r) -> r.getRating() == null ? 0 : r.getRating(), Comparator.reverseOrder())
                .thenComparing(ReviewDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        var allReviews = reviewRepository.findByBook_IdOrderByCreatedAtDesc(book.getId()).stream()
                .map(ReviewDTO::fromEntity)
                .sorted(reviewComparator)
                .toList();

        Integer normalizedRatingFilter = (ratingFilter != null && ratingFilter >= 1 && ratingFilter <= 5)
                ? ratingFilter
                : null;

        // Filter by rating if provided
        List<ReviewDTO> filteredReviews;
        if (normalizedRatingFilter == null) {
            filteredReviews = allReviews;
        } else {
            final int filterValue = normalizedRatingFilter.intValue();
            filteredReviews = allReviews.stream()
                    .filter(r -> r.getRating() != null && r.getRating().intValue() == filterValue)
                    .toList();
        }

        model.addAttribute("book", BookDTO.fromEntity(book));
        model.addAttribute("selectedRatingFilter", normalizedRatingFilter);

        // Calculate average rating from all reviews
        if (!allReviews.isEmpty()) {
            double avgRating = allReviews.stream()
                    .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                    .average()
                    .orElse(0.0);
            model.addAttribute("avgRating", avgRating);
        }

        // Pagination: 4 reviews per page
        final int REVIEWS_PER_PAGE = 4;
        int filteredReviewCount = filteredReviews.size();
        int totalReviewPages = Math.max(1, (int) Math.ceil((double) filteredReviewCount / REVIEWS_PER_PAGE));
        int currentReviewPage = Math.max(1, Math.min(reviewPage, totalReviewPages));

        int fromIndex = (currentReviewPage - 1) * REVIEWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + REVIEWS_PER_PAGE, filteredReviewCount);
        List<ReviewDTO> paginatedReviews = fromIndex < toIndex ? filteredReviews.subList(fromIndex, toIndex)
                : List.of();

        model.addAttribute("reviews", paginatedReviews);
        model.addAttribute("reviewPage", currentReviewPage);
        model.addAttribute("totalReviewPages", totalReviewPages);
        model.addAttribute("hasReviewPrevious", currentReviewPage > 1);
        model.addAttribute("hasReviewNext", currentReviewPage < totalReviewPages);
        model.addAttribute("totalReviewCount", allReviews.size());
        model.addAttribute("filteredReviewCount", filteredReviewCount);

        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            model.addAttribute("canReview", false);
            model.addAttribute("remainingReviewCount", 0L);
            model.addAttribute("reviewEligibilityMessage", "Please log in to write a review.");
        } else {
            long completedPurchaseCount = orderDetailRepository
                    .findByOrder_User_IdAndBook_Id(user.getId(), book.getId())
                    .stream()
                    .filter(detail -> isCompletedStatus(
                            detail.getOrder() != null ? detail.getOrder().getStatus() : null))
                    .count();
            long submittedReviewCount = reviewRepository.countByBook_IdAndUser_Id(book.getId(), user.getId());
            long remainingReviewCount = Math.max(0L, completedPurchaseCount - submittedReviewCount);
            boolean canReview = remainingReviewCount > 0;

            model.addAttribute("canReview", canReview);
            model.addAttribute("remainingReviewCount", remainingReviewCount);

            if (completedPurchaseCount == 0) {
                model.addAttribute("reviewEligibilityMessage", "You can review after buying this book.");
            } else if (!canReview) {
                model.addAttribute("reviewEligibilityMessage",
                        "You have used all review turns for this book. Buy again to review again.");
            }
        }

        return "user/books/book-detail";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("book", new BookDTO());
        model.addAttribute("categories", categoryService.getAllCategories().stream()
                .map(CategoryDTO::fromEntity)
                .toList());
        return "user/books/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(book -> {
            model.addAttribute("book", BookDTO.fromEntity(book));
            model.addAttribute("categories", categoryService.getAllCategories().stream()
                    .map(CategoryDTO::fromEntity)
                    .toList());
        });
        return "user/books/form";
    }

    @PostMapping
    public String saveBook(@ModelAttribute("book") BookDTO bookDto) {
        Book book = bookDto.toEntity();
        if (book.getId() != null) {
            bookService.getBookById(book.getId()).ifPresent(existingBook -> {
                if (book.getCreatedAt() == null) {
                    book.setCreatedAt(existingBook.getCreatedAt());
                }
                if (book.getImageUrl() == null || book.getImageUrl().isBlank()) {
                    book.setImageUrl(existingBook.getImageUrl());
                }
            });
        }
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
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedAuthor = author == null ? "" : author.trim();
        Long normalizedCategoryId = parseCategoryId(categoryId);
        BigDecimal normalizedMinPrice = parsePrice(minPrice);
        BigDecimal normalizedMaxPrice = parsePrice(maxPrice);

        List<BookDTO> filteredBooks = filterBooks(normalizedTitle, normalizedAuthor, normalizedCategoryId,
                normalizedMinPrice,
                normalizedMaxPrice);

        addPaginationAttributes(model, filteredBooks, page,
                normalizedTitle.isBlank() ? null : normalizedTitle,
                normalizedAuthor.isBlank() ? null : normalizedAuthor,
                normalizedCategoryId,
                normalizedMinPrice,
                normalizedMaxPrice);
        return "user/books/book-list";
    }

    private Long parseCategoryId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return parsed.compareTo(BigDecimal.ZERO) < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<BookDTO> filterBooks(String title, String author, Long categoryId, BigDecimal minPrice,
            BigDecimal maxPrice) {
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase();
        String normalizedAuthor = author == null ? "" : author.trim().toLowerCase();

        return bookService.getAllBooks().stream()
                .filter(this::isBookProduct)
                .filter(book -> !book.isDiscontinued())
                .filter(book -> normalizedTitle.isBlank() || (book.getTitle() != null
                        && book.getTitle().toLowerCase().contains(normalizedTitle)))
                .filter(book -> normalizedAuthor.isBlank() || (book.getAuthor() != null
                        && book.getAuthor().toLowerCase().contains(normalizedAuthor)))
                .filter(book -> categoryId == null || (book.getCategory() != null
                        && categoryId.equals(book.getCategory().getId())))
                .filter(book -> minPrice == null || (book.getPrice() != null
                        && book.getPrice().compareTo(minPrice) >= 0))
                .filter(book -> maxPrice == null || (book.getPrice() != null
                        && book.getPrice().compareTo(maxPrice) <= 0))
                .map(BookDTO::fromEntity)
                .toList();
    }

    private void addPaginationAttributes(Model model,
            List<BookDTO> books,
            int requestedPage,
            String title,
            String author,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice) {
        int totalItems = books.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / BOOKS_PER_PAGE));
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int fromIndex = (page - 1) * BOOKS_PER_PAGE;
        int toIndex = Math.min(fromIndex + BOOKS_PER_PAGE, totalItems);
        List<BookDTO> pageItems = fromIndex < toIndex ? books.subList(fromIndex, toIndex) : List.of();

        boolean isSearch = (title != null && !title.isBlank())
                || (author != null && !author.isBlank())
                || categoryId != null
                || minPrice != null
                || maxPrice != null;

        model.addAttribute("books", pageItems);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("hasPrevious", page > 1);
        model.addAttribute("hasNext", page < totalPages);
        model.addAttribute("title", title == null ? "" : title);
        model.addAttribute("author", author == null ? "" : author);
        model.addAttribute("selectedCategoryId", categoryId == null ? "" : categoryId.toString());
        model.addAttribute("minPrice", minPrice == null ? "" : minPrice.stripTrailingZeros().toPlainString());
        model.addAttribute("maxPrice", maxPrice == null ? "" : maxPrice.stripTrailingZeros().toPlainString());
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
                .filter(this::isBookProduct)
                .filter(book -> !book.isDiscontinued())
                .limit(8)
                .map(BookDTO::fromEntity)
                .map(book -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", book.getId());
                    item.put("title", book.getTitle() == null ? "" : book.getTitle());
                    item.put("author", book.getAuthor() == null ? "" : book.getAuthor());
                    item.put("imageUrl", (book.getImageUrl() == null || book.getImageUrl().equals("Noimage")) ? ""
                            : book.getImageUrl());
                    return item;
                })
                .toList();
    }

    private boolean isBookProduct(Book book) {
        return book != null
                && book.getCategory() != null
                && book.getCategory().getProductType() != null
                && Long.valueOf(BOOK_PRODUCT_TYPE_ID).equals(book.getCategory().getProductType().getId());
    }

    private boolean isCompletedStatus(String status) {
        String normalized = normalizeStatus(status);
        return normalized.contains("hoan thanh")
                || normalized.contains("completed")
                || normalized.contains("da giao")
                || normalized.contains("delivered");
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }

        return Normalizer.normalize(status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
