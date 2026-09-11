package com.skillswap.app.utils;

import android.util.Log;

/**
 * Thin wrapper around {@link android.util.Log} used instead of calling it directly,
 * so every log call in the app carries a single, consistent tag
 * ({@code "SkillSwap"}) that's trivial to filter for in {@code adb logcat -s
 * SkillSwap}, rather than each class inventing its own tag (or, as was previously
 * the case in several Firebase callback {@code onError} handlers, logging nothing
 * at all and silently swallowing the failure — see {@code docs/SDLC.md} "Logging").
 *
 * Deliberately not a full logging framework (no levels config, no file sink): this
 * app has one deployment target (a device via {@code adb logcat}), so
 * {@code android.util.Log} plus one consistent tag is already the correct amount of
 * machinery. A wrapper still earns its place because it's the one place a future
 * change (e.g. suppressing verbose logs in release builds) would go, instead of
 * needing to touch every call site.
 */
public final class AppLogger {

    private static final String TAG = "SkillSwap";

    private AppLogger() {
    }

    /** A recoverable failure the user-visible flow already handles (e.g. a Firebase
     *  read/write failed and the UI shows a retry or just stays in its current
     *  state) — worth a trace to debug from logs, not an error the app crashed on. */
    public static void w(String where, String message) {
        Log.w(TAG, where + ": " + message);
    }

    /** An unexpected condition the app recovered from but that shouldn't normally
     *  happen (e.g. a callback fired with data that failed a sanity check). */
    public static void e(String where, String message, Throwable cause) {
        Log.e(TAG, where + ": " + message, cause);
    }
}
