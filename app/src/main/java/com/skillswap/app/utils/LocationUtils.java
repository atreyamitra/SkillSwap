package com.skillswap.app.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

/**
 * Thin, crash-safe wrapper around FusedLocationProviderClient. Every call first checks
 * the runtime permission and silently no-ops (never crashes) if it isn't granted.
 */
public final class LocationUtils {

    private LocationUtils() {
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public interface LocationCallback {
        void onLocation(double lat, double lng);
        void onUnavailable();
    }

    @android.annotation.SuppressLint("MissingPermission")
    public static void getLastKnownLocation(Context context, LocationCallback callback) {
        if (!hasLocationPermission(context)) {
            callback.onUnavailable();
            return;
        }
        try {
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
            client.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onLocation(location.getLatitude(), location.getLongitude());
                        } else {
                            callback.onUnavailable();
                        }
                    })
                    .addOnFailureListener(e -> callback.onUnavailable());
        } catch (SecurityException e) {
            callback.onUnavailable();
        }
    }
}
