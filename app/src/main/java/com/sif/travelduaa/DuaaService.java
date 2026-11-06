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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

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

    /** Ignition broadcasts from different head unit makers. */
    private static final String[] ACC_ON_ACTIONS = {
            "com.fyt.boot.ACCON",
            "autochips.intent.action.QB_POWERON",
            "autochips.intent.action.ACC_ON",
            "android.intent.action.ACC_ON",
            "com.android.action.ACC_ON",
            "android.intent.action.QUICKBOOT_POWERON",
    };

    public static volatile boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private PowerManager.WakeLock wakeLock;
    private boolean watchingHeadUnit = false;
    private long screenOffAt = 0;
    private long waitStartedAt = 0;

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> { };
    private final Runnable playRunnable = this::play;
    private final Runnable waitForBluetoothAudio = this::checkBluetoothAudio;

    /** Head unit: screen on/off and ACC ON while the app is already running. */
    private final BroadcastReceiver headUnitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                screenOffAt = System.currentTimeMillis();
                // Stop playing if the car is switched off mid-duaa.
                stopPlayer();
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                long offFor = screenOffAt == 0 ? 0 : System.currentTimeMillis() - screenOffAt;
                screenOffAt = 0;
                boolean useScreen = Prefs.get(context).getBoolean(Prefs.HU_SCREEN_WAKE, true);
                if (useScreen && offFor >= Prefs.HU_MIN_SLEEP_MS) {
                    Prefs.log(context, "Screen on after " + (offFor / 60000) + " min asleep");
                    headUnitStarted();
                }
            } else {
                Prefs.log(context, "Received: " + a);
                headUnitStarted();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        boolean headUnit = Prefs.isHeadUnit(this);

        // Must be called quickly every time the service is started.
        Compat.startForeground(this, Compat.notification(this,
                headUnit ? "Ready to play when the car starts" : "Waiting for car audio"),
                !headUnit);

        if (headUnit) {
            startWatchingHeadUnit();
        }

        if (action == null) {
            // Restarted by Android after being killed.
            if (!headUnit) stopEverything();
            return headUnit ? START_STICKY : START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_HU_STARTED:
                headUnitStarted();
                break;
            case ACTION_HU_WATCH:
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
                play();
                break;
            case ACTION_TEST:
                runTest(headUnit);
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
        Compat.registerExported(this, headUnitReceiver, f);
    }

    private void headUnitStarted() {
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean(Prefs.ENABLED, true)) {
            Prefs.log(this, "Skipped: auto-play is off");
            return;
        }
        if (playedWithin(Prefs.HU_DEBOUNCE_MS)) {
            Prefs.log(this, "Skipped: already played in the last 3 min");
            return;
        }
        markPlayed();
        int delay = p.getInt(Prefs.HU_DELAY, 5);
        handler.removeCallbacks(playRunnable);
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
        Duas.Dua dua = Duas.selectedDua(this);
        Duas.Recording rec = Duas.audioFor(this, dua);
        if (rec == null) {
            Prefs.log(this, "No audio for \"" + dua.title + "\". Choose or add one in the app.");
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
            player.setOnCompletionListener(mp -> {
                stopPlayer();
                if (!Prefs.isHeadUnit(this)) stopEverything();
            });
            // Ask the radio / music app to go quieter while the duaa plays.
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            acquireWakeLock();
            player.start();
            markPlayed();
            Prefs.log(this, "▶ Playing: " + dua.title + " (" + rec.name + ")");
        } catch (Exception e) {
            Prefs.log(this, "Playback error: " + e.getMessage());
            stopPlayer();
            if (!Prefs.isHeadUnit(this)) stopEverything();
        }
    }

    private void stopPlayer() {
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
        stopPlayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
