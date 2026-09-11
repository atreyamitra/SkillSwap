package com.skillswap.app.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * One-off WorkManager job scheduled when a session is created, so the user gets a
 * local notification shortly before the scheduled time even if the app is in the
 * background. This does NOT survive the app being fully uninstalled or the device
 * being off at the trigger time for very long delays on some OEMs' aggressive
 * battery savers, but is a genuine, standard approach for a student demo.
 */
public class SessionReminderWorker extends Worker {

    public static final String KEY_WITH_NAME = "with_name";
    public static final String KEY_DATE_TEXT = "date_text";
    public static final String KEY_TIME_TEXT = "time_text";

    public SessionReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String withName = getInputData().getString(KEY_WITH_NAME);
        String dateText = getInputData().getString(KEY_DATE_TEXT);
        String timeText = getInputData().getString(KEY_TIME_TEXT);
        NotificationUtils.notifyUpcomingSession(getApplicationContext(),
                withName == null ? "your swap partner" : withName,
                dateText == null ? "" : dateText,
                timeText == null ? "" : timeText);
        return Result.success();
    }

    /**
     * Schedules a reminder to fire `leadTimeMinutes` before `sessionEpochMillis`.
     * If that moment is already in the past (e.g. it's less than the lead time away),
     * the reminder fires almost immediately instead of being skipped, so the demo
     * always shows a working notification.
     */
    public static String scheduleReminder(Context context, long sessionEpochMillis, long leadTimeMinutes,
                                           String withName, String dateText, String timeText) {
        long triggerAt = sessionEpochMillis - TimeUnit.MINUTES.toMillis(leadTimeMinutes);
        long delay = Math.max(0, triggerAt - System.currentTimeMillis());

        Data input = new Data.Builder()
                .putString(KEY_WITH_NAME, withName)
                .putString(KEY_DATE_TEXT, dateText)
                .putString(KEY_TIME_TEXT, timeText)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SessionReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build();

        WorkManager.getInstance(context).enqueue(request);
        return request.getId().toString();
    }
}
