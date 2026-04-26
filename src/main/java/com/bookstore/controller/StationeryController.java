package com.bookstore.controller;

import com.bookstore.dto.BookDTO;
import com.bookstore.dto.ReviewDTO;
import com.bookstore.model.Book;
import com.bookstore.model.User;
import com.bookstore.repository.ReviewRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/stationery")
@RequiredArgsConstructor
public class StationeryController {

    private static final long STATIONERY_PRODUCT_TYPE_ID = 2L;
    private final BookService bookService;
    private final CategoryService categoryService;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private static final int STATIONERY_PER_PAGE = 12;

    @GetMapping
    public String listStationery(@RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            Model model) {
        Long normalizedCategoryId = parseCategoryId(categoryId);
        BigDecimal normalizedMinPrice = parsePrice(minPrice);
        BigDecimal normalizedMaxPrice = parsePrice(maxPrice);
        List<BookDTO> stationeries = filterStationery("", "", normalizedCategoryId, normalizedMinPrice, normalizedMaxPrice);
        addPaginationAttributes(model, stationeries, page, null, null, normalizedCategoryId, normalizedMinPrice,
                normalizedMaxPrice);
        return "stationery-list";
    }

    @GetMapping("/{id}")
    public String viewStationery(@PathVariable Long id, HttpSession session, Model model) {
        var itemOpt = bookService.getBookById(id);
        if (itemOpt.isEmpty()) {
            return "redirect:/stationery";
        }

        var stationery = itemOpt.get();
        if (stationery.isDiscontinued() || !isStationeryProduct(stationery)) {
            return "redirect:/stationery";
        }

        var reviews = reviewRepository.findByBook_IdOrderByCreatedAtDesc(stationery.getId()).stream()
                .map(ReviewDTO::fromEntity)
                .toList();
        
        model.addAttribute("item", BookDTO.fromEntity(stationery));
        model.addAttribute("reviews", reviews);

        if (!reviews.isEmpty()) {
            double avgRating = reviews.stream()
                    .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                    .average()
                    .orElse(0.0);
            model.addAttribute("avgRating", avgRating);
        }

        User user = (User) session.getAttribute("loggedInUser");
        boolean canReview = false;
        if (user != null) {
            long totalCompletedOrders = orderRepository.countCompletedOrdersByUserIdAndBookId(user.getId(), stationery.getId());
            long totalReviewsDone = reviewRepository.countByBook_IdAndUser_Id(stationery.getId(), user.getId());
            canReview = totalCompletedOrders > totalReviewsDone;
            
            reviewRepository.findByBook_IdAndUser_Id(stationery.getId(), user.getId())
                    .map(ReviewDTO::fromEntity)
                    .ifPresent(review -> model.addAttribute("myReview", review));
        }
        model.addAttribute("canReview", canReview);

        return "stationery-detail";
    }

    @GetMapping("/search")
    public String searchStationery(@RequestParam(required = false) String title,
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

        List<BookDTO> filteredStationery = filterStationery(normalizedTitle, normalizedAuthor, normalizedCategoryId,
                normalizedMinPrice,
                normalizedMaxPrice);

        addPaginationAttributes(model, filteredStationery, page,
                normalizedTitle.isBlank() ? null : normalizedTitle,
                normalizedAuthor.isBlank() ? null : normalizedAuthor,
                normalizedCategoryId,
                normalizedMinPrice,
                normalizedMaxPrice);
        return "stationery-list";
    }

    private Long parseCategoryId(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException ignored) { return null; }
    }

    private BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return parsed.compareTo(BigDecimal.ZERO) < 0 ? null : parsed;
        } catch (NumberFormatException ignored) { return null; }
    }

    private List<BookDTO> filterStationery(String title, String author, Long categoryId, BigDecimal minPrice,
            BigDecimal maxPrice) {
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase();
        String normalizedAuthor = author == null ? "" : author.trim().toLowerCase();

        return bookService.getAllBooks().stream()
                .filter(this::isStationeryProduct)
                .filter(item -> !item.isDiscontinued())
                .filter(item -> normalizedTitle.isBlank() || (item.getTitle() != null
                        && item.getTitle().toLowerCase().contains(normalizedTitle)))
                .filter(item -> normalizedAuthor.isBlank() || (item.getAuthor() != null
                        && item.getAuthor().toLowerCase().contains(normalizedAuthor)))
                .filter(item -> categoryId == null || (item.getCategory() != null
                        && categoryId.equals(item.getCategory().getId())))
                .filter(item -> minPrice == null || (item.getPrice() != null
                        && item.getPrice().compareTo(minPrice) >= 0))
                .filter(item -> maxPrice == null || (item.getPrice() != null
                        && item.getPrice().compareTo(maxPrice) <= 0))
                .map(BookDTO::fromEntity)
                .toList();
    }

    private void addPaginationAttributes(Model model, List<BookDTO> items, int requestedPage,
            String title, String author, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
        int totalItems = items.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / STATIONERY_PER_PAGE));
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int fromIndex = (page - 1) * STATIONERY_PER_PAGE;
        int toIndex = Math.min(fromIndex + STATIONERY_PER_PAGE, totalItems);
        List<BookDTO> pageItems = fromIndex < toIndex ? items.subList(fromIndex, toIndex) : List.of();

        boolean isSearch = (title != null && !title.isBlank()) || (author != null && !author.isBlank())
                || categoryId != null || minPrice != null || maxPrice != null;

        model.addAttribute("items", pageItems);
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
    public List<Map<String, Object>> suggestStationery(@RequestParam(name = "q", required = false) String q) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) return List.of();

        var uniqueItems = Stream.concat(
                bookService.searchBooksByTitle(keyword).stream(),
                bookService.searchBooksByAuthor(keyword).stream())
                .collect(Collectors.toMap(Book::getId, b -> b, (f, i) -> f, LinkedHashMap::new))
                .values();

        return uniqueItems.stream()
                .filter(this::isStationeryProduct)
                .filter(item -> !item.isDiscontinued())
                .limit(8)
                .map(BookDTO::fromEntity)
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", item.getId());
                    map.put("title", item.getTitle() == null ? "" : item.getTitle());
                    map.put("author", item.getAuthor() == null ? "" : item.getAuthor());
                    return map;
                }).toList();
    }

    private boolean isStationeryProduct(Book item) {
        return item != null && item.getCategory() != null && item.getCategory().getProductType() != null
                && Long.valueOf(STATIONERY_PRODUCT_TYPE_ID).equals(item.getCategory().getProductType().getId());
    }
}
