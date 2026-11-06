package com.sif.travelduaa;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;

/**
 * Every call that only exists on newer Android versions lives here,
 * each one behind a version check, so the app also runs on old head units.
 */
public class Compat {
    private static final String CHANNEL = "status";

    /** Starts the service in a way the current Android version allows. */
    public static void startService(Context c, Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Exception e) {
            // Android 12+ can refuse if the app is in the background at the wrong moment.
            Prefs.log(c, "Android refused to start: " + e.getClass().getSimpleName());
        }
    }

    public static Notification notification(Context c, String text) {
        Intent open = new Intent(c, MainActivity.class);
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Status",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        return b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Travel Duaa")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    /**
     * @param playing true = media playback (phone mode, while playing),
     *                false = waiting in the background (head unit mode)
     */
    public static void startForeground(Service s, Notification n, boolean playing) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                int type = playing
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                s.startForeground(1, n, type);
            } else if (Build.VERSION.SDK_INT >= 29) {
                s.startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                s.startForeground(1, n);
            }
        } catch (Exception e) {
            Prefs.log(s, "Could not show status notification: " + e.getClass().getSimpleName());
        }
    }

    /** Receivers for broadcasts from other apps (e.g. head unit ACC ON) need this flag on 13+. */
    public static void registerExported(Context c, BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) {
            c.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } else {
            c.registerReceiver(r, f);
        }
    }

    public static void stopForeground(Service s) {
        if (Build.VERSION.SDK_INT >= 24) {
            s.stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            s.stopForeground(true);
        }
    }

    /** Bluetooth permission is only asked for at runtime on Android 12+. */
    public static boolean needsBluetoothPermission(Context c) {
        return Build.VERSION.SDK_INT >= 31
                && c.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static boolean needsNotificationPermission(Context c) {
        return Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static String bluetoothPermission() {
        return android.Manifest.permission.BLUETOOTH_CONNECT;
    }

    public static String notificationPermission() {
        return android.Manifest.permission.POST_NOTIFICATIONS;
    }
}
