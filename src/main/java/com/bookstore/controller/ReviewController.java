package com.bookstore.controller;

import com.bookstore.dto.ReviewDTO;
import com.bookstore.model.Book;
import com.bookstore.model.OrderDetail;
import com.bookstore.model.Review;
import com.bookstore.model.User;
import com.bookstore.repository.OrderDetailRepository;
import com.bookstore.repository.ReviewRepository;
import com.bookstore.service.AuthService;
import com.bookstore.service.BookService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ReviewController {

    private static final long STATIONERY_PRODUCT_TYPE_ID = 2L;
    private final ReviewRepository reviewRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final BookService bookService;
    private final AuthService authService;

    @PostMapping("/reviews/add")
    public String addReview(@RequestParam Long bookId,
            @RequestParam int rating,
            @RequestParam String content,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        if (bookId == null || bookId <= 0) {
            redirectAttributes.addFlashAttribute("reviewError", "Invalid book");
            return "redirect:/books";
        }

        Optional<Book> bookOpt = bookService.getBookById(bookId);
        if (bookOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Invalid book");
            return "redirect:/books";
        }
        Book book = bookOpt.get();
        String detailRedirectPath = resolveDetailRedirectPath(book);
        String detailReviewAnchorRedirectPath = detailRedirectPath + "#reviews-section";

        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute("reviewError", "Rating must be between 1 and 5");
            return "redirect:" + detailReviewAnchorRedirectPath;
        }

        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Review content cannot be empty");
            return "redirect:" + detailReviewAnchorRedirectPath;
        }
        if (countWords(normalizedContent) > 100) {
            redirectAttributes.addFlashAttribute("reviewError", "Review can be at most 100 words");
            return "redirect:" + detailReviewAnchorRedirectPath;
        }

        long completedPurchaseCount = countCompletedPurchases(user.getId(), bookId);
        if (completedPurchaseCount == 0) {
            redirectAttributes.addFlashAttribute("reviewError", "You need to buy this book before reviewing");
            return "redirect:" + detailReviewAnchorRedirectPath;
        }

        long submittedReviewCount = reviewRepository.countByBook_IdAndUser_Id(bookId, user.getId());
        if (submittedReviewCount >= completedPurchaseCount) {
            redirectAttributes.addFlashAttribute("reviewError",
                    "You already used all review turns for this book. Buy again to review again");
            return "redirect:" + detailReviewAnchorRedirectPath;
        }

        Review review = new Review();
        review.setBook(book);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(normalizedContent);
        review.setCreatedAt(LocalDateTime.now());
        reviewRepository.save(review);

        redirectAttributes.addFlashAttribute("reviewSuccess", "Review submitted");
        return "redirect:" + detailReviewAnchorRedirectPath;
    }

    @PostMapping("/reviews/update")
    public String updateReview(@RequestParam Long reviewId,
            @RequestParam Long bookId,
            @RequestParam int rating,
            @RequestParam String content,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        if (reviewId == null || reviewId <= 0 || bookId == null || bookId <= 0) {
            return "redirect:/books/" + bookId;
        }

        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute("reviewError", "Rating must be between 1 and 5");
            return "redirect:/books/" + bookId;
        }

        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Review content cannot be empty");
            return "redirect:/books/" + bookId;
        }
        if (countWords(normalizedContent) > 100) {
            redirectAttributes.addFlashAttribute("reviewError", "Review can be at most 100 words");
            return "redirect:/books/" + bookId;
        }

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            return "redirect:/books/" + bookId;
        }

        Review review = reviewOpt.get();
        if (!review.getUser().getId().equals(user.getId())) {
            return "redirect:/books/" + bookId;
        }

        review.setRating(rating);
        review.setUserCommentPreservingAdminReply(normalizedContent);
        reviewRepository.save(review);
        redirectAttributes.addFlashAttribute("reviewSuccess", "Review updated");
        return "redirect:/books/" + bookId;
    }

    @PostMapping("/reviews/delete")
    public String deleteReview(@RequestParam Long reviewId,
            @RequestParam Long bookId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        if (reviewId == null || reviewId <= 0 || bookId == null || bookId <= 0) {
            return "redirect:/books/" + bookId;
        }

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            return "redirect:/books/" + bookId;
        }

        Review review = reviewOpt.get();
        if (!review.getUser().getId().equals(user.getId()) && !authService.isAdmin(user)) {
            return "redirect:/books/" + bookId;
        }

        reviewRepository.delete(review);
        redirectAttributes.addFlashAttribute("reviewSuccess", "Review deleted");
        return "redirect:/books/" + bookId;
    }

    @GetMapping("/admin/reviews")
    public String adminReviews(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || !authService.isAdmin(user)) {
            return "redirect:/login";
        }

        List<ReviewDTO> reviews = reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ReviewDTO::fromEntity)
                .toList();
        model.addAttribute("reviews", reviews);
        return "admin/admin-reviews";
    }

    @PostMapping("/admin/reviews/reply")
    public String replyReview(@RequestParam Long reviewId,
            @RequestParam String reply,
            @RequestParam(required = false, defaultValue = "/admin/reviews") String redirectTo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || !authService.isAdmin(user)) {
            return "redirect:/login";
        }

        String safeRedirect = resolveAdminRedirect(redirectTo);

        if (reviewId == null || reviewId <= 0) {
            redirectAttributes.addFlashAttribute("reviewError", "Invalid review");
            return "redirect:" + safeRedirect;
        }

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Review not found");
            return "redirect:" + safeRedirect;
        }

        String normalizedReply = reply == null ? "" : reply.trim();
        if (normalizedReply.length() > 1000) {
            redirectAttributes.addFlashAttribute("reviewError", "Review content too long");
            return "redirect:" + safeRedirect;
        }

        Review review = reviewOpt.get();
        review.setAdminReply(normalizedReply);
        reviewRepository.save(review);

        if (normalizedReply.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewSuccess", "Review deleted");
        } else {
            redirectAttributes.addFlashAttribute("reviewSuccess", "Sent review feedback from");
        }
        return "redirect:" + safeRedirect;
    }

    @PostMapping("/admin/reviews/delete")
    public String deleteReviewAsAdmin(@RequestParam Long reviewId,
            @RequestParam(required = false, defaultValue = "/admin/reviews") String redirectTo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || !authService.isAdmin(user)) {
            return "redirect:/login";
        }

        String safeRedirect = resolveAdminRedirect(redirectTo);
        if (reviewId == null || reviewId <= 0) {
            redirectAttributes.addFlashAttribute("reviewError", "Reviews invalid");
            return "redirect:" + safeRedirect;
        }

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Review not found");
            return "redirect:" + safeRedirect;
        }

        reviewRepository.delete(reviewOpt.get());
        redirectAttributes.addFlashAttribute("reviewSuccess", "Review deleted");
        return "redirect:" + safeRedirect;
    }

    private String resolveAdminRedirect(String redirectTo) {
        if (redirectTo == null || redirectTo.isBlank()) {
            return "/admin/reviews";
        }

        String normalized = redirectTo.trim();
        if (normalized.startsWith("/admin")) {
            return normalized;
        }

        return "/admin/reviews";
    }

    private long countCompletedPurchases(Long userId, Long bookId) {
        return orderDetailRepository.findByOrder_User_IdAndBook_Id(userId, bookId).stream()
                .filter(detail -> isCompletedStatus(detail))
                .count();
    }

    private boolean isCompletedStatus(OrderDetail detail) {
        if (detail == null || detail.getOrder() == null) {
            return false;
        }

        String normalized = normalizeStatus(detail.getOrder().getStatus());
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

    private int countWords(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }

        return content.trim().split("\\s+").length;
    }

    private String resolveDetailRedirectPath(Book book) {
        if (book == null
                || book.getId() == null
                || book.getCategory() == null
                || book.getCategory().getProductType() == null
                || book.getCategory().getProductType().getId() == null) {
            return "/books";
        }

        Long productTypeId = book.getCategory().getProductType().getId();
        if (STATIONERY_PRODUCT_TYPE_ID == productTypeId.longValue()) {
            return "/stationery/detail/" + book.getId();
        }

        return "/books/" + book.getId();
    }
}
