package com.skillswap.app.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.skillswap.app.databinding.ActivityLoginBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.utils.ValidationUtils;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = safeText(binding.etEmail.getText());
        String password = safeText(binding.etPassword.getText());

        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        boolean valid = true;
        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.setError("Enter a valid email");
            valid = false;
        }
        if (!ValidationUtils.isNonEmpty(password)) {
            binding.tilPassword.setError("Password is required");
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        AuthManager.getInstance().getFirebaseAuth().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        String message = task.getException() != null
                                ? task.getException().getMessage() : "Login failed";
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.btnLogin.setEnabled(!loading);
    }

    private String safeText(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
