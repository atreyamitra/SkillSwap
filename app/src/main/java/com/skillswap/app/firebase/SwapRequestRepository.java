package com.skillswap.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.skillswap.app.models.SwapRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write helper for the swapRequests/{requestId} tree.
 */
public class SwapRequestRepository {

    private final DatabaseReference requestsRef;

    public SwapRequestRepository() {
        requestsRef = FirebaseDatabase.getInstance().getReference(DatabasePaths.SWAP_REQUESTS);
    }

    public interface RequestListCallback {
        void onSuccess(List<SwapRequest> requests);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public String newRequestId() {
        return requestsRef.push().getKey();
    }

    public void sendRequest(SwapRequest request, SimpleCallback callback) {
        requestsRef.child(request.getRequestId()).setValue(request)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updateStatus(String requestId, String status, SimpleCallback callback) {
        requestsRef.child(requestId).child("status").setValue(status)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Checks whether a PENDING or ACCEPTED request already exists between the two users for
     *  the given skill pair, to prevent duplicate requests. */
    public void hasExistingActiveRequest(String senderId, String receiverId, RequestExistsCallback callback) {
        requestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean exists = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    SwapRequest r = child.getValue(SwapRequest.class);
                    if (r == null) continue;
                    boolean samePair = (r.getSenderId().equals(senderId) && r.getReceiverId().equals(receiverId))
                            || (r.getSenderId().equals(receiverId) && r.getReceiverId().equals(senderId));
                    boolean active = SwapRequest.STATUS_PENDING.equals(r.getStatus())
                            || SwapRequest.STATUS_ACCEPTED.equals(r.getStatus());
                    if (samePair && active) {
                        exists = true;
                        break;
                    }
                }
                callback.onResult(exists);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(false);
            }
        });
    }

    public interface RequestExistsCallback {
        void onResult(boolean exists);
    }

    public void listenIncoming(String uid, RequestListCallback callback) {
        Query q = requestsRef.orderByChild("receiverId").equalTo(uid);
        q.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callback.onSuccess(parseList(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void listenSent(String uid, RequestListCallback callback) {
        Query q = requestsRef.orderByChild("senderId").equalTo(uid);
        q.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callback.onSuccess(parseList(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void getRequestOnce(String requestId, SingleCallback callback) {
        requestsRef.child(requestId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callback.onResult(snapshot.getValue(SwapRequest.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(null);
            }
        });
    }

    public interface SingleCallback {
        void onResult(SwapRequest request);
    }

    private List<SwapRequest> parseList(DataSnapshot snapshot) {
        List<SwapRequest> list = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            SwapRequest r = child.getValue(SwapRequest.class);
            if (r != null) list.add(r);
        }
        return list;
    }
}
