package com.skillswap.app.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.skillswap.app.R;
import com.skillswap.app.activities.MainActivity;

/**
 * Creates the app's notification channels and posts local notifications for:
 *  - a new incoming swap request
 *  - a request being accepted
 *  - an upcoming scheduled session (triggered by SessionReminderWorker)
 *
 * NOTE: these are genuine local notifications fired by app/worker logic while the
 * device has the app installed. True push-when-app-is-fully-closed-and-killed would
 * require Firebase Cloud Messaging plus a server/cloud function, which is out of
 * scope for this student demo (see BUILD_NOTES.md).
 */
public final class NotificationUtils {

    public static final String CHANNEL_REQUESTS = "channel_requests";
    public static final String CHANNEL_SESSIONS = "channel_sessions";

    private static final int NOTIF_ID_REQUEST = 1001;
    private static final int NOTIF_ID_ACCEPTED = 1002;
    private static final int NOTIF_ID_SESSION = 1003;

    private NotificationUtils() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel requests = new NotificationChannel(
                    CHANNEL_REQUESTS,
                    context.getString(R.string.notification_channel_requests_name),
                    NotificationManager.IMPORTANCE_HIGH);
            requests.setDescription(context.getString(R.string.notification_channel_requests_desc));

            NotificationChannel sessions = new NotificationChannel(
                    CHANNEL_SESSIONS,
                    context.getString(R.string.notification_channel_sessions_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            sessions.setDescription(context.getString(R.string.notification_channel_sessions_desc));

            manager.createNotificationChannel(requests);
            manager.createNotificationChannel(sessions);
        }
    }

    private static boolean hasPostPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void notifyIncomingRequest(Context context, String fromName, String skill) {
        if (!hasPostPermission(context)) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REQUESTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New swap request")
                .setContentText(fromName + " wants to swap for \"" + skill + "\"")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent(context));
        NotificationManagerCompat.from(context).notify(NOTIF_ID_REQUEST, builder.build());
    }

    public static void notifyRequestAccepted(Context context, String byName) {
        if (!hasPostPermission(context)) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REQUESTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Request accepted!")
                .setContentText(byName + " accepted your swap request")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent(context));
        NotificationManagerCompat.from(context).notify(NOTIF_ID_ACCEPTED, builder.build());
    }

    public static void notifyUpcomingSession(Context context, String withName, String dateText, String timeText) {
        if (!hasPostPermission(context)) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SESSIONS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Upcoming session")
                .setContentText("Your session with " + withName + " is on " + dateText + " at " + timeText)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent(context));
        NotificationManagerCompat.from(context).notify(NOTIF_ID_SESSION, builder.build());
    }

    private static android.app.PendingIntent contentIntent(Context context) {
        android.content.Intent intent = new android.content.Intent(context, MainActivity.class);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        return android.app.PendingIntent.getActivity(context, 0, intent, flags);
    }
}
