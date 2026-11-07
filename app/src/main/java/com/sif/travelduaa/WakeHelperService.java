package com.sif.travelduaa;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional "Extra reliability" helper for head units that close apps while they sleep.
 *
 * Why an accessibility service: Android keeps these running, and restarts them by itself
 * if the app is closed. Head unit "clean memory" tools also usually leave them alone.
 *
 * What it does:
 *  1. When Android (re)connects it, it starts the main DuaaService again.
 *  2. It runs its own clock check (same method as DuaaService) to notice the unit waking.
 *  3. When the screen changes (e.g. the home screen or CPLink appears after waking),
 *     it runs the clock check straight away instead of waiting up to 5 seconds.
 *
 * It never reads what's on the screen; it only uses "a window changed" as a nudge.
 */
public class WakeHelperService extends AccessibilityService {

    private static final long CHECK_MS = 5_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastSleepTotal;
    private long lastCheckWall;
    private final Runnable check = new Runnable() {
        @Override public void run() {
            checkForSleep();
            handler.postDelayed(this, CHECK_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Prefs.log(this, "Extra reliability helper connected");
        if (!Prefs.isHeadUnit(this)) return;

        // Restart the main service. If the app was gone for a while, it treats that as a car start.
        Intent i = new Intent(this, DuaaService.class);
        i.setAction(DuaaService.ACTION_HU_CHECK_AWAY);
        Compat.startService(this, i);

        lastSleepTotal = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
        lastCheckWall = System.currentTimeMillis();
        handler.removeCallbacks(check);
        handler.postDelayed(check, CHECK_MS);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !Prefs.isHeadUnit(this)) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Something new appeared on screen: check now whether the unit just woke up.
            checkForSleep();
        }
    }

    /** Same idea as DuaaService: uptime stops during sleep, real time doesn't. */
    private void checkForSleep() {
        long sleepTotal = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
        long slept = sleepTotal - lastSleepTotal;
        lastSleepTotal = sleepTotal;

        long now = System.currentTimeMillis();
        long gap = now - lastCheckWall - CHECK_MS;
        lastCheckWall = now;

        long away = Math.max(slept, gap);
        if (away < 10_000) return;

        Prefs.log(this, "Helper: head unit was asleep for " + (away / 1000) + " s");
        long needed = Prefs.get(this).getInt(Prefs.HU_MIN_SLEEP_MIN, 1) * 60_000L;
        if (away < needed) return;

        Intent i = new Intent(this, DuaaService.class);
        i.setAction(DuaaService.ACTION_HU_WOKE);
        Compat.startService(this, i);
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(check);
        super.onDestroy();
    }
}
