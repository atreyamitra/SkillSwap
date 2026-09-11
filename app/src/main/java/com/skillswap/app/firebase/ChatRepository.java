package com.skillswap.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.skillswap.app.models.Message;

/**
 * Read/write helper for messages/{conversationId}/{messageId}.
 */
public class ChatRepository {

    private final DatabaseReference messagesRoot;

    public ChatRepository() {
        messagesRoot = FirebaseDatabase.getInstance().getReference(DatabasePaths.MESSAGES);
    }

    public void sendMessage(String uidA, String uidB, Message message) {
        String conversationId = DatabasePaths.conversationId(uidA, uidB);
        DatabaseReference convoRef = messagesRoot.child(conversationId);
        String key = convoRef.push().getKey();
        message.setMessageId(key);
        convoRef.child(key).setValue(message);
    }

    /** Attaches a live listener for new messages appended to a conversation. Returns the
     *  listener so callers can detach it in onDestroy via removeListener(). */
    public ChildEventListener listenForMessages(String uidA, String uidB, MessageCallback callback) {
        String conversationId = DatabasePaths.conversationId(uidA, uidB);
        DatabaseReference convoRef = messagesRoot.child(conversationId);
        ChildEventListener listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Message m = snapshot.getValue(Message.class);
                if (m != null) callback.onNewMessage(m);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) { }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) { }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) { }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        convoRef.addChildEventListener(listener);
        return listener;
    }

    public void removeListener(String uidA, String uidB, ChildEventListener listener) {
        String conversationId = DatabasePaths.conversationId(uidA, uidB);
        messagesRoot.child(conversationId).removeEventListener(listener);
    }

    public interface MessageCallback {
        void onNewMessage(Message message);
    }
}
