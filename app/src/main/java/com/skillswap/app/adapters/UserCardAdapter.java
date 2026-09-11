package com.skillswap.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skillswap.app.databinding.ItemUserCardBinding;
import com.skillswap.app.models.User;
import com.skillswap.app.utils.DistanceUtils;
import com.skillswap.app.utils.MatchUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows a list of other users on the Discover / Favorites screens, each with a
 * computed match score against the current user and an optional distance label.
 */
public class UserCardAdapter extends RecyclerView.Adapter<UserCardAdapter.ViewHolder> {

    public interface Listener {
        void onCardClick(User user);
        void onFavoriteClick(User user, boolean nowFavorite);
    }

    private final List<User> users = new ArrayList<>();
    private User currentUser;
    private Double currentLat;
    private Double currentLng;
    private List<String> favoriteIds = new ArrayList<>();
    private final Listener listener;

    public UserCardAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<User> newUsers) {
        users.clear();
        users.addAll(newUsers);
        notifyDataSetChanged();
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        notifyDataSetChanged();
    }

    public void setCurrentLocation(Double lat, Double lng) {
        this.currentLat = lat;
        this.currentLng = lng;
        notifyDataSetChanged();
    }

    public void setFavoriteIds(List<String> ids) {
        this.favoriteIds = ids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserCardBinding binding = ItemUserCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemUserCardBinding binding;

        ViewHolder(ItemUserCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(User user) {
            binding.tvName.setText(user.getName());
            binding.tvTeaches.setText("Teaches: " + joinOrNone(user.getSkillsTeach()));
            binding.tvWants.setText("Wants: " + joinOrNone(user.getSkillsWant()));

            StringBuilder ratingText = new StringBuilder();
            ratingText.append("★ ")
                    .append(String.format(Locale.getDefault(), "%.1f", user.getAvgRating()))
                    .append(" (").append(user.getRatingCount()).append(")");

            if (currentLat != null && currentLng != null
                    && DistanceUtils.hasValidCoordinates(user.getLat(), user.getLng())) {
                double km = DistanceUtils.haversineKm(currentLat, currentLng, user.getLat(), user.getLng());
                ratingText.append(" · ").append(String.format(Locale.getDefault(), "%.1f km away", km));
            }
            binding.tvRating.setText(ratingText.toString());

            if (currentUser != null) {
                MatchUtils.MatchResult result = MatchUtils.computeMatch(
                        currentUser.getSkillsTeach(), currentUser.getSkillsWant(),
                        user.getSkillsTeach(), user.getSkillsWant());
                binding.tvMatchScore.setText(result.score + "% match");
                binding.tvMatchScore.setVisibility(View.VISIBLE);
            } else {
                binding.tvMatchScore.setVisibility(View.GONE);
            }

            boolean isFavorite = favoriteIds.contains(user.getUid());
            binding.btnFavorite.setImageResource(isFavorite
                    ? com.skillswap.app.R.drawable.ic_favorite_filled
                    : com.skillswap.app.R.drawable.ic_favorite_border);

            binding.btnFavorite.setOnClickListener(v -> {
                boolean newState = !favoriteIds.contains(user.getUid());
                if (listener != null) listener.onFavoriteClick(user, newState);
            });

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onCardClick(user);
            });
        }

        private String joinOrNone(List<String> items) {
            if (items == null || items.isEmpty()) return "None yet";
            return android.text.TextUtils.join(", ", items);
        }
    }
}
