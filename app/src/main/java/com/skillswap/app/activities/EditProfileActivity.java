package com.skillswap.app.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.skillswap.app.databinding.ActivityEditProfileBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.User;
import com.skillswap.app.utils.LocationUtils;
import com.skillswap.app.utils.ValidationUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets the user edit name/bio/location, and optionally fetch their device's
 * last known location (guarded behind a runtime permission request).
 */
public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private final UserRepository userRepository = new UserRepository();
    private Double pendingLat;
    private Double pendingLng;

    private final androidx.activity.result.ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean granted = Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_FINE_LOCATION))
                                || Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (granted) {
                            fetchLocation();
                        } else {
                            Snackbar.make(binding.getRoot(), "Location permission denied", Snackbar.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarInclude.tvToolbarTitle.setText("Edit Profile");
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        loadCurrentProfile();

        binding.btnUseLocation.setOnClickListener(v -> {
            if (LocationUtils.hasLocationPermission(this)) {
                fetchLocation();
            } else {
                locationPermissionLauncher.launch(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

        binding.btnSave.setOnClickListener(v -> saveProfile());
    }

    private void loadCurrentProfile() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        userRepository.getUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (user == null) return;
                binding.etName.setText(user.getName());
                binding.etBio.setText(user.getBio());
                binding.etLocation.setText(user.getLocation());
                pendingLat = user.getLat();
                pendingLng = user.getLng();
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void fetchLocation() {
        setLoading(true);
        LocationUtils.getLastKnownLocation(this, new LocationUtils.LocationCallback() {
            @Override
            public void onLocation(double lat, double lng) {
                setLoading(false);
                pendingLat = lat;
                pendingLng = lng;
                Snackbar.make(binding.getRoot(), "Location captured", Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onUnavailable() {
                setLoading(false);
                Snackbar.make(binding.getRoot(), "Could not get location right now", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfile() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;

        String name = safeText(binding.etName.getText());
        if (!ValidationUtils.isNonEmpty(name)) {
            Snackbar.make(binding.getRoot(), "Name cannot be empty", Snackbar.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("bio", safeText(binding.etBio.getText()));
        updates.put("location", safeText(binding.etLocation.getText()));
        if (pendingLat != null) updates.put("lat", pendingLat);
        if (pendingLng != null) updates.put("lng", pendingLng);

        setLoading(true);
        userRepository.updateFields(uid, updates,
                () -> {
                    setLoading(false);
                    Snackbar.make(binding.getRoot(), "Profile updated", Snackbar.LENGTH_SHORT).show();
                    finish();
                },
                () -> {
                    setLoading(false);
                    Snackbar.make(binding.getRoot(), "Failed to save profile", Snackbar.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }

    private String safeText(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
