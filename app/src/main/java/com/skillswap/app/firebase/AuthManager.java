package com.skillswap.app.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Thin wrapper around FirebaseAuth used everywhere instead of calling
 * FirebaseAuth.getInstance() directly, so auth behaviour stays centralized.
 */
public final class AuthManager {

    private static AuthManager instance;
    private final FirebaseAuth firebaseAuth;

    private AuthManager() {
        firebaseAuth = FirebaseAuth.getInstance();
    }

    public static synchronized AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }

    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public String getCurrentUid() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public FirebaseAuth getFirebaseAuth() {
        return firebaseAuth;
    }

    public void signOut() {
        firebaseAuth.signOut();
    }
}
