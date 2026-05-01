package com.openterface.keymod;

import android.content.Context;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple in-app toast limiter to avoid repetitive popup spam.
 */
public final class UiToastLimiter {
    private static final long DEFAULT_WINDOW_MS = 2000L;
    private static final Map<String, Long> LAST_SHOWN_AT = new HashMap<>();

    private UiToastLimiter() {
    }

    public static void show(Context context, String message) {
        show(context, message, message, Toast.LENGTH_SHORT, DEFAULT_WINDOW_MS);
    }

    public static void show(Context context, String key, String message, int duration, long windowMs) {
        if (context == null || message == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        synchronized (LAST_SHOWN_AT) {
            Long last = LAST_SHOWN_AT.get(key);
            if (last != null && now - last < windowMs) {
                return;
            }
            LAST_SHOWN_AT.put(key, now);
        }
        Toast.makeText(context, message, duration).show();
    }
}
