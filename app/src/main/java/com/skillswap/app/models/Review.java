package com.skillswap.app.models;

import com.skillswap.app.exception.InvalidRatingException;

import java.util.Objects;

/**
 * A 1-5 star rating + comment left after a completed swap, stored at reviews/{reviewId}.
 * One review per (swapId, reviewerId) pair is enforced in app logic before writing
 * (see {@code ReviewRepository.hasReviewedSwap}).
 */
public class Review {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    private String reviewId;
    private String reviewerId;
    private String reviewerName;
    private String reviewedUserId;
    private String swapId;
    private int rating;
    private String comment;
    private long timestamp;

    /** Required by Firebase for deserialization; do not call directly. */
    public Review() {
    }

    public Review(String reviewId, String reviewerId, String reviewerName, String reviewedUserId,
                   String swapId, int rating, String comment) {
        this.reviewId = Objects.requireNonNull(reviewId, "reviewId");
        this.reviewerId = Objects.requireNonNull(reviewerId, "reviewerId");
        this.reviewedUserId = Objects.requireNonNull(reviewedUserId, "reviewedUserId");
        if (reviewerId.equals(reviewedUserId)) {
            throw new IllegalArgumentException("A user cannot review themselves");
        }
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new InvalidRatingException(rating);
        }
        this.reviewerName = reviewerName;
        this.swapId = Objects.requireNonNull(swapId, "swapId");
        this.rating = rating;
        this.comment = comment == null ? "" : comment;
        this.timestamp = System.currentTimeMillis();
    }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getReviewedUserId() { return reviewedUserId; }
    public void setReviewedUserId(String reviewedUserId) { this.reviewedUserId = reviewedUserId; }

    public String getSwapId() { return swapId; }
    public void setSwapId(String swapId) { this.swapId = swapId; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Review)) return false;
        Review other = (Review) o;
        return Objects.equals(reviewId, other.reviewId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(reviewId);
    }

    @Override
    public String toString() {
        return "Review{reviewId='" + reviewId + "', rating=" + rating + '}';
    }
}
