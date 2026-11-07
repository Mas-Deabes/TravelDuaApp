package com.sif.travelduaa;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.FileInputStream;

/**
 * Plays the duaa.
 *
 * Head unit mode: keeps running in the background, because most Chinese head
 * units sleep instead of shutting down. It watches for ignition (ACC ON) and
 * for the screen waking after a long sleep.
 *
 * Phone mode: only runs for a short time after the car's Bluetooth connects.
 * It waits until Bluetooth audio is ready, plays once, then stops.
 */
public class DuaaService extends Service {
    // Head unit
    public static final String ACTION_HU_STARTED = "com.sif.travelduaa.HU_STARTED";
    public static final String ACTION_HU_WATCH = "com.sif.travelduaa.HU_WATCH";
    // Phone
    public static final String ACTION_PHONE_CONNECTED = "com.sif.travelduaa.PHONE_CONNECTED";
    public static final String ACTION_PHONE_DISCONNECTED = "com.sif.travelduaa.PHONE_DISCONNECTED";
    // Buttons in the app
    public static final String ACTION_PLAY_NOW = "com.sif.travelduaa.PLAY_NOW";
    public static final String ACTION_STOP = "com.sif.travelduaa.STOP";
    /** Test button: runs the real start-up steps, but ignores "already played" and the on/off switch. */
    public static final String ACTION_TEST = "com.sif.travelduaa.TEST";
    public static final String EXTRA_FULL_BOOT = "full_boot";
    /** From the accessibility helper: it noticed the unit waking up. */
    public static final String ACTION_HU_WOKE = "com.sif.travelduaa.HU_WOKE";
    /** From the accessibility helper after it (re)connects: check how long the app was gone. */
    public static final String ACTION_HU_CHECK_AWAY = "com.sif.travelduaa.HU_CHECK_AWAY";
    /** Volume slider moved: apply it now if that recording is playing. */
    public static final String ACTION_VOLUME = "com.sif.travelduaa.VOLUME";
    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_PERCENT = "percent";

    /** How often the app checks whether the head unit has been asleep. */
    private static final long HEARTBEAT_MS = 5_000;

    /** Ignition broadcasts from different head unit makers. */
    private static final String[] ACC_ON_ACTIONS = {
            "com.fyt.boot.ACCON",
            "autochips.intent.action.QB_POWERON",
            "autochips.intent.action.ACC_ON",
            "android.intent.action.ACC_ON",
            "com.android.action.ACC_ON",
            "android.intent.action.QUICKBOOT_POWERON",
            "car.intent.action.ACC_ON",
            "com.microntek.bootcheck",          // Microntek / MTCx units
            "com.microntek.acc_on",
            "com.microntek.acc.on",
            "android.intent.action.CAR_ACC_ON",  // Allwinner T3 / T8
            "android.intent.action.MCU_ACC_ON",  // XYAuto / AC8227L / YT9216
            "com.android.ecar.ACC_ON",           // ECar firmware
            "com.fyt.action.ACC_ON",             // older FYT
            "com.fyt.screen.on",
            "android.intent.action.TS_ACC_ON",   // TopWay TS10 / TS18
            "com.tw.action.ACC_ON",
            Intent.ACTION_USER_PRESENT,          // unit is awake and usable again
    };

    /** Car-off broadcasts. Only logged, so the event log shows what the unit sends. */
    private static final String[] ACC_OFF_ACTIONS = {
            "com.fyt.boot.ACCOFF",
            "autochips.intent.action.QB_POWEROFF",
            "autochips.intent.action.ACC_OFF",
            "android.intent.action.ACC_OFF",
            "com.android.action.ACC_OFF",
            "android.intent.action.QUICKBOOT_POWEROFF",
            "car.intent.action.ACC_OFF",
            "com.microntek.acc_off",
            "com.microntek.acc.off",
            "android.intent.action.CAR_ACC_OFF",
            "android.intent.action.MCU_ACC_OFF",
            "com.android.ecar.ACC_OFF",
            "com.fyt.action.ACC_OFF",
            "com.fyt.screen.off",
            "android.intent.action.TS_ACC_OFF",
            "com.tw.action.ACC_OFF",
            Intent.ACTION_SHUTDOWN,
    };

