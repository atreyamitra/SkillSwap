package com.skillswap.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.skillswap.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write helper for the users/{uid} tree.
 */
public class UserRepository {

    private final DatabaseReference usersRef;

    public UserRepository() {
        usersRef = FirebaseDatabase.getInstance().getReference(DatabasePaths.USERS);
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onError(String message);
    }

    public interface UserListCallback {
        void onSuccess(List<User> users);
        void onError(String message);
    }

    public void createOrUpdateUser(User user, Runnable onSuccess, Runnable onError) {
        usersRef.child(user.getUid()).setValue(user)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.run());
    }

    public void updateFields(String uid, java.util.Map<String, Object> updates, Runnable onSuccess, Runnable onError) {
        usersRef.child(uid).updateChildren(updates)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.run());
    }

    public void getUser(String uid, UserCallback callback) {
        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    user.setUid(snapshot.getKey());
                }
                callback.onSuccess(user);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void getAllUsers(UserListCallback callback) {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<User> users = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    User user = child.getValue(User.class);
                    if (user != null) {
                        user.setUid(child.getKey());
                        users.add(user);
                    }
                }
                callback.onSuccess(users);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public DatabaseReference getUsersRef() {
        return usersRef;
    }
}
