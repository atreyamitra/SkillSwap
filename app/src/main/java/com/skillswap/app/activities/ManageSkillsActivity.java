package com.skillswap.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.skillswap.app.databinding.ActivityManageSkillsBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.User;
import com.skillswap.app.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Add/remove chips for skillsTeach and skillsWant. Each add/remove writes straight
 * to Firebase so the change is never lost even if the user backs out immediately.
 */
public class ManageSkillsActivity extends AppCompatActivity {

    private ActivityManageSkillsBinding binding;
    private final UserRepository userRepository = new UserRepository();

    private final List<String> teachSkills = new ArrayList<>();
    private final List<String> wantSkills = new ArrayList<>();
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageSkillsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarInclude.tvToolbarTitle.setText("Manage Skills");
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        uid = AuthManager.getInstance().getCurrentUid();

        binding.btnAddTeach.setOnClickListener(v -> {
            String skill = safeText(binding.etTeachSkill.getText());
            if (!ValidationUtils.isNonEmpty(skill)) return;
            if (!containsIgnoreCase(teachSkills, skill)) {
                teachSkills.add(skill);
                renderChips(binding.chipGroupTeach, teachSkills, true);
                persist();
            }
            binding.etTeachSkill.setText("");
        });

        binding.btnAddWant.setOnClickListener(v -> {
            String skill = safeText(binding.etWantSkill.getText());
            if (!ValidationUtils.isNonEmpty(skill)) return;
            if (!containsIgnoreCase(wantSkills, skill)) {
                wantSkills.add(skill);
                renderChips(binding.chipGroupWant, wantSkills, false);
                persist();
            }
            binding.etWantSkill.setText("");
        });

        binding.btnDone.setOnClickListener(v -> finish());

        loadSkills();
    }

    private void loadSkills() {
        if (uid == null) return;
        userRepository.getUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (user == null) return;
                teachSkills.clear();
                if (user.getSkillsTeach() != null) teachSkills.addAll(user.getSkillsTeach());
                wantSkills.clear();
                if (user.getSkillsWant() != null) wantSkills.addAll(user.getSkillsWant());
                renderChips(binding.chipGroupTeach, teachSkills, true);
                renderChips(binding.chipGroupWant, wantSkills, false);
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void renderChips(com.google.android.material.chip.ChipGroup group, List<String> skills, boolean isTeach) {
        group.removeAllViews();
        for (String skill : skills) {
            Chip chip = new Chip(this);
            chip.setText(skill);
            chip.setCloseIconVisible(true);
            chip.setChipBackgroundColorResource(isTeach
                    ? com.skillswap.app.R.color.chip_teach : com.skillswap.app.R.color.chip_want);
            chip.setOnCloseIconClickListener(v -> {
                skills.remove(skill);
                renderChips(group, skills, isTeach);
                persist();
            });
            group.addView(chip);
        }
    }

    private void persist() {
        if (uid == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("skillsTeach", new ArrayList<>(teachSkills));
        updates.put("skillsWant", new ArrayList<>(wantSkills));
        userRepository.updateFields(uid, updates,
                () -> { },
                () -> Snackbar.make(binding.getRoot(), "Failed to save skill changes", Snackbar.LENGTH_SHORT).show());
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private String safeText(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
