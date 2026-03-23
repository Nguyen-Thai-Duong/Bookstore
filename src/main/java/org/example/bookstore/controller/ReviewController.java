package org.example.bookstore.controller;

import jakarta.servlet.http.HttpSession;
import org.example.bookstore.model.Book;
import org.example.bookstore.model.Review;
import org.example.bookstore.model.User;
import org.example.bookstore.repository.ReviewRepository;
import org.example.bookstore.service.BookService;
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
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final BookService bookService;

    public ReviewController(ReviewRepository reviewRepository, BookService bookService) {
        this.reviewRepository = reviewRepository;
        this.bookService = bookService;
    }

    @PostMapping("/reviews/add")
    public String addReview(@RequestParam int bookId,
                            @RequestParam int rating,
                            @RequestParam String content,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (bookId <= 0) {
            redirectAttributes.addFlashAttribute("reviewError", "Sach khong hop le");
            return "redirect:/books";
        }
        Book book = bookService.getBookById(bookId);
        if (book == null) {
            redirectAttributes.addFlashAttribute("reviewError", "Sách không hợp lệ");
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
        Optional<Review> existing = reviewRepository.findByBookBookIDAndUserUserID(bookId, user.getUserID());
        if (existing.isPresent()) {
            redirectAttributes.addFlashAttribute("reviewError", "Bạn đã đánh giá sách này");
            return "redirect:/books/" + bookId;
        }
        Review review = new Review();
        review.setBook(book);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(normalizedContent);
        review.setCreatedAt(LocalDateTime.now());
        reviewRepository.save(review);

        redirectAttributes.addFlashAttribute("reviewSuccess", "Đã gửi đánh giá");
        return "redirect:/books/" + bookId;
    }

    @PostMapping("/reviews/update")
    public String updateReview(@RequestParam int reviewId,
                               @RequestParam int bookId,
                               @RequestParam int rating,
                               @RequestParam String content,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
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
        if (reviewId <= 0 || bookId <= 0) {
            return "redirect:/books/" + bookId;
        }
        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            return "redirect:/books/" + bookId;
        }
        Review review = reviewOpt.get();
        if (review.getUser().getUserID() != user.getUserID()) {
            return "redirect:/books/" + bookId;
        }
        review.setRating(rating);
        review.setComment(normalizedContent);
        reviewRepository.save(review);
        redirectAttributes.addFlashAttribute("reviewSuccess", "Đã cập nhật đánh giá");
        return "redirect:/books/" + bookId;
    }

    @PostMapping("/reviews/delete")
    public String deleteReview(@RequestParam int reviewId,
                               @RequestParam int bookId,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (reviewId <= 0 || bookId <= 0) {
            return "redirect:/books/" + bookId;
        }
        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            return "redirect:/books/" + bookId;
        }
        Review review = reviewOpt.get();
        if (review.getUser().getUserID() != user.getUserID()) {
            return "redirect:/books/" + bookId;
        }
        reviewRepository.delete(review);
        redirectAttributes.addFlashAttribute("reviewSuccess", "Đã xóa đánh giá");
        return "redirect:/books/" + bookId;
    }

    @GetMapping("/admin/reviews")
    public String adminReviews(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || user.getRoleID() != 1) {
            return "redirect:/login";
        }
        List<Review> reviews = reviewRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("reviews", reviews);
        return "admin-reviews";
    }
}
