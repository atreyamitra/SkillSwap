package com.skillswap.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.skillswap.app.databinding.ActivityUserDetailBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.FavoritesRepository;
import com.skillswap.app.firebase.SwapRequestRepository;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.SwapRequest;
import com.skillswap.app.models.User;
import com.skillswap.app.utils.DistanceUtils;
import com.skillswap.app.utils.MatchUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows another user's full profile with an explainable match score, and lets the
 * current user send a swap request (offering one of their own taught skills for
 * one of the other user's taught skills).
 */
public class UserDetailActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "extra_uid";

    private ActivityUserDetailBinding binding;
    private final UserRepository userRepository = new UserRepository();
    private final SwapRequestRepository requestRepository = new SwapRequestRepository();
    private final FavoritesRepository favoritesRepository = new FavoritesRepository();

    private User targetUser;
    private User currentUser;
    private boolean isFavorite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarInclude.tvToolbarTitle.setText("Profile");
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        String targetUid = getIntent().getStringExtra(EXTRA_UID);
        if (targetUid == null) {
            finish();
            return;
        }

        binding.btnFavorite.setOnClickListener(v -> toggleFavorite(targetUid));
        binding.btnSendRequest.setOnClickListener(v -> sendRequest(targetUid));

        loadCurrentUser();
        loadTargetUser(targetUid);
        loadFavoriteState(targetUid);
    }

    private void loadCurrentUser() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        userRepository.getUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                populateOfferedDropdown();
                if (targetUser != null) renderMatch();
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void loadTargetUser(String targetUid) {
        userRepository.getUser(targetUid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                targetUser = user;
                if (user == null) return;
                binding.tvName.setText(user.getName());
                binding.tvRating.setText(String.format(Locale.getDefault(),
                        "★ %.1f (%d ratings)", user.getAvgRating(), user.getRatingCount()));
                binding.tvBio.setText(user.getBio() == null || user.getBio().isEmpty()
                        ? "No bio yet" : user.getBio());

                String locationLabel = user.getLocation() == null ? "" : user.getLocation();
                if (currentUser != null && DistanceUtils.hasValidCoordinates(currentUser.getLat(), currentUser.getLng())
                        && DistanceUtils.hasValidCoordinates(user.getLat(), user.getLng())) {
                    double km = DistanceUtils.haversineKm(currentUser.getLat(), currentUser.getLng(),
                            user.getLat(), user.getLng());
                    locationLabel += (locationLabel.isEmpty() ? "" : " · ")
                            + String.format(Locale.getDefault(), "%.1f km away", km);
                }
                binding.tvLocation.setText(locationLabel);

                populateChips(binding.chipGroupTeach, user.getSkillsTeach());
                populateChips(binding.chipGroupWant, user.getSkillsWant());
                populateRequestedDropdown();
                renderMatch();
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void renderMatch() {
        if (currentUser == null || targetUser == null) return;
        MatchUtils.MatchResult result = MatchUtils.computeMatch(
                currentUser.getSkillsTeach(), currentUser.getSkillsWant(),
                targetUser.getSkillsTeach(), targetUser.getSkillsWant());

        StringBuilder explanation = new StringBuilder();
        explanation.append(result.score).append("% match");
        if (!result.theyTeachYouWant.isEmpty()) {
            explanation.append(" — they teach ")
                    .append(android.text.TextUtils.join(", ", result.theyTeachYouWant))
                    .append(" which you want");
        }
        if (!result.youTeachTheyWant.isEmpty()) {
            explanation.append(result.theyTeachYouWant.isEmpty() ? " — " : ", and ")
                    .append("you teach ")
                    .append(android.text.TextUtils.join(", ", result.youTeachTheyWant))
                    .append(" which they want");
        }
        binding.tvMatchExplain.setText(explanation.toString());
    }

    private void populateChips(com.google.android.material.chip.ChipGroup group, List<String> skills) {
        group.removeAllViews();
        if (skills == null || skills.isEmpty()) {
            Chip chip = new Chip(this);
            chip.setText("None listed");
            chip.setClickable(false);
            group.addView(chip);
            return;
        }
        for (String skill : skills) {
            Chip chip = new Chip(this);
            chip.setText(skill);
            chip.setClickable(false);
            group.addView(chip);
        }
    }

    private void populateOfferedDropdown() {
        if (currentUser == null) return;
        List<String> skills = currentUser.getSkillsTeach() == null ? new ArrayList<>() : currentUser.getSkillsTeach();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, skills);
        binding.dropdownOffered.setAdapter(adapter);
    }

    private void populateRequestedDropdown() {
        if (targetUser == null) return;
        List<String> skills = targetUser.getSkillsTeach() == null ? new ArrayList<>() : targetUser.getSkillsTeach();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, skills);
        binding.dropdownRequested.setAdapter(adapter);
    }

    private void loadFavoriteState(String targetUid) {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        favoritesRepository.isFavorite(uid, targetUid, exists -> {
            isFavorite = exists;
            updateFavoriteIcon();
        });
    }

    private void toggleFavorite(String targetUid) {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        isFavorite = !isFavorite;
        favoritesRepository.setFavorite(uid, targetUid, isFavorite);
        updateFavoriteIcon();
    }

    private void updateFavoriteIcon() {
        binding.btnFavorite.setImageResource(isFavorite
                ? com.skillswap.app.R.drawable.ic_favorite_filled
                : com.skillswap.app.R.drawable.ic_favorite_border);
    }

    private void sendRequest(String targetUid) {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null || currentUser == null || targetUser == null) return;

        String offered = binding.dropdownOffered.getText() == null ? "" : binding.dropdownOffered.getText().toString().trim();
        String requested = binding.dropdownRequested.getText() == null ? "" : binding.dropdownRequested.getText().toString().trim();

        if (offered.isEmpty() || requested.isEmpty()) {
            Snackbar.make(binding.getRoot(), "Pick a skill you'll offer and one you want", Snackbar.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        requestRepository.hasExistingActiveRequest(uid, targetUid, exists -> {
            if (exists) {
                setLoading(false);
                Snackbar.make(binding.getRoot(), "You already have an active request with this user", Snackbar.LENGTH_LONG).show();
                return;
            }

            String requestId = requestRepository.newRequestId();
            SwapRequest request = new SwapRequest(requestId, uid, targetUid,
                    currentUser.getName(), targetUser.getName(), offered, requested);
            requestRepository.sendRequest(request, new SwapRequestRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    setLoading(false);
                    Snackbar.make(binding.getRoot(), "Swap request sent!", Snackbar.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    Snackbar.make(binding.getRoot(), "Failed to send request: " + message, Snackbar.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSendRequest.setEnabled(!loading);
    }
}
