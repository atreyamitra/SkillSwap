package com.skillswap.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skillswap.app.databinding.ItemChatBinding;
import com.skillswap.app.models.SwapRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows one row per accepted swap (= one chat conversation) in the Chats tab.
 */
public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {

    public interface Listener {
        void onChatClick(SwapRequest request);
    }

    private final List<SwapRequest> conversations = new ArrayList<>();
    private final String currentUid;
    private final Listener listener;

    public ChatListAdapter(String currentUid, Listener listener) {
        this.currentUid = currentUid;
        this.listener = listener;
    }

    public void submitList(List<SwapRequest> newList) {
        conversations.clear();
        conversations.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatBinding binding = ItemChatBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(conversations.get(position));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatBinding binding;

        ViewHolder(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SwapRequest request) {
            boolean isSender = currentUid != null && currentUid.equals(request.getSenderId());
            String otherName = isSender ? request.getReceiverName() : request.getSenderName();
            binding.tvName.setText(otherName);
            binding.tvSubtitle.setText("Swap: " + request.getOfferedSkill()
                    + " ⇄ " + request.getRequestedSkill());
            binding.getRoot().setOnClickListener(v -> listener.onChatClick(request));
        }
    }
}
