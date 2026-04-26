package com.bookstore.controller;

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

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stationery")
@RequiredArgsConstructor
public class StationeryController {

    private static final long STATIONERY_PRODUCT_TYPE_ID = 2L;
    private static final int REVIEWS_PER_PAGE = 4;
    private final BookService bookService;
    private final CategoryService categoryService;
    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;

    @GetMapping
    public String listStationery(@RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String sort,
            Model model) {

        // Lấy tất cả sản phẩm Stationery (ProductTypeID = 2) và chỉ lấy sản phẩm Active
        List<Book> items = bookService.getProductsByProductType(2L).stream()
                .filter(i -> "Active".equalsIgnoreCase(i.getStatus()))
                .collect(Collectors.toList());

        // Filter logic
        if (name != null && !name.isEmpty()) {
            String lowerName = name.toLowerCase();
            items = items.stream()
                    .filter(i -> (i.getTitle() != null && i.getTitle().toLowerCase().contains(lowerName)) ||
                            (i.getAuthor() != null && i.getAuthor().toLowerCase().contains(lowerName)))
                    .collect(Collectors.toList());
        }

        if (categoryId != null) {
            items = items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getId().equals(categoryId))
                    .collect(Collectors.toList());
        }

        if (minPrice != null) {
            items = items.stream()
                    .filter(i -> i.getPrice() != null && i.getPrice().doubleValue() >= minPrice)
                    .collect(Collectors.toList());
        }

        if (maxPrice != null) {
            items = items.stream()
                    .filter(i -> i.getPrice() != null && i.getPrice().doubleValue() <= maxPrice)
                    .collect(Collectors.toList());
        }

        // Xử lý sắp xếp
        if (sort != null) {
            switch (sort) {
                case "name_asc":
                    items.sort(Comparator.comparing(Book::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)));
                    break;
                case "price_asc":
                    items.sort(Comparator.comparing(Book::getPrice, Comparator.nullsLast(Comparator.naturalOrder())));
                    break;
                case "price_desc":
                    items.sort(Comparator.comparing(Book::getPrice, Comparator.nullsLast(Comparator.reverseOrder())));
                    break;
            }
        }

        model.addAttribute("items", items);
        model.addAttribute("categories", categoryService.getCategoriesByProductType(2L));
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

        model.addAttribute("item", item);

        List<Book> relatedItems = bookService.getProductsByProductType(STATIONERY_PRODUCT_TYPE_ID).stream()
                .filter(i -> i.getCategory() != null
                        && item.getCategory() != null
                        && i.getCategory().getId().equals(item.getCategory().getId())
                        && !i.getId().equals(item.getId())
                        && "Active".equalsIgnoreCase(i.getStatus()))
                .limit(4)
                .collect(Collectors.toList());
        model.addAttribute("relatedItems", relatedItems);

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

        List<ReviewDTO> filteredReviews;
        if (normalizedRatingFilter == null) {
            filteredReviews = allReviews;
        } else {
            final int filterValue = normalizedRatingFilter.intValue();
            filteredReviews = allReviews.stream()
                    .filter(r -> r.getRating() != null && r.getRating().intValue() == filterValue)
                    .toList();
        }

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
            long completedPurchaseCount = orderDetailRepository
                    .findByOrder_User_IdAndBook_Id(user.getId(), item.getId())
                    .stream()
                    .filter(detail -> isCompletedStatus(
                            detail.getOrder() != null ? detail.getOrder().getStatus() : null))
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

    @GetMapping("/suggest")
    @ResponseBody
    public List<Map<String, Object>> suggestStationery(@RequestParam(name = "q", required = false) String q) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            return List.of();
        }

        String lowerKeyword = keyword.toLowerCase();
        List<Book> items = bookService.getProductsByProductType(2L).stream()
                .filter(i -> "Active".equalsIgnoreCase(i.getStatus()))
                .filter(i -> (i.getTitle() != null && i.getTitle().toLowerCase().contains(lowerKeyword)) ||
                        (i.getAuthor() != null && i.getAuthor().toLowerCase().contains(lowerKeyword)))
                .limit(8)
                .collect(Collectors.toList());

        return items.stream()
                .map(item -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", item.getId());
                    result.put("title", item.getTitle() == null ? "" : item.getTitle());
                    result.put("author", item.getAuthor() == null ? "" : item.getAuthor());
                    return result;
                })
                .toList();

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
