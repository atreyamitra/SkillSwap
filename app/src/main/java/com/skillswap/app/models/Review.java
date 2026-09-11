package com.skillswap.app.models;

/**
 * A 1-5 star rating + comment left after a completed swap, stored at reviews/{reviewId}.
 * One review per (swapId, reviewerId) pair is enforced in app logic before writing.
 */
public class Review {

    private String reviewId;
    private String reviewerId;
    private String reviewerName;
    private String reviewedUserId;
    private String swapId;
    private int rating;
    private String comment;
    private long timestamp;

    public Review() {
    }

    public Review(String reviewId, String reviewerId, String reviewerName, String reviewedUserId,
                   String swapId, int rating, String comment) {
        this.reviewId = reviewId;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewedUserId = reviewedUserId;
        this.swapId = swapId;
        this.rating = rating;
        this.comment = comment;
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
}
