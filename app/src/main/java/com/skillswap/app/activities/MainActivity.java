package com.skillswap.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.skillswap.app.databinding.ActivityMainBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.SwapRequestRepository;
import com.skillswap.app.fragments.ChatListFragment;
import com.skillswap.app.fragments.HomeFragment;
import com.skillswap.app.fragments.ProfileFragment;
import com.skillswap.app.fragments.RequestsFragment;
import com.skillswap.app.models.RequestStatus;
import com.skillswap.app.models.SwapRequest;
import com.skillswap.app.utils.NotificationUtils;

/**
 * Hosts the four bottom-navigation destinations. Also owns a lightweight
 * Firebase listener that fires local notifications when a NEW incoming request
 * appears or one of the user's SENT requests gets accepted, while the app is
 * running (see BUILD_NOTES.md for the limits of this approach vs. real push).
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final SwapRequestRepository requestRepository = new SwapRequestRepository();
    private boolean firstIncomingSnapshot = true;
    private boolean firstSentSnapshot = true;
    private final java.util.Set<String> knownIncomingIds = new java.util.HashSet<>();
    private final java.util.Map<String, RequestStatus> knownSentStatuses = new java.util.HashMap<>();

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // no-op either way; notifications are best-effort
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        requestNotificationPermissionIfNeeded();

        if (savedInstanceState == null) {
            showFragment(new HomeFragment());
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == com.skillswap.app.R.id.nav_home) {
                showFragment(new HomeFragment());
                return true;
            } else if (id == com.skillswap.app.R.id.nav_requests) {
                showFragment(new RequestsFragment());
                return true;
            } else if (id == com.skillswap.app.R.id.nav_chats) {
                showFragment(new ChatListFragment());
                return true;
            } else if (id == com.skillswap.app.R.id.nav_profile) {
                showFragment(new ProfileFragment());
                return true;
            }
            return false;
        });

        listenForRequestNotifications();
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(com.skillswap.app.R.id.fragmentContainer, fragment)
                .commit();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void listenForRequestNotifications() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;

        requestRepository.listenIncoming(uid, new SwapRequestRepository.RequestListCallback() {
            @Override
            public void onSuccess(java.util.List<SwapRequest> requests) {
                if (firstIncomingSnapshot) {
                    for (SwapRequest r : requests) knownIncomingIds.add(r.getRequestId());
                    firstIncomingSnapshot = false;
                    return;
                }
                for (SwapRequest r : requests) {
                    if (!knownIncomingIds.contains(r.getRequestId())) {
                        knownIncomingIds.add(r.getRequestId());
                        if (r.getStatus() == RequestStatus.PENDING) {
                            NotificationUtils.notifyIncomingRequest(MainActivity.this,
                                    r.getSenderName(), r.getRequestedSkill());
                        }
                    }
                }
            }

            @Override
            public void onError(String message) {
                // silently ignore; notifications are best-effort
            }
        });

        requestRepository.listenSent(uid, new SwapRequestRepository.RequestListCallback() {
            @Override
            public void onSuccess(java.util.List<SwapRequest> requests) {
                if (firstSentSnapshot) {
                    for (SwapRequest r : requests) knownSentStatuses.put(r.getRequestId(), r.getStatus());
                    firstSentSnapshot = false;
                    return;
                }
                for (SwapRequest r : requests) {
                    RequestStatus previous = knownSentStatuses.get(r.getRequestId());
                    knownSentStatuses.put(r.getRequestId(), r.getStatus());
                    if (previous != null && previous != r.getStatus()
                            && r.getStatus() == RequestStatus.ACCEPTED) {
                        NotificationUtils.notifyRequestAccepted(MainActivity.this, r.getReceiverName());
                    }
                }
            }

            @Override
            public void onError(String message) {
                // silently ignore; notifications are best-effort
            }
        });
    }
}
