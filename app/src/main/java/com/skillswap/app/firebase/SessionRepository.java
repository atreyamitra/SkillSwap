package com.skillswap.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.skillswap.app.models.Session;
import com.skillswap.app.models.SessionStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write helper for sessions/{sessionId}.
 */
public class SessionRepository {

    private final DatabaseReference sessionsRef;

    public SessionRepository() {
        sessionsRef = FirebaseDatabase.getInstance().getReference(DatabasePaths.SESSIONS);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface SessionListCallback {
        void onSuccess(List<Session> sessions);
        void onError(String message);
    }

    public String newSessionId() {
        return sessionsRef.push().getKey();
    }

    public void scheduleSession(Session session, SimpleCallback callback) {
        sessionsRef.child(session.getSessionId()).setValue(session)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void markCompleted(String sessionId, SimpleCallback callback) {
        sessionsRef.child(sessionId).child("status").setValue(SessionStatus.COMPLETED.name())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void listenForUser(String uid, SessionListCallback callback) {
        sessionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Session> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Session s = child.getValue(Session.class);
                    if (s != null && (uid.equals(s.getUserA()) || uid.equals(s.getUserB()))) {
                        list.add(s);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void getByRequestId(String requestId, SessionSingleCallback callback) {
        Query q = sessionsRef.orderByChild("requestId").equalTo(requestId);
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Session found = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    found = child.getValue(Session.class);
                }
                callback.onResult(found);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(null);
            }
        });
    }

    public interface SessionSingleCallback {
        void onResult(Session session);
    }
}
