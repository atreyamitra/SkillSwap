package com.skillswap.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.skillswap.app.activities.EditProfileActivity;
import com.skillswap.app.activities.FavoritesActivity;
import com.skillswap.app.activities.ManageSkillsActivity;
import com.skillswap.app.databinding.FragmentProfileBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.User;

import java.util.List;
import java.util.Locale;

/**
 * "My Profile" tab: shows the logged in user's own details, skills, and links
 * out to Edit Profile / Manage Skills / Favorites / Log out.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private final UserRepository userRepository = new UserRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.btnManageSkills.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ManageSkillsActivity.class)));
        binding.btnFavorites.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FavoritesActivity.class)));
        binding.btnLogout.setOnClickListener(v -> {
            AuthManager.getInstance().signOut();
            Intent intent = new Intent(requireContext(), com.skillswap.app.activities.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        loadProfile();
    }

    private void loadProfile() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        userRepository.getUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (binding == null || user == null) return;
                binding.tvName.setText(user.getName());
                binding.tvEmail.setText(user.getEmail());
                binding.tvBio.setText(user.getBio() == null || user.getBio().isEmpty()
                        ? "No bio yet. Tap Edit Profile to add one." : user.getBio());
                binding.tvRating.setText(String.format(Locale.getDefault(),
                        "★ %.1f (%d ratings)", user.getAvgRating(), user.getRatingCount()));

                populateChips(binding.chipGroupTeach, user.getSkillsTeach());
                populateChips(binding.chipGroupWant, user.getSkillsWant());
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void populateChips(com.google.android.material.chip.ChipGroup group, List<String> skills) {
        group.removeAllViews();
        if (skills == null || skills.isEmpty()) {
            Chip chip = new Chip(requireContext());
            chip.setText("None added yet");
            chip.setClickable(false);
            group.addView(chip);
            return;
        }
        for (String skill : skills) {
            Chip chip = new Chip(requireContext());
            chip.setText(skill);
            chip.setClickable(false);
            group.addView(chip);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
