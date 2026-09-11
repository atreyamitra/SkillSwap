package com.skillswap.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.skillswap.app.activities.ChatActivity;
import com.skillswap.app.adapters.ChatListAdapter;
import com.skillswap.app.databinding.FragmentChatListBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.SwapRequestRepository;
import com.skillswap.app.models.SwapRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Chats tab: one row per ACCEPTED (or COMPLETED) swap request, since chat is only
 * available once a swap has been accepted.
 */
public class ChatListFragment extends Fragment {

    private FragmentChatListBinding binding;
    private final SwapRequestRepository requestRepository = new SwapRequestRepository();
    private ChatListAdapter adapter;
    private List<SwapRequest> incoming = new ArrayList<>();
    private List<SwapRequest> sent = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentChatListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String currentUid = AuthManager.getInstance().getCurrentUid();

        adapter = new ChatListAdapter(currentUid, request -> {
            boolean isSender = currentUid != null && currentUid.equals(request.getSenderId());
            String otherUid = isSender ? request.getReceiverId() : request.getSenderId();
            String otherName = isSender ? request.getReceiverName() : request.getSenderName();
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_OTHER_UID, otherUid);
            intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, otherName);
            startActivity(intent);
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        if (currentUid == null) return;

        requestRepository.listenIncoming(currentUid, new SwapRequestRepository.RequestListCallback() {
            @Override
            public void onSuccess(List<SwapRequest> requests) {
                incoming = requests;
                refresh();
            }

            @Override
            public void onError(String message) { }
        });

        requestRepository.listenSent(currentUid, new SwapRequestRepository.RequestListCallback() {
            @Override
            public void onSuccess(List<SwapRequest> requests) {
                sent = requests;
                refresh();
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void refresh() {
        if (binding == null) return;
        List<SwapRequest> conversations = new ArrayList<>();
        for (SwapRequest r : incoming) {
            if (isChatEligible(r)) conversations.add(r);
        }
        for (SwapRequest r : sent) {
            if (isChatEligible(r)) conversations.add(r);
        }
        adapter.submitList(conversations);
        binding.tvEmpty.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean isChatEligible(SwapRequest r) {
        return SwapRequest.STATUS_ACCEPTED.equals(r.getStatus()) || SwapRequest.STATUS_COMPLETED.equals(r.getStatus());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
