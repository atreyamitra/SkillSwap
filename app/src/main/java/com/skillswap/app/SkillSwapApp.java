package com.skillswap.app;

import android.app.Application;

import com.skillswap.app.utils.NotificationUtils;

/**
 * Application entry point. Creates notification channels once, on process start.
 */
public class SkillSwapApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationUtils.createChannels(this);
    }
}
