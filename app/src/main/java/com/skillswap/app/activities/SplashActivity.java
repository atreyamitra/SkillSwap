package com.skillswap.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.skillswap.app.databinding.ActivitySplashBinding;
import com.skillswap.app.firebase.AuthManager;

/**
 * Shown briefly on launch, then routes to MainActivity if already logged in, or
 * LoginActivity otherwise.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 900;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, SPLASH_DELAY_MS);
    }

    private void routeNext() {
        if (isFinishing()) return;
        Intent intent;
        if (AuthManager.getInstance().isLoggedIn()) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
