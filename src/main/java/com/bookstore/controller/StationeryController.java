package com.bookstore.controller;

import com.bookstore.dto.BookDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/stationery")
@RequiredArgsConstructor
public class StationeryController {

    private static final long STATIONERY_PRODUCT_TYPE_ID = 2L;
    private static final int STATIONERY_PER_PAGE = 12;
    private static final int REVIEWS_PER_PAGE = 4;

    private final BookService bookService;
    private final CategoryService categoryService;
    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;

    @GetMapping
    public String listStationery(@RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            Model model) {
        Long normalizedCategoryId = parseCategoryId(categoryId);
        BigDecimal normalizedMinPrice = parsePrice(minPrice);
        BigDecimal normalizedMaxPrice = parsePrice(maxPrice);

        List<BookDTO> stationeries = filterStationery("", "", normalizedCategoryId, normalizedMinPrice,
                normalizedMaxPrice);
        addPaginationAttributes(model, stationeries, page, null, null, normalizedCategoryId, normalizedMinPrice,
                normalizedMaxPrice);
        model.addAttribute("categories", categoryService.getCategoriesByProductType(STATIONERY_PRODUCT_TYPE_ID));
        model.addAttribute("activePage", "stationery");
        return "user/stationery-list";
    }

    @GetMapping({ "/detail/{id}", "/{id}" })
    public String viewStationeryDetail(@PathVariable Long id,
            @RequestParam(required = false) Integer ratingFilter,
            @RequestParam(defaultValue = "1") int reviewPage,
            HttpSession session,
            Model model) {
        var itemOpt = bookService.getBookById(id);
        if (itemOpt.isEmpty()) {
            return "redirect:/stationery";
        }

        Book item = itemOpt.get();
        if (!isStationeryProduct(item) || !"Active".equalsIgnoreCase(item.getStatus())) {
            return "redirect:/stationery";
        }

        model.addAttribute("item", BookDTO.fromEntity(item));

        Comparator<ReviewDTO> reviewComparator = Comparator
                .comparing((ReviewDTO r) -> r.getRating() == null ? 0 : r.getRating(), Comparator.reverseOrder())
                .thenComparing(ReviewDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        List<ReviewDTO> allReviews = reviewRepository.findByBook_IdOrderByCreatedAtDesc(item.getId()).stream()
                .map(ReviewDTO::fromEntity)
                .sorted(reviewComparator)
                .toList();

        Integer normalizedRatingFilter = (ratingFilter != null && ratingFilter >= 1 && ratingFilter <= 5)
                ? ratingFilter
                : null;

        List<ReviewDTO> filteredReviews = normalizedRatingFilter == null
                ? allReviews
                : allReviews.stream()
                        .filter(r -> r.getRating() != null && r.getRating().intValue() == normalizedRatingFilter)
                        .toList();

        if (!allReviews.isEmpty()) {
            double avgRating = allReviews.stream()
                    .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                    .average()
                    .orElse(0.0);
            model.addAttribute("avgRating", avgRating);
        }

        int filteredReviewCount = filteredReviews.size();
        int totalReviewPages = Math.max(1, (int) Math.ceil((double) filteredReviewCount / REVIEWS_PER_PAGE));
        int currentReviewPage = Math.max(1, Math.min(reviewPage, totalReviewPages));

        int fromIndex = (currentReviewPage - 1) * REVIEWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + REVIEWS_PER_PAGE, filteredReviewCount);
        List<ReviewDTO> paginatedReviews = fromIndex < toIndex ? filteredReviews.subList(fromIndex, toIndex)
                : List.of();

        model.addAttribute("reviews", paginatedReviews);
        model.addAttribute("selectedRatingFilter", normalizedRatingFilter);
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
            long completedPurchaseCount = orderDetailRepository.findByOrder_User_IdAndBook_Id(user.getId(), item.getId())
                    .stream()
                    .filter(detail -> isCompletedStatus(detail.getOrder() != null ? detail.getOrder().getStatus() : null))
                    .count();
            long submittedReviewCount = reviewRepository.countByBook_IdAndUser_Id(item.getId(), user.getId());
            long remainingReviewCount = Math.max(0L, completedPurchaseCount - submittedReviewCount);
            boolean canReview = remainingReviewCount > 0;

            model.addAttribute("canReview", canReview);
            model.addAttribute("remainingReviewCount", remainingReviewCount);

            if (completedPurchaseCount == 0) {
                model.addAttribute("reviewEligibilityMessage", "You can review after buying this product.");
            } else if (!canReview) {
                model.addAttribute("reviewEligibilityMessage",
                        "You have used all review turns for this product. Buy again to review again.");
            }
        }

        model.addAttribute("activePage", "stationery");
        return "user/stationery-detail";
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
        model.addAttribute("categories", categoryService.getCategoriesByProductType(STATIONERY_PRODUCT_TYPE_ID));
        model.addAttribute("activePage", "stationery");
        return "user/stationery-list";
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

    private List<BookDTO> filterStationery(String title, String author, Long categoryId, BigDecimal minPrice,
            BigDecimal maxPrice) {
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase();
        String normalizedAuthor = author == null ? "" : author.trim().toLowerCase();

        return bookService.getProductsByProductType(STATIONERY_PRODUCT_TYPE_ID).stream()
                .filter(this::isStationeryProduct)
                .filter(item -> "Active".equalsIgnoreCase(item.getStatus()))
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
        if (keyword.isEmpty()) {
            return List.of();
        }

        var uniqueItems = Stream.concat(
                bookService.searchBooksByTitle(keyword).stream(),
                bookService.searchBooksByAuthor(keyword).stream())
                .collect(Collectors.toMap(Book::getId, b -> b, (first, ignored) -> first, LinkedHashMap::new))
                .values();

        return uniqueItems.stream()
                .filter(this::isStationeryProduct)
                .filter(item -> "Active".equalsIgnoreCase(item.getStatus()))
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
        return item != null
                && item.getCategory() != null
                && item.getCategory().getProductType() != null
                && Long.valueOf(STATIONERY_PRODUCT_TYPE_ID).equals(item.getCategory().getProductType().getId());
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