    public static volatile boolean running = false;
    /** Title of the duaa playing right now, or null. Shown as a banner in the app. */
    public static volatile String nowPlaying = null;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private String playingKey;                 // which recording is playing
    private LoudnessEnhancer booster;          // used when volume is above 100%
    private AudioManager audio;
    private PowerManager.WakeLock wakeLock;
    private boolean watchingHeadUnit = false;
    private long waitStartedAt = 0;
    // Sleep detection: while the unit sleeps, uptime stops but real time keeps going.
    private long lastSleepTotal = 0;   // elapsedRealtime - uptimeMillis at the last check
    private long lastBeatWall = 0;     // clock time at the last check
    private long lastSavedAlive = 0;
    /** True when the next play() is an automatic start (or the Test button), not "Play". */
    private boolean nextIsStartUp = false;

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> { };
    private final Runnable playRunnable = this::play;
    private final Runnable waitForBluetoothAudio = this::checkBluetoothAudio;
    private final Runnable heartbeat = this::checkForSleep;

    /** Head unit: screen on/off and ACC ON while the app is already running. */
    private final BroadcastReceiver headUnitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                // Saved, not just kept in memory, in case Android restarts the app while asleep.
                Prefs.get(context).edit().putLong(Prefs.SCREEN_OFF_AT, System.currentTimeMillis()).apply();
                Prefs.log(context, "Screen off");
                // Stop playing if the car is switched off mid-duaa.
                stopPlayer();
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                long offAt = Prefs.get(context).getLong(Prefs.SCREEN_OFF_AT, 0);
                Prefs.get(context).edit().remove(Prefs.SCREEN_OFF_AT).apply();
                long offFor = offAt == 0 ? 0 : System.currentTimeMillis() - offAt;
                boolean useScreen = Prefs.get(context).getBoolean(Prefs.HU_SCREEN_WAKE, true);
                long needed = Prefs.get(context).getInt(Prefs.HU_MIN_SLEEP_MIN, 1) * 60_000L;
                Prefs.log(context, "Screen on (was off " + duration(offFor) + ")");
                if (!useScreen) {
                    Prefs.log(context, "Not playing: \"wakes from sleep\" is turned off");
                } else if (offFor < needed) {
                    Prefs.log(context, "Not playing: off for less than " + (needed / 60000) + " min");
                } else {
                    wokeUp();
                }
            } else if (isAccOff(a)) {
                Prefs.log(context, "Car off signal: " + a);
                stopPlayer();
            } else {
                Prefs.log(context, "Received: " + a);
                wokeUp();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Prefs.isHeadUnit(this)) Prefs.log(this, "Background service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        boolean headUnit = Prefs.isHeadUnit(this);

        // Must be called quickly every time the service is started.
        Compat.startForeground(this, Compat.notification(this,
                headUnit ? "Ready to play when the car starts" : "Waiting for car audio"),
                !headUnit);

        boolean firstStart = !watchingHeadUnit;
        if (headUnit) {
            startWatchingHeadUnit();
        }

        if (action == null) {
            // Restarted by Android after being killed.
            if (!headUnit) {
                stopEverything();
                return START_NOT_STICKY;
            }
            // If the app was gone for a long time, the car was most likely off: treat as waking up.
            if (firstStart) checkTimeAway("App restarted");
            return START_STICKY;
        }

        switch (action) {
            case ACTION_HU_STARTED:
                if (intent.getBooleanExtra(EXTRA_FULL_BOOT, false)) {
                    if (Prefs.get(this).getBoolean(Prefs.HU_ON_BOOT, true)) {
                        headUnitStarted();
                    } else {
                        Prefs.log(this, "Not playing: \"full start-up\" is turned off");
                    }
                } else {
                    wokeUp();
                }
                break;
            case ACTION_HU_WATCH:
                break;
            case ACTION_HU_WOKE:
                Prefs.log(this, "Helper noticed the car start");
                wokeUp();
                break;
            case ACTION_HU_CHECK_AWAY:
                if (firstStart) checkTimeAway("App started by the helper");
                break;
            case ACTION_PHONE_CONNECTED:
                phoneConnected();
                break;
            case ACTION_PHONE_DISCONNECTED:
            case ACTION_STOP:
                handler.removeCallbacks(playRunnable);
                handler.removeCallbacks(waitForBluetoothAudio);
                stopPlayer();
                if (!headUnit) stopEverything();
                break;
            case ACTION_PLAY_NOW:
                handler.removeCallbacks(playRunnable);
                nextIsStartUp = false;
                play();
                break;
            case ACTION_TEST:
                runTest(headUnit);
                break;
            case ACTION_VOLUME:
                if (player != null && intent.getStringExtra(EXTRA_KEY) != null
                        && intent.getStringExtra(EXTRA_KEY).equals(playingKey)) {
                    applyVolume(intent.getIntExtra(EXTRA_PERCENT, 100));
                }
                if (!headUnit && player == null) stopEverything();
                break;
        }
        return headUnit ? START_STICKY : START_NOT_STICKY;
    }

    // ---------- Head unit mode ----------

    private void startWatchingHeadUnit() {
        if (watchingHeadUnit) return;
        watchingHeadUnit = true;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        for (String a : ACC_ON_ACTIONS) f.addAction(a);
        for (String a : ACC_OFF_ACTIONS) f.addAction(a);
        Compat.registerExported(this, headUnitReceiver, f);

        // Start checking for sleep.
        lastSleepTotal = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
        lastBeatWall = System.currentTimeMillis();
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
    }

    /**
     * Runs every few seconds. Two ways to tell the unit was asleep:
     *  1. uptime (which stops during sleep) fell behind real elapsed time
     *  2. this check itself didn't run for a long time (the app was frozen)
     * Works even on units that send no screen or ignition signal.
     */
    private void checkForSleep() {
        long sleepTotal = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
        long slept = sleepTotal - lastSleepTotal;
        lastSleepTotal = sleepTotal;

        long now = System.currentTimeMillis();
        long gap = now - lastBeatWall - HEARTBEAT_MS;
        lastBeatWall = now;

        long away = Math.max(slept, gap);
        if (away >= 10_000) {
            Prefs.log(this, "Head unit was asleep for " + duration(away));
            long needed = Prefs.get(this).getInt(Prefs.HU_MIN_SLEEP_MIN, 1) * 60_000L;
            if (away >= needed) {
                wokeUp();
            } else {
                Prefs.log(this, "Not playing: off for less than " + (needed / 60000) + " min");
            }
        }

        // Remember we were running, in case the app gets closed while the unit sleeps.
        if (now - lastSavedAlive > 30_000 || now < lastSavedAlive) {
            Prefs.get(this).edit().putLong(Prefs.LAST_ALIVE, now).apply();
            lastSavedAlive = now;
        }
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
    }

    /**
     * One car start can trigger several checks (screen, ignition, clocks) within seconds.
     * Ignore repeats within this window: the "Car must be off" time, at least 30 s, at most 3 min.
     */
    private long huRepeatWindow() {
        long min = Prefs.get(this).getInt(Prefs.HU_MIN_SLEEP_MIN, 1) * 60_000L;
        return Math.max(30_000, Math.min(Prefs.HU_DEBOUNCE_MS, min));
    }

    /** After a restart: if the app hadn't run for a while, the car was probably off. */
    private void checkTimeAway(String reason) {
        long last = Prefs.get(this).getLong(Prefs.LAST_ALIVE, 0);
        if (last == 0) return;
        long away = System.currentTimeMillis() - last;
        long needed = Prefs.get(this).getInt(Prefs.HU_MIN_SLEEP_MIN, 1) * 60_000L;
        Prefs.log(this, reason + ", last running " + duration(away) + " ago");
        if (away >= Math.max(needed, 10_000)) wokeUp();
    }

    /** The unit woke from sleep (car turned back on). */
    private void wokeUp() {
        if (!Prefs.get(this).getBoolean(Prefs.HU_SCREEN_WAKE, true)) {
            Prefs.log(this, "Not playing: \"wakes from sleep\" is turned off");
            return;
        }
        headUnitStarted();
    }

    private void headUnitStarted() {
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean(Prefs.ENABLED, true)) {
            Prefs.log(this, "Skipped: auto-play is off");
            return;
        }
        long window = huRepeatWindow();
        if (playedWithin(window)) {
            Prefs.log(this, "Skipped: already played in the last " + duration(window));
            return;
        }
        markPlayed();
        int delay = p.getInt(Prefs.HU_DELAY, 5);
        handler.removeCallbacks(playRunnable);
        nextIsStartUp = true;
        handler.postDelayed(playRunnable, delay * 1000L);
        Prefs.log(this, "Will play in " + delay + " s");
    }

    // ---------- Test button ----------

    private void runTest(boolean headUnit) {
        handler.removeCallbacks(playRunnable);
        handler.removeCallbacks(waitForBluetoothAudio);
        stopPlayer();
        if (headUnit) {
            int delay = Prefs.get(this).getInt(Prefs.HU_DELAY, 5);
            Prefs.log(this, "TEST: pretending the car just started. Playing in " + delay + " s");
            nextIsStartUp = true;
            handler.postDelayed(playRunnable, delay * 1000L);
        } else {
            int delay = Prefs.get(this).getInt(Prefs.PHONE_DELAY, 2);
            Prefs.log(this, bluetoothAudioReady()
                    ? "TEST: car Bluetooth audio is connected. Playing in " + delay + " s"
                    : "TEST: no Bluetooth audio right now, playing on this phone in " + delay + " s");
            acquireWakeLock();
            handler.postDelayed(playRunnable, delay * 1000L);
        }
    }

    // ---------- Phone mode ----------

    private void phoneConnected() {
        if (!Prefs.get(this).getBoolean(Prefs.ENABLED, true)) {
            Prefs.log(this, "Skipped: auto-play is off");
            stopEverything();
            return;
        }
        if (playedWithin(Prefs.PHONE_DEBOUNCE_MS)) {
            Prefs.log(this, "Skipped: already played in the last 10 min");
            stopEverything();
            return;
        }
        markPlayed();
        acquireWakeLock();
        waitStartedAt = System.currentTimeMillis();
        handler.removeCallbacks(waitForBluetoothAudio);
        handler.post(waitForBluetoothAudio);
    }

    /**
     * The phone connects to the car first, and Bluetooth audio follows a few
     * seconds later. Check once a second until the audio is ready.
     */
    private void checkBluetoothAudio() {
        boolean ready = bluetoothAudioReady();
        long waited = System.currentTimeMillis() - waitStartedAt;
        if (ready || waited >= Prefs.PHONE_MAX_WAIT_MS) {
            int delay = Prefs.get(this).getInt(Prefs.PHONE_DELAY, 2);
            Prefs.log(this, ready
                    ? "Bluetooth audio ready after " + (waited / 1000) + " s"
                    : "Bluetooth audio not detected, playing anyway");
            handler.postDelayed(playRunnable, delay * 1000L);
        } else {
            handler.postDelayed(waitForBluetoothAudio, 1000);
        }
    }

    private boolean bluetoothAudioReady() {
        if (Build.VERSION.SDK_INT >= 23) {
            for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true;
            }
            return false;
        }
        //noinspection deprecation
        return audio.isBluetoothA2dpOn();
    }

    // ---------- Playback ----------

    private void play() {
        final boolean startUp = nextIsStartUp;
        nextIsStartUp = false;
        Duas.Dua dua = Duas.selectedDua(this);
        Duas.Recording rec = Duas.audioFor(this, dua);
        if (rec == null) {
            Prefs.log(this, "No audio for \"" + dua.title + "\". Choose or add one in the app.");
            if (startUp) openAfterApp();
            if (!Prefs.isHeadUnit(this)) stopEverything();
            return;
        }
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if (rec.file != null) {
                try (FileInputStream in = new FileInputStream(rec.file)) {
                    player.setDataSource(in.getFD());
                }
            } else {
                try (AssetFileDescriptor afd = getResources().openRawResourceFd(rec.rawRes)) {
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                }
            }
            player.prepare();
            playingKey = rec.key;
            applyVolume(Duas.volume(this, rec));
            player.setOnCompletionListener(mp -> {
                stopPlayer();
                Prefs.log(this, "Duaa finished");
                if (startUp) openAfterApp();
                if (!Prefs.isHeadUnit(this)) stopEverything();
            });
            // Ask the radio / music app to go quieter while the duaa plays.
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            acquireWakeLock();
            player.start();
            nowPlaying = dua.title;
            if (startUp) showAppIfWanted();
            Prefs.log(this, "▶ Playing: " + dua.title + " (" + rec.name + ", "
                    + Duas.volume(this, rec) + "%)");
        } catch (Exception e) {
            Prefs.log(this, "Playback error: " + e.getMessage());
            stopPlayer();
            if (startUp) openAfterApp();
            if (!Prefs.isHeadUnit(this)) stopEverything();
        }
    }

    /** Head unit: opens the app chosen under "After the duaa, open" (e.g. CPLink / CarPlay). */
    private void openAfterApp() {
        if (!Prefs.isHeadUnit(this)) return;
        String pkg = Prefs.get(this).getString(Prefs.AFTER_APP, "");
        if (pkg.isEmpty()) return;
        String name = Prefs.get(this).getString(Prefs.AFTER_APP_NAME, pkg);
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                Prefs.log(this, "Can't open " + name + ": it's no longer installed");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
            Prefs.log(this, "Opened " + name);
        } catch (Exception e) {
            Prefs.log(this, "Couldn't open " + name + ": " + e.getClass().getSimpleName()
                    + ". Allow \"Display over other apps\" for Travel Duaa.");
        }
    }

    /**
     * 10–100%: turns the recording down.
     * 100–200%: full volume plus a loudness boost, for recordings saved too quietly.
     */
    private void applyVolume(int percent) {
        if (player == null) return;
        float v = Math.max(10, Math.min(200, percent)) / 100f;
        player.setVolume(Math.min(1f, v), Math.min(1f, v));
        try {
            if (v > 1f) {
                if (booster == null) booster = new LoudnessEnhancer(player.getAudioSessionId());
                // Gain in millibels: 200% ≈ +6 dB = 600 mB
                booster.setTargetGain((int) Math.round(2000 * Math.log10(v)));
                booster.setEnabled(true);
            } else if (booster != null) {
                booster.setEnabled(false);
            }
        } catch (Exception e) {
            Prefs.log(this, "Volume boost not supported on this device");
        }
    }

    /** Head unit option: bring Travel Duaa to the front while the duaa plays (e.g. over CPLink). */
    private void showAppIfWanted() {
        if (!Prefs.isHeadUnit(this)) return;
        if (!Prefs.get(this).getBoolean(Prefs.HU_SHOW_WHILE_PLAYING, false)) return;
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            Prefs.log(this, "Showing Travel Duaa while the duaa plays");
        } catch (Exception e) {
            Prefs.log(this, "Couldn't show the app: allow \"Display over other apps\"");
        }
    }

    private void stopPlayer() {
        nowPlaying = null;
        if (booster != null) {
            try { booster.release(); } catch (Exception ignored) { }
            booster = null;
        }
        playingKey = null;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        if (audio != null) audio.abandonAudioFocus(focusListener);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void stopEverything() {
        handler.removeCallbacks(playRunnable);
        handler.removeCallbacks(waitForBluetoothAudio);
        stopPlayer();
        Compat.stopForeground(this);
        stopSelf();
    }

    private boolean playedWithin(long ms) {
        long last = Prefs.get(this).getLong(Prefs.LAST_PLAYED, 0);
        long now = System.currentTimeMillis();
        return now >= last && now - last < ms;
    }

    private static boolean isAccOff(String action) {
        for (String a : ACC_OFF_ACTIONS) if (a.equals(action)) return true;
        return false;
    }

    private static String duration(long ms) {
        long sec = ms / 1000;
        if (ms <= 0) return "unknown time";
        if (sec < 60) return sec + " s";
        return (sec / 60) + " min";
    }

    private void markPlayed() {
        Prefs.get(this).edit().putLong(Prefs.LAST_PLAYED, System.currentTimeMillis()).apply();
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TravelDuaa:play");
        }
        if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (watchingHeadUnit) {
            try { unregisterReceiver(headUnitReceiver); } catch (Exception ignored) { }
        }
        handler.removeCallbacks(playRunnable);
        handler.removeCallbacks(waitForBluetoothAudio);
        handler.removeCallbacks(heartbeat);
        stopPlayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
