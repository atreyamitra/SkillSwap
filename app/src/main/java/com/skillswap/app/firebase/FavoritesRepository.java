package com.skillswap.app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Read/write helper for favorites/{uid}/{targetUid} : true.
 */
public class FavoritesRepository {

    private final DatabaseReference favoritesRef;

    public FavoritesRepository() {
        favoritesRef = FirebaseDatabase.getInstance().getReference(DatabasePaths.FAVORITES);
    }

    public void setFavorite(String uid, String targetUid, boolean favorite) {
        if (favorite) {
            favoritesRef.child(uid).child(targetUid).setValue(true);
        } else {
            favoritesRef.child(uid).child(targetUid).removeValue();
        }
    }

    public void isFavorite(String uid, String targetUid, ExistsCallback callback) {
        favoritesRef.child(uid).child(targetUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callback.onResult(snapshot.exists());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(false);
            }
        });
    }

    public void listenFavoriteIds(String uid, IdsCallback callback) {
        favoritesRef.child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ids.add(child.getKey());
                }
                callback.onResult(ids);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(new ArrayList<>());
            }
        });
    }

    public interface ExistsCallback {
        void onResult(boolean exists);
    }

    public interface IdsCallback {
        void onResult(List<String> ids);
    }
}
