package com.bookstore.dto;

import com.bookstore.model.Review;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {

    private static final String ADMIN_REPLY_MARKER = "\n\n[ADMIN_REPLY]\n";

    private Long id;
    private UserDTO user;
    private BookDTO book;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;

    public static ReviewDTO fromEntity(Review review) {
        if (review == null) {
            return null;
        }

        return new ReviewDTO(
                review.getId(),
                UserDTO.fromEntity(review.getUser()),
                BookDTO.fromEntity(review.getBook()),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }

    public String getUserComment() {
        if (comment == null) {
            return "";
        }

        int markerIndex = comment.indexOf(ADMIN_REPLY_MARKER);
        if (markerIndex < 0) {
            return comment;
        }

        return comment.substring(0, markerIndex);
    }

    public String getAdminReply() {
        if (comment == null) {
            return null;
        }

        int markerIndex = comment.indexOf(ADMIN_REPLY_MARKER);
        if (markerIndex < 0) {
            return null;
        }

        String reply = comment.substring(markerIndex + ADMIN_REPLY_MARKER.length()).trim();
        return reply.isEmpty() ? null : reply;
    }

    public boolean hasAdminReply() {
        return getAdminReply() != null;
    }
}