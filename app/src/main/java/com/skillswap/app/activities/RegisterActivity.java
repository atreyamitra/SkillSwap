package com.skillswap.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.skillswap.app.databinding.ActivityRegisterBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.UserRepository;
import com.skillswap.app.models.User;
import com.skillswap.app.utils.ValidationUtils;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvGoLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String name = safeText(binding.etName.getText());
        String email = safeText(binding.etEmail.getText());
        String password = safeText(binding.etPassword.getText());
        String confirm = safeText(binding.etConfirmPassword.getText());

        binding.tilName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);

        boolean valid = true;
        if (!ValidationUtils.isNonEmpty(name)) {
            binding.tilName.setError("Name is required");
            valid = false;
        }
        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.setError("Enter a valid email");
            valid = false;
        }
        if (!ValidationUtils.isValidPassword(password)) {
            binding.tilPassword.setError("At least 6 characters");
            valid = false;
        }
        if (!password.equals(confirm)) {
            binding.tilConfirmPassword.setError("Passwords do not match");
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        AuthManager.getInstance().getFirebaseAuth().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null
                            && task.getResult().getUser() != null) {
                        String uid = task.getResult().getUser().getUid();
                        User newUser = new User(uid, name, email);
                        userRepository.createOrUpdateUser(newUser,
                                () -> {
                                    setLoading(false);
                                    startActivity(new Intent(this, MainActivity.class));
                                    finish();
                                },
                                () -> {
                                    setLoading(false);
                                    Snackbar.make(binding.getRoot(),
                                            "Account created but profile save failed. Please edit your profile.",
                                            Snackbar.LENGTH_LONG).show();
                                    startActivity(new Intent(this, MainActivity.class));
                                    finish();
                                });
                    } else {
                        setLoading(false);
                        String message = task.getException() != null
                                ? task.getException().getMessage() : "Registration failed";
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!loading);
    }

    private String safeText(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
