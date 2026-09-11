package com.skillswap.app.utils;

import android.text.TextUtils;
import android.util.Patterns;

/**
 * Simple, dependency-free form validation helpers shared by Register/Login/Profile screens.
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static boolean isValidEmail(CharSequence email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isValidPassword(CharSequence password) {
        return password != null && password.length() >= 6;
    }

    public static boolean isNonEmpty(CharSequence text) {
        return !TextUtils.isEmpty(text) && !text.toString().trim().isEmpty();
    }
}
