package com.bookstore.controller;

import com.bookstore.model.Book;
import com.bookstore.model.Review;
import com.bookstore.model.User;
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
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRepository reviewRepository;
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
            redirectAttributes.addFlashAttribute("reviewError", "Sách không hợp lệ");
            return "redirect:/books";
        }

        Optional<Book> bookOpt = bookService.getBookById(bookId);
        if (bookOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Sách không hợp lệ");
            return "redirect:/books";
        }

        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute("reviewError", "Đánh giá phải từ 1 đến 5");
            return "redirect:/books/" + bookId;
        }

        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Nội dung đánh giá không được để trống");
            return "redirect:/books/" + bookId;
        }
        if (normalizedContent.length() > 1000) {
            redirectAttributes.addFlashAttribute("reviewError", "Nội dung đánh giá quá dài");
            return "redirect:/books/" + bookId;
        }

        Optional<Review> existing = reviewRepository.findByBook_IdAndUser_Id(bookId, user.getId());
        if (existing.isPresent()) {
            redirectAttributes.addFlashAttribute("reviewError", "Bạn đã đánh giá sách này");
            return "redirect:/books/" + bookId;
        }

        Review review = new Review();
        review.setBook(bookOpt.get());
        review.setUser(user);
        review.setRating(rating);
        review.setComment(normalizedContent);
        review.setCreatedAt(LocalDateTime.now());
        reviewRepository.save(review);

        redirectAttributes.addFlashAttribute("reviewSuccess", "Đã gửi đánh giá");
        return "redirect:/books/" + bookId;
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
            redirectAttributes.addFlashAttribute("reviewError", "Đánh giá phải từ 1 đến 5");
            return "redirect:/books/" + bookId;
        }

        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Nội dung đánh giá không được để trống");
            return "redirect:/books/" + bookId;
        }
        if (normalizedContent.length() > 1000) {
            redirectAttributes.addFlashAttribute("reviewError", "Nội dung đánh giá quá dài");
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
        redirectAttributes.addFlashAttribute("reviewSuccess", "Đã cập nhật đánh giá");
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
        redirectAttributes.addFlashAttribute("reviewSuccess", "Đã xóa đánh giá");
        return "redirect:/books/" + bookId;
    }

    @GetMapping("/admin/reviews")
    public String adminReviews(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || !authService.isAdmin(user)) {
            return "redirect:/login";
        }

        List<Review> reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("reviews", reviews);
        return "admin-reviews";
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
            redirectAttributes.addFlashAttribute("reviewError", "Đánh giá không hợp lệ");
            return "redirect:" + safeRedirect;
        }

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Không tìm thấy đánh giá");
            return "redirect:" + safeRedirect;
        }

        String normalizedReply = reply == null ? "" : reply.trim();
        if (normalizedReply.length() > 1000) {
            redirectAttributes.addFlashAttribute("reviewError", "Nội dung phản hồi quá dài");
            return "redirect:" + safeRedirect;
        }

        Review review = reviewOpt.get();
        review.setAdminReply(normalizedReply);
        reviewRepository.save(review);

        if (normalizedReply.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewSuccess", "Đã xóa phản hồi");
        } else {
            redirectAttributes.addFlashAttribute("reviewSuccess", "Đã gửi phản hồi cho đánh giá");
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
            redirectAttributes.addFlashAttribute("reviewError", "Đánh giá không hợp lệ");
            return "redirect:" + safeRedirect;
        }

        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("reviewError", "Không tìm thấy đánh giá");
            return "redirect:" + safeRedirect;
        }

        reviewRepository.delete(reviewOpt.get());
        redirectAttributes.addFlashAttribute("reviewSuccess", "Đã xóa đánh giá");
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
}
