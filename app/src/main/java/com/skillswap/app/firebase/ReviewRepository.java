package com.skillswap.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.skillswap.app.models.Review;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write helper for reviews/{reviewId}.
 */
public class ReviewRepository {

    private final DatabaseReference reviewsRef;

    public ReviewRepository() {
        reviewsRef = FirebaseDatabase.getInstance().getReference(DatabasePaths.REVIEWS);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface ReviewListCallback {
        void onSuccess(List<Review> reviews);
        void onError(String message);
    }

    public interface ExistsCallback {
        void onResult(boolean exists);
    }

    public String newReviewId() {
        return reviewsRef.push().getKey();
    }

    public void submitReview(Review review, SimpleCallback callback) {
        reviewsRef.child(review.getReviewId()).setValue(review)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Prevents a reviewer from leaving a second review for the same completed swap. */
    public void hasReviewedSwap(String swapId, String reviewerId, ExistsCallback callback) {
        Query q = reviewsRef.orderByChild("swapId").equalTo(swapId);
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean exists = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    Review r = child.getValue(Review.class);
                    if (r != null && reviewerId.equals(r.getReviewerId())) {
                        exists = true;
                        break;
                    }
                }
                callback.onResult(exists);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(false);
            }
        });
    }

    /** One-off read, used e.g. right after submitting a review to recompute an average. */
    public void getForUserOnce(String reviewedUserId, ReviewListCallback callback) {
        Query q = reviewsRef.orderByChild("reviewedUserId").equalTo(reviewedUserId);
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Review> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Review r = child.getValue(Review.class);
                    if (r != null) list.add(r);
                }
                callback.onSuccess(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void listenForUser(String reviewedUserId, ReviewListCallback callback) {
        Query q = reviewsRef.orderByChild("reviewedUserId").equalTo(reviewedUserId);
        q.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Review> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Review r = child.getValue(Review.class);
                    if (r != null) list.add(r);
                }
                callback.onSuccess(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }
}
