package com.sif.travelduaa;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Saved settings plus a short event log for troubleshooting. */
public class Prefs {
    private static final String NAME = "settings";

    // Where the app is installed
    public static final String MODE = "mode";
    public static final String MODE_PHONE = "phone";
    public static final String MODE_HEAD_UNIT = "head_unit";

    // Shared settings
    public static final String ENABLED = "enabled";
    public static final String LAST_PLAYED = "last_played";
    public static final String LOG = "log";
    public static final String SELECTED_DUA = "selected_dua";
    public static final String AUDIO_FOR = "audio_for_";        // + duaa id → chosen recording
    public static final String PENDING_PICK = "pending_pick";   // which duaa a file is being added for

    // Head unit settings
    public static final String HU_DELAY = "hu_delay";              // seconds
    public static final String HU_ON_BOOT = "hu_on_boot";          // play after a full start-up
    public static final String HU_SCREEN_WAKE = "hu_screen_wake";  // play when waking from sleep
    public static final String HU_MIN_SLEEP_MIN = "hu_min_sleep";  // minutes the car must be off
    public static final String SCREEN_OFF_AT = "screen_off_at";
    public static final String LAST_ALIVE = "last_alive";          // last time the app was running
    public static final String AFTER_APP = "after_app";            // package to open after the duaa
    public static final String AFTER_APP_NAME = "after_app_name";
    public static final String HU_SHOW_WHILE_PLAYING = "hu_show_playing"; // bring app to front during duaa
    public static final String THEME = "theme";                    // dark (default) / light / system
    public static final String SETUP_DONE = "setup_done";
    public static final String SETUP_STEP = "setup_step";          // so setup resumes after a car restart
    public static final String TEST_SINCE = "test_since";          // final setup test started at

    // Phone settings
    public static final String CARS = "cars";                      // set of "MAC|Name"
    private static final String OLD_CAR_ADDRESS = "car_address";   // from the first version
    private static final String OLD_CAR_NAME = "car_name";
    public static final String PHONE_DELAY = "phone_delay";        // seconds after BT audio is ready

    /** Head unit: ACC ON + screen on together count as one start. */
    public static final long HU_DEBOUNCE_MS = 3 * 60 * 1000;
    /** Phone: a Bluetooth drop and reconnect within this time doesn't replay. */
    public static final long PHONE_DEBOUNCE_MS = 10 * 60 * 1000;
    /** Phone: longest wait for the car's Bluetooth audio to be ready. */
    public static final long PHONE_MAX_WAIT_MS = 30 * 1000;

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String mode(Context c) {
        return get(c).getString(MODE, "");
    }

    public static boolean isHeadUnit(Context c) {
        return MODE_HEAD_UNIT.equals(mode(c));
    }

    public static boolean isPhone(Context c) {
        return MODE_PHONE.equals(mode(c));
    }

    // ---------- Cars (phone mode) ----------

    /** The Bluetooth devices the duaa plays for: address → name. */
    public static Map<String, String> cars(Context c) {
        SharedPreferences p = get(c);
        Map<String, String> cars = new TreeMap<>();
        Set<String> saved = p.getStringSet(CARS, null);
        if (saved != null) {
            for (String entry : saved) {
                int bar = entry.indexOf('|');
                if (bar > 0) cars.put(entry.substring(0, bar).toUpperCase(), entry.substring(bar + 1));
            }
        } else {
            // Move a car chosen in the first version of the app into the new list.
            String oldAddress = p.getString(OLD_CAR_ADDRESS, "");
            if (!oldAddress.isEmpty()) {
                cars.put(oldAddress.toUpperCase(), p.getString(OLD_CAR_NAME, oldAddress));
                saveCars(c, cars);
            }
        }
        return cars;
    }

    public static void saveCars(Context c, Map<String, String> cars) {
        Set<String> set = new HashSet<>();
        for (Map.Entry<String, String> e : cars.entrySet()) set.add(e.getKey() + "|" + e.getValue());
        get(c).edit().putStringSet(CARS, set).remove(OLD_CAR_ADDRESS).remove(OLD_CAR_NAME).apply();
    }

    public static boolean isCar(Context c, String address) {
        return address != null && cars(c).containsKey(address.toUpperCase());
    }

    public static void log(Context c, String msg) {
        SharedPreferences p = get(c);
        String time = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.UK).format(new Date());
        String updated = time + "  " + msg + "\n" + p.getString(LOG, "");
        if (updated.length() > 4000) updated = updated.substring(0, 4000);
        p.edit().putString(LOG, updated).apply();
    }
}
