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

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.skillswap.app.activities.ChatActivity;
import com.skillswap.app.activities.ReviewActivity;
import com.skillswap.app.activities.SessionScheduleActivity;
import com.skillswap.app.adapters.RequestAdapter;
import com.skillswap.app.databinding.FragmentRequestsBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.SessionRepository;
import com.skillswap.app.firebase.SwapRequestRepository;
import com.skillswap.app.models.RequestStatus;
import com.skillswap.app.models.SwapRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Requests tab with three sub-views (Incoming / Sent / Accepted) toggled via a TabLayout.
 * "Accepted" also includes COMPLETED requests so the user can leave a review.
 */
public class RequestsFragment extends Fragment {

    private FragmentRequestsBinding binding;
    private final SwapRequestRepository requestRepository = new SwapRequestRepository();
    private final SessionRepository sessionRepository = new SessionRepository();
    private RequestAdapter adapter;

    private List<SwapRequest> incoming = new ArrayList<>();
    private List<SwapRequest> sent = new ArrayList<>();
    private int currentTab = RequestAdapter.TAB_INCOMING;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentRequestsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String currentUid = AuthManager.getInstance().getCurrentUid();

        adapter = new RequestAdapter(currentUid, new RequestAdapter.Listener() {
            @Override
            public void onAccept(SwapRequest request) {
                if (!request.getStatus().canTransitionTo(RequestStatus.ACCEPTED)) {
                    showMessage("This request can no longer be accepted.");
                    return;
                }
                requestRepository.updateStatus(request.getRequestId(), RequestStatus.ACCEPTED,
                        new SwapRequestRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                showMessage("Request accepted");
                            }

                            @Override
                            public void onError(String message) {
                                showMessage("Failed: " + message);
                            }
                        });
            }

            @Override
            public void onReject(SwapRequest request) {
                if (!request.getStatus().canTransitionTo(RequestStatus.REJECTED)) {
                    showMessage("This request can no longer be rejected.");
                    return;
                }
                requestRepository.updateStatus(request.getRequestId(), RequestStatus.REJECTED,
                        new SwapRequestRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                showMessage("Request rejected");
                            }

                            @Override
                            public void onError(String message) {
                                showMessage("Failed: " + message);
                            }
                        });
            }

            @Override
            public void onChat(SwapRequest request) {
                String otherUid = otherUid(request);
                String otherName = otherName(request);
                Intent intent = new Intent(requireContext(), ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_OTHER_UID, otherUid);
                intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, otherName);
                startActivity(intent);
            }

            @Override
            public void onSchedule(SwapRequest request) {
                Intent intent = new Intent(requireContext(), SessionScheduleActivity.class);
                intent.putExtra(SessionScheduleActivity.EXTRA_REQUEST_ID, request.getRequestId());
                intent.putExtra(SessionScheduleActivity.EXTRA_OTHER_NAME, otherName(request));
                intent.putExtra(SessionScheduleActivity.EXTRA_OTHER_UID, otherUid(request));
                startActivity(intent);
            }

            @Override
            public void onComplete(SwapRequest request) {
                if (!request.getStatus().canTransitionTo(RequestStatus.COMPLETED)) {
                    showMessage("This request can no longer be marked complete.");
                    return;
                }
                requestRepository.updateStatus(request.getRequestId(), RequestStatus.COMPLETED,
                        new SwapRequestRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                showMessage("Marked as completed. You can now leave a review!");
                                sessionRepository.getByRequestId(request.getRequestId(), session -> {
                                    if (session != null) {
                                        sessionRepository.markCompleted(session.getSessionId(),
                                                new SessionRepository.SimpleCallback() {
                                                    @Override
                                                    public void onSuccess() { }

                                                    @Override
                                                    public void onError(String message) { }
                                                });
                                    }
                                });
                            }

                            @Override
                            public void onError(String message) {
                                showMessage("Failed: " + message);
                            }
                        });
            }

            @Override
            public void onReview(SwapRequest request) {
                Intent intent = new Intent(requireContext(), ReviewActivity.class);
                intent.putExtra(ReviewActivity.EXTRA_SWAP_ID, request.getRequestId());
                intent.putExtra(ReviewActivity.EXTRA_REVIEWED_UID, otherUid(request));
                intent.putExtra(ReviewActivity.EXTRA_REVIEWED_NAME, otherName(request));
                startActivity(intent);
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                adapter.setTab(currentTab);
                refreshList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });

        listenIncoming();
        listenSent();
        loadReviewedIds();
    }

    private void listenIncoming() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        requestRepository.listenIncoming(uid, new SwapRequestRepository.RequestListCallback() {
            @Override
            public void onSuccess(List<SwapRequest> requests) {
                incoming = requests;
                refreshList();
            }

            @Override
            public void onError(String message) {
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void listenSent() {
        String uid = AuthManager.getInstance().getCurrentUid();
        if (uid == null) return;
        requestRepository.listenSent(uid, new SwapRequestRepository.RequestListCallback() {
            @Override
            public void onSuccess(List<SwapRequest> requests) {
                sent = requests;
                refreshList();
            }

            @Override
            public void onError(String message) {
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void loadReviewedIds() {
        // Simplification for the demo: re-checked per-request when the review screen opens;
        // here we just avoid duplicate reviews at write-time too (see ReviewActivity).
    }

    private void refreshList() {
        if (binding == null) return;
        binding.progressBar.setVisibility(View.GONE);
        List<SwapRequest> list;
        if (currentTab == RequestAdapter.TAB_INCOMING) {
            list = incoming;
        } else if (currentTab == RequestAdapter.TAB_SENT) {
            list = sent;
        } else {
            list = new ArrayList<>();
            for (SwapRequest r : incoming) {
                if (r.getStatus() == RequestStatus.ACCEPTED || r.getStatus() == RequestStatus.COMPLETED) {
                    list.add(r);
                }
            }
            for (SwapRequest r : sent) {
                if (r.getStatus() == RequestStatus.ACCEPTED || r.getStatus() == RequestStatus.COMPLETED) {
                    list.add(r);
                }
            }
        }
        adapter.submitList(list);
        binding.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String otherUid(SwapRequest request) {
        String currentUid = AuthManager.getInstance().getCurrentUid();
        return currentUid != null && currentUid.equals(request.getSenderId())
                ? request.getReceiverId() : request.getSenderId();
    }

    private String otherName(SwapRequest request) {
        String currentUid = AuthManager.getInstance().getCurrentUid();
        return currentUid != null && currentUid.equals(request.getSenderId())
                ? request.getReceiverName() : request.getSenderName();
    }

    private void showMessage(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
