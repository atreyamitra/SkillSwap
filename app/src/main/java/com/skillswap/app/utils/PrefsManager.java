package com.skillswap.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Wraps SharedPreferences for the small amount of local, per-device state the app needs:
 * whether onboarding/splash has been shown, and the user's last-used discover filter.
 */
public class PrefsManager {

    private static final String PREFS_NAME = "skillswap_prefs";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_LAST_FILTER = "last_filter_query";
    private static final String KEY_LAST_SESSION_REMINDER_ID = "last_session_reminder_work_id";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingComplete() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public void setOnboardingComplete(boolean complete) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    public String getLastFilter() {
        return prefs.getString(KEY_LAST_FILTER, "");
    }

    public void setLastFilter(String filter) {
        prefs.edit().putString(KEY_LAST_FILTER, filter).apply();
    }

    public void setLastSessionReminderWorkId(String sessionId, String workId) {
        prefs.edit().putString(KEY_LAST_SESSION_REMINDER_ID + "_" + sessionId, workId).apply();
    }

    public String getLastSessionReminderWorkId(String sessionId) {
        return prefs.getString(KEY_LAST_SESSION_REMINDER_ID + "_" + sessionId, null);
    }
}
