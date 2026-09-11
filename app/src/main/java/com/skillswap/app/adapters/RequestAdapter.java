package com.skillswap.app.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skillswap.app.databinding.ItemRequestBinding;
import com.skillswap.app.models.RequestStatus;
import com.skillswap.app.models.SwapRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows swap requests in the Requests screen. The same adapter is reused for all
 * three tabs (Incoming / Sent / Accepted); which action buttons are visible depends
 * on the tab and the request status.
 */
public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {

    public static final int TAB_INCOMING = 0;
    public static final int TAB_SENT = 1;
    public static final int TAB_ACCEPTED = 2;

    public interface Listener {
        void onAccept(SwapRequest request);
        void onReject(SwapRequest request);
        void onChat(SwapRequest request);
        void onSchedule(SwapRequest request);
        void onComplete(SwapRequest request);
        void onReview(SwapRequest request);
    }

    private final List<SwapRequest> requests = new ArrayList<>();
    private final Listener listener;
    private final String currentUid;
    private int tab = TAB_INCOMING;
    private Set<String> alreadyReviewedRequestIds = new HashSet<>();

    public RequestAdapter(String currentUid, Listener listener) {
        this.currentUid = currentUid;
        this.listener = listener;
    }

    public void setTab(int tab) {
        this.tab = tab;
    }

    public void submitList(List<SwapRequest> newList) {
        requests.clear();
        requests.addAll(newList);
        notifyDataSetChanged();
    }

    public void setAlreadyReviewedIds(Set<String> ids) {
        this.alreadyReviewedRequestIds = ids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRequestBinding binding = ItemRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(requests.get(position));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemRequestBinding binding;

        ViewHolder(ItemRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SwapRequest request) {
            boolean isSender = currentUid != null && currentUid.equals(request.getSenderId());
            String otherName = isSender ? request.getReceiverName() : request.getSenderName();
            binding.tvOtherName.setText(otherName);
            binding.tvSkills.setText("Offers: " + request.getOfferedSkill()
                    + "  ⇄  Wants: " + request.getRequestedSkill());
            RequestStatus status = request.getStatus();
            binding.tvStatus.setText(status.name());
            binding.tvStatus.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(statusColor(status)));

            binding.actionsLayout.setVisibility(View.GONE);
            binding.acceptedActionsLayout.setVisibility(View.GONE);
            binding.btnReview.setVisibility(View.GONE);

            if (tab == TAB_INCOMING && status == RequestStatus.PENDING) {
                binding.actionsLayout.setVisibility(View.VISIBLE);
                binding.btnAccept.setOnClickListener(v -> listener.onAccept(request));
                binding.btnReject.setOnClickListener(v -> listener.onReject(request));
            } else if (status == RequestStatus.ACCEPTED) {
                binding.acceptedActionsLayout.setVisibility(View.VISIBLE);
                binding.btnChat.setOnClickListener(v -> listener.onChat(request));
                binding.btnSchedule.setOnClickListener(v -> listener.onSchedule(request));
                binding.btnComplete.setOnClickListener(v -> listener.onComplete(request));
            } else if (status == RequestStatus.COMPLETED) {
                boolean alreadyReviewed = alreadyReviewedRequestIds.contains(request.getRequestId());
                if (!alreadyReviewed) {
                    binding.btnReview.setVisibility(View.VISIBLE);
                    binding.btnReview.setOnClickListener(v -> listener.onReview(request));
                }
            }
        }

        private int statusColor(RequestStatus status) {
            switch (status) {
                case ACCEPTED:
                    return Color.parseColor("#2E7D32");
                case REJECTED:
                    return Color.parseColor("#D32F2F");
                case COMPLETED:
                    return Color.parseColor("#1976D2");
                default:
                    return Color.parseColor("#FFA000");
            }
        }
    }
}
