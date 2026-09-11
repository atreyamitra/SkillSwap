package com.skillswap.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skillswap.app.databinding.ItemMessageReceivedBinding;
import com.skillswap.app.databinding.ItemMessageSentBinding;
import com.skillswap.app.models.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Renders chat messages as left/right bubbles depending on whether the current
 * user was the sender.
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private final List<Message> messages = new ArrayList<>();
    private final String currentUid;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public MessageAdapter(String currentUid) {
        this.currentUid = currentUid;
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public int getMessageCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        Message m = messages.get(position);
        return currentUid != null && currentUid.equals(m.getSenderId()) ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SENT) {
            return new SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false));
        } else {
            return new ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message m = messages.get(position);
        String time = timeFormat.format(new Date(m.getTimestamp()));
        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).binding.tvMessage.setText(m.getText());
            ((SentViewHolder) holder).binding.tvTime.setText(time);
        } else if (holder instanceof ReceivedViewHolder) {
            ((ReceivedViewHolder) holder).binding.tvMessage.setText(m.getText());
            ((ReceivedViewHolder) holder).binding.tvTime.setText(time);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        final ItemMessageSentBinding binding;

        SentViewHolder(ItemMessageSentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        final ItemMessageReceivedBinding binding;

        ReceivedViewHolder(ItemMessageReceivedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
