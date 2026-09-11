package com.skillswap.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.skillswap.app.databinding.ActivityReviewBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.ReviewRepository;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.Review;
import com.skillswap.app.models.User;

import java.util.HashMap;
import java.util.Map;

/**
 * 1-5 star rating + optional comment for a COMPLETED swap. Prevents a duplicate
 * review for the same swap by checking reviews/{*}/swapId+reviewerId before writing,
 * and recomputes the reviewed user's avgRating/ratingCount afterwards.
 */
public class ReviewActivity extends AppCompatActivity {

    public static final String EXTRA_SWAP_ID = "extra_swap_id";
    public static final String EXTRA_REVIEWED_UID = "extra_reviewed_uid";
    public static final String EXTRA_REVIEWED_NAME = "extra_reviewed_name";

    private ActivityReviewBinding binding;
    private final ReviewRepository reviewRepository = new ReviewRepository();
    private final UserRepository userRepository = new UserRepository();

    private int selectedRating = 0;
    private ImageButton[] stars;

    private String swapId;
    private String reviewedUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        swapId = getIntent().getStringExtra(EXTRA_SWAP_ID);
        reviewedUid = getIntent().getStringExtra(EXTRA_REVIEWED_UID);
        String reviewedName = getIntent().getStringExtra(EXTRA_REVIEWED_NAME);

        binding.toolbarInclude.tvToolbarTitle.setText("Rate & Review");
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());
        binding.tvReviewing.setText("Rate your swap with " + (reviewedName == null ? "your partner" : reviewedName));

        stars = new ImageButton[]{binding.star1, binding.star2, binding.star3, binding.star4, binding.star5};
        for (int i = 0; i < stars.length; i++) {
            int rating = i + 1;
            stars[i].setOnClickListener(v -> setRating(rating));
        }

        binding.btnSubmit.setOnClickListener(v -> submitReview());

        checkAlreadyReviewed();
    }

    private void checkAlreadyReviewed() {
        String currentUid = AuthManager.getInstance().getCurrentUid();
        if (currentUid == null || swapId == null) return;
        reviewRepository.hasReviewedSwap(swapId, currentUid, exists -> {
            if (exists) {
                Snackbar.make(binding.getRoot(), "You already reviewed this swap", Snackbar.LENGTH_LONG).show();
                binding.btnSubmit.setEnabled(false);
            }
        });
    }

    private void setRating(int rating) {
        selectedRating = rating;
        for (int i = 0; i < stars.length; i++) {
            stars[i].setImageResource(i < rating
                    ? com.skillswap.app.R.drawable.ic_star
                    : com.skillswap.app.R.drawable.ic_star_border);
        }
    }

    private void submitReview() {
        String currentUid = AuthManager.getInstance().getCurrentUid();
        if (currentUid == null || swapId == null || reviewedUid == null) return;
        if (selectedRating == 0) {
            Snackbar.make(binding.getRoot(), "Please select a star rating", Snackbar.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        reviewRepository.hasReviewedSwap(swapId, currentUid, exists -> {
            if (exists) {
                setLoading(false);
                Snackbar.make(binding.getRoot(), "You already reviewed this swap", Snackbar.LENGTH_LONG).show();
                return;
            }

            userRepository.getUser(currentUid, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(User reviewer) {
                    String reviewId = reviewRepository.newReviewId();
                    String comment = binding.etComment.getText() == null ? "" : binding.etComment.getText().toString().trim();
                    Review review = new Review(reviewId, currentUid,
                            reviewer == null ? "" : reviewer.getName(), reviewedUid, swapId, selectedRating, comment);

                    reviewRepository.submitReview(review, new ReviewRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            recomputeAverageRating();
                        }

                        @Override
                        public void onError(String message) {
                            setLoading(false);
                            Snackbar.make(binding.getRoot(), "Failed: " + message, Snackbar.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                }
            });
        });
    }

    private void recomputeAverageRating() {
        reviewRepository.getForUserOnce(reviewedUid, new ReviewRepository.ReviewListCallback() {
            @Override
            public void onSuccess(java.util.List<Review> reviews) {
                int count = reviews.size();
                double sum = 0;
                for (Review r : reviews) sum += r.getRating();
                double avg = count == 0 ? 0 : sum / count;

                Map<String, Object> updates = new HashMap<>();
                updates.put("avgRating", avg);
                updates.put("ratingCount", count);
                userRepository.updateFields(reviewedUid, updates,
                        () -> {
                            setLoading(false);
                            Snackbar.make(binding.getRoot(), "Review submitted. Thank you!", Snackbar.LENGTH_SHORT).show();
                            finish();
                        },
                        () -> {
                            setLoading(false);
                            finish();
                        });
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                finish();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSubmit.setEnabled(!loading);
    }
}
