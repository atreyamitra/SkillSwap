package com.skillswap.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.skillswap.app.adapters.UserCardAdapter;
import com.skillswap.app.databinding.ActivityFavoritesBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.FavoritesRepository;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists users the current user has favorited, reusing the same card layout/adapter
 * as the Discover screen.
 */
public class FavoritesActivity extends AppCompatActivity {

    private ActivityFavoritesBinding binding;
    private final UserRepository userRepository = new UserRepository();
    private final FavoritesRepository favoritesRepository = new FavoritesRepository();
    private UserCardAdapter adapter;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFavoritesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbarInclude.tvToolbarTitle.setText("My Favorites");
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        adapter = new UserCardAdapter(new UserCardAdapter.Listener() {
            @Override
            public void onCardClick(User user) {
                Intent intent = new Intent(FavoritesActivity.this, UserDetailActivity.class);
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
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        loadCurrentUser();
        loadFavorites();
    }

    private void loadCurrentUser() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        userRepository.getUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                adapter.setCurrentUser(user);
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void loadFavorites() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        favoritesRepository.listenFavoriteIds(uid, ids -> {
            adapter.setFavoriteIds(ids);
            if (ids.isEmpty()) {
                adapter.submitList(new ArrayList<>());
                binding.tvEmpty.setVisibility(View.VISIBLE);
                return;
            }
            userRepository.getAllUsers(new UserRepository.UserListCallback() {
                @Override
                public void onSuccess(List<User> users) {
                    List<User> favorites = new ArrayList<>();
                    for (User u : users) {
                        if (ids.contains(u.getUid())) favorites.add(u);
                    }
                    adapter.submitList(favorites);
                    binding.tvEmpty.setVisibility(favorites.isEmpty() ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onError(String message) { }
            });
        });
    }
}
