package com.bookstore.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "Review")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    private static final String ADMIN_REPLY_MARKER = "\n\n[ADMIN_REPLY]\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReviewID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false, referencedColumnName = "UserID")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductID", nullable = false, referencedColumnName = "ProductID")
    private Book book;

    @Column(name = "Rating")
    private Integer rating;

    @Column(name = "Comment")
    private String comment;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
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

    @Transient
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

    @Transient
    public boolean hasAdminReply() {
        return getAdminReply() != null;
    }

    public void setUserCommentPreservingAdminReply(String userComment) {
        String normalizedUserComment = userComment == null ? "" : userComment.trim();
        String existingReply = getAdminReply();

        if (existingReply == null) {
            this.comment = normalizedUserComment;
        } else {
            this.comment = normalizedUserComment + ADMIN_REPLY_MARKER + existingReply;
        }
    }

    public void setAdminReply(String adminReply) {
        String normalizedUserComment = getUserComment();
        String normalizedReply = adminReply == null ? "" : adminReply.trim();

        if (normalizedReply.isEmpty()) {
            this.comment = normalizedUserComment;
        } else {
            this.comment = normalizedUserComment + ADMIN_REPLY_MARKER + normalizedReply;
        }
    }
}