package com.skillswap.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.skillswap.app.activities.UserDetailActivity;
import com.skillswap.app.adapters.UserCardAdapter;
import com.skillswap.app.databinding.FragmentHomeBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.FavoritesRepository;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.User;
import com.skillswap.app.utils.LocationUtils;
import com.skillswap.app.utils.MatchUtils;
import com.skillswap.app.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Discover tab: lists every other registered user with a search box and three
 * filter chips (All / Best Match / Nearby).
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private final UserRepository userRepository = new UserRepository();
    private final FavoritesRepository favoritesRepository = new FavoritesRepository();
    private UserCardAdapter adapter;

    private List<User> allUsers = new ArrayList<>();
    private User currentUser;
    private Double currentLat;
    private Double currentLng;
    private int selectedFilter = 0; // 0 all, 1 best match, 2 nearby
    private PrefsManager prefsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefsManager = new PrefsManager(requireContext());

        adapter = new UserCardAdapter(new UserCardAdapter.Listener() {
            @Override
            public void onCardClick(User user) {
                Intent intent = new Intent(requireContext(), UserDetailActivity.class);
                intent.putExtra(UserDetailActivity.EXTRA_UID, user.getUid());
                startActivity(intent);
            }

            @Override
            public void onFavoriteClick(User user, boolean nowFavorite) {
                String uid = AuthManager.getInstance().getCurrentUid();
                if (uid == null) return;
                favoritesRepository.setFavorite(uid, user.getUid(), nowFavorite);
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(this::loadUsers);

        binding.etSearch.setText(prefsManager.getLastFilter());
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefsManager.setLastFilter(s.toString());
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        binding.filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedFilter = 0;
            } else if (checkedIds.get(0) == binding.chipBestMatch.getId()) {
                selectedFilter = 1;
            } else if (checkedIds.get(0) == binding.chipNearby.getId()) {
                selectedFilter = 2;
            } else {
                selectedFilter = 0;
            }
            applyFilters();
        });

        loadCurrentUserAndLocation();
        loadUsers();
        listenFavorites();
    }

    private void loadCurrentUserAndLocation() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        userRepository.getUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                if (adapter != null) adapter.setCurrentUser(user);
                if (user != null && com.skillswap.app.utils.DistanceUtils.hasValidCoordinates(user.getLat(), user.getLng())) {
                    currentLat = user.getLat();
                    currentLng = user.getLng();
                    if (adapter != null) adapter.setCurrentLocation(currentLat, currentLng);
                }
                applyFilters();
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void listenFavorites() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        favoritesRepository.listenFavoriteIds(uid, ids -> {
            if (adapter != null) adapter.setFavoriteIds(ids);
        });
    }

    private void loadUsers() {
        setLoading(true);
        userRepository.getAllUsers(new UserRepository.UserListCallback() {
            @Override
            public void onSuccess(List<User> users) {
                setLoading(false);
                binding.swipeRefresh.setRefreshing(false);
                String myUid = AuthManager.getInstance().getCurrentUid();
                allUsers = new ArrayList<>();
                for (User u : users) {
                    if (myUid == null || !myUid.equals(u.getUid())) {
                        allUsers.add(u);
                    }
                }
                applyFilters();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                binding.swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void applyFilters() {
        if (binding == null) return;
        String query = binding.etSearch.getText() == null ? "" :
                binding.etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

        List<User> filtered = new ArrayList<>();
        for (User u : allUsers) {
            if (query.isEmpty() || matchesQuery(u, query)) {
                filtered.add(u);
            }
        }

        if (selectedFilter == 1 && currentUser != null) {
            filtered.sort((a, b) -> {
                int scoreA = MatchUtils.computeMatch(currentUser.getSkillsTeach(), currentUser.getSkillsWant(),
                        a.getSkillsTeach(), a.getSkillsWant()).score;
                int scoreB = MatchUtils.computeMatch(currentUser.getSkillsTeach(), currentUser.getSkillsWant(),
                        b.getSkillsTeach(), b.getSkillsWant()).score;
                return Integer.compare(scoreB, scoreA);
            });
        } else if (selectedFilter == 2 && currentLat != null && currentLng != null) {
            List<User> withLocation = new ArrayList<>();
            for (User u : filtered) {
                if (com.skillswap.app.utils.DistanceUtils.hasValidCoordinates(u.getLat(), u.getLng())) {
                    withLocation.add(u);
                }
            }
            withLocation.sort(Comparator.comparingDouble(u ->
                    com.skillswap.app.utils.DistanceUtils.haversineKm(currentLat, currentLng, u.getLat(), u.getLng())));
            filtered = withLocation;
        }

        adapter.submitList(filtered);
        binding.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesQuery(User u, String query) {
        if (u.getName() != null && u.getName().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (containsSkill(u.getSkillsTeach(), query)) return true;
        return containsSkill(u.getSkillsWant(), query);
    }

    private boolean containsSkill(List<String> skills, String query) {
        if (skills == null) return false;
        for (String s : skills) {
            if (s != null && s.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.progressBar.setVisibility(loading && allUsers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Best-effort: refresh current-user location on resume, guarded against missing permission.
        if (getContext() != null && LocationUtils.hasLocationPermission(requireContext())) {
            LocationUtils.getLastKnownLocation(requireContext(), new LocationUtils.LocationCallback() {
                @Override
                public void onLocation(double lat, double lng) {
                    currentLat = lat;
                    currentLng = lng;
                    if (adapter != null) adapter.setCurrentLocation(lat, lng);
                }

                @Override
                public void onUnavailable() { }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
