package com.sif.travelduaa;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The app's only screen. First launch asks where the app is installed. */
public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1;
    private static final int ASK_PERMISSIONS = 2;

    private Ui ui;
    private ScrollView scroll;          // the main column (left column on wide screens)
    private LinearLayout content;
    private ScrollView detailScroll;    // right column on wide screens, else null
    private LinearLayout rootRow;       // wide screens: holds both columns
    private LinearLayout bottomBar;     // setup: Back / Next, always visible at the bottom
    private LinearLayout detail;
    private boolean wide;               // landscape screen wide enough for two columns
    private int sidePadding;            // single-column padding
    private TextView logText;
    private View logCardView;
    private boolean askedPermissions = false;
    private float widthDp;
    private int setupStep = -1;         // >= 0 while the guided setup is showing
    private static final int PAGE_MAIN = 0, PAGE_SETTINGS = 1, PAGE_LOG = 2;
    private int page = PAGE_MAIN;       // which screen is showing after setup
    private TextView nowPlayingText;    // banner text while the duaa plays
    private View nowPlayingBanner;
    private boolean lastTestPassed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Updates the event log on screen once a second, so tests show up live. */
    private final Runnable liveLog = new Runnable() {
        @Override public void run() {
            updateLog();
            updateNowPlaying();
            if (setupStep == SETUP_TEST && testPassed() != lastTestPassed) buildUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Light or dark must be chosen before the screen is created.
        if (Ui.applyTheme(this)) {
            int light = getResources().getIdentifier("AppThemeLight", "style", getPackageName());
            if (light != 0) setTheme(light);
        }
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        // e.g. after switching light / dark on the Settings screen, stay on Settings
        if (savedInstanceState != null) page = savedInstanceState.getInt("page", PAGE_MAIN);

        float density = getResources().getDisplayMetrics().density;
        widthDp = getResources().getDisplayMetrics().widthPixels / density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;
        // Head units (e.g. 1280x720) are wide and short: use two columns there.
        wide = widthDp >= 840 && widthDp > heightDp;

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = ui.vertical();
        scroll.addView(content);

        if (wide) {
            LinearLayout root = ui.row();
            root.setGravity(Gravity.TOP);
            root.setBackgroundColor(Ui.BG);
            root.setPadding(ui.dp(12), 0, ui.dp(12), 0);
            rootRow = root;
            content.setPadding(ui.dp(8), ui.dp(18), ui.dp(8), ui.dp(24));

            detailScroll = new ScrollView(this);
            detailScroll.setFillViewport(true);
            detailScroll.setVerticalScrollBarEnabled(false);
            detail = ui.vertical();
            detail.setPadding(ui.dp(8), ui.dp(18), ui.dp(8), ui.dp(24));
            detailScroll.addView(detail);

            root.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            root.addView(detailScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f));
            setContentView(withBottomBar(root));
        } else {
            scroll.setBackgroundColor(Ui.BG);
            sidePadding = (int) Math.max(16, (widthDp - 760) / 2);
            content.setPadding(ui.dp(sidePadding), ui.dp(24), ui.dp(sidePadding), ui.dp(32));
            setContentView(withBottomBar(scroll));
        }
        colourSystemBars();

        // First run: show the guided setup. Existing installs skip it (it can be re-run from Settings).
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean(Prefs.SETUP_DONE, false)) {
            if (Prefs.mode(this).isEmpty() || p.contains(Prefs.SETUP_STEP)) {
                setupStep = p.getInt(Prefs.SETUP_STEP, 0);
            } else {
                p.edit().putBoolean(Prefs.SETUP_DONE, true).apply();
            }
        }
        buildUi();
    }

    /**
     * Puts the screen above a fixed bar for the setup's Back / Next buttons, so they're
     * always visible, even on short screens like 1280x720 head units.
     */
    private View withBottomBar(View main) {
        LinearLayout outer = ui.vertical();
        outer.setBackgroundColor(Ui.BG);
        outer.addView(main, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        bottomBar = ui.row();
        bottomBar.setBackground(ui.rounded(Ui.CARD, 0, Ui.STROKE, 1));
        int side = wide ? (int) Math.max(20, (widthDp - 860) / 2) : Math.max(16, sidePadding);
        bottomBar.setPadding(ui.dp(side), ui.dp(10), ui.dp(side), ui.dp(12));
        bottomBar.setVisibility(View.GONE);
        outer.addView(bottomBar, ui.matchWidth());
        return outer;
    }

    /** Status bar and navigation bar in the theme's background colour. */
    private void colourSystemBars() {
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (Ui.light) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags |= 0x10;   // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** Dialogs in the same light / dark style as the app. */
    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, Ui.light
                ? android.R.style.Theme_Material_Light_Dialog_Alert
                : android.R.style.Theme_Material_Dialog_Alert);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(liveLog);
        // Coming back from Android settings: refresh permission / helper status.
        if (Prefs.isHeadUnit(this)) buildUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(liveLog);
    }

    private void buildUi() {
        int scrollY = scroll.getScrollY();
        int detailY = detailScroll != null ? detailScroll.getScrollY() : 0;
        content.removeAllViews();
        if (detail != null) detail.removeAllViews();
        logText = null;
        logCardView = null;
        nowPlayingBanner = null;
        nowPlayingText = null;
        bottomBar.removeAllViews();
        bottomBar.setVisibility(View.GONE);
        if (setupStep >= 0) {
            buildSetup();
        } else if (Prefs.mode(this).isEmpty()) {
            columns(false);
            buildModeChooser();
        } else {
            if (page == PAGE_SETTINGS) {
                columns(true);
                buildSettingsPage();
            } else if (page == PAGE_LOG) {
                columns(false);
                buildLogPage();
            } else {
                columns(true);
                buildMain();
            }
            if (!askedPermissions) {
                askedPermissions = true;
                askPermissions();
            }
            if (Prefs.isHeadUnit(this)) sendToService(DuaaService.ACTION_HU_WATCH);
        }
        scroll.post(() -> scroll.scrollTo(0, scrollY));
        if (detailScroll != null) detailScroll.post(() -> detailScroll.scrollTo(0, detailY));
    }

    /** Wide screens: two columns, or one centred column. Phones always use one column. */
    private void columns(boolean two) {
        if (!wide) return;
        detailScroll.setVisibility(two ? View.VISIBLE : View.GONE);
        int side = two ? 8 : (int) Math.max(8, (widthDp - 24 - 860) / 2);
        content.setPadding(ui.dp(side), ui.dp(18), ui.dp(side), ui.dp(24));
    }

    // =====================================================================
    // First screen: where is the app installed?
    // =====================================================================

    private void buildModeChooser() {
        content.addView(header(false));
        TextView q = ui.bold("Where is this app installed?", 22, Ui.TEXT);
        q.setPadding(0, ui.dp(8), 0, ui.dp(16));
        content.addView(q);
        content.addView(modeOptions());
    }

    /** The two choices, side by side on wide screens. The likely one is tagged "Suggested". */
    private View modeOptions() {
        boolean headUnitLikely = looksLikeHeadUnit();
        String current = Prefs.mode(this);
        View phone = modeOption("📱", "On my phone",
                "Plays through the car speakers when your phone connects to the car by Bluetooth.",
                Prefs.MODE_PHONE, !headUnitLikely, current.equals(Prefs.MODE_PHONE));
        View unit = modeOption("🚗", "On the car head unit",
                "Plays when the car starts or wakes up. No Bluetooth needed.",
                Prefs.MODE_HEAD_UNIT, headUnitLikely, current.equals(Prefs.MODE_HEAD_UNIT));
        LinearLayout box = ui.vertical();
        if (wide) {
            LinearLayout r = ui.row();
            r.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams a = ui.weight(1);
            LinearLayout.LayoutParams b = ui.weight(1);
            b.leftMargin = ui.dp(14);
            r.addView(phone, a);
            r.addView(unit, b);
            box.addView(r);
        } else {
            box.addView(phone);
            box.addView(unit);
        }
        return box;
    }

    /**
     * Head units are usually wide landscape screens with no real battery
     * (they often report 0 V and 0 °C).
     */
    private boolean looksLikeHeadUnit() {
        boolean fakeBattery = false;
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) {
                fakeBattery = true;
            } else {
                boolean present = b.getBooleanExtra(android.os.BatteryManager.EXTRA_PRESENT, true);
                int volts = b.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1);
                fakeBattery = !present || volts == 0;
            }
        } catch (Exception ignored) { }
        return wide || fakeBattery;
    }

    private View modeOption(String emoji, String title, String desc, String mode,
                            boolean suggested, boolean chosen) {
        LinearLayout card = ui.card(null);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ui.pressable(ui.rounded(Ui.CARD, 18, chosen ? Ui.ACCENT : Ui.STROKE, chosen ? 2 : 1)));
        card.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(22));

        TextView icon = ui.text(emoji, 40, Ui.TEXT);
        icon.setPadding(0, 0, ui.dp(18), 0);
        card.addView(icon);

        LinearLayout texts = ui.vertical();
        if (suggested) {
            TextView tag = ui.chip("Suggested for this device", Ui.ACCENT);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.bottomMargin = ui.dp(6);
            texts.addView(tag, tlp);
        }
        texts.addView(ui.bold(title, 20, Ui.TEXT));
        texts.addView(ui.text(desc, 15, Ui.MUTED));
        card.addView(texts, ui.weight(1));

        card.addView(ui.bold(chosen ? "✓" : "›", 32, Ui.ACCENT));
        card.setClickable(true);
        card.setOnClickListener(v -> setMode(mode));
        return card;
    }

    private void setMode(String mode) {
        stopService(new Intent(this, DuaaService.class));
        Prefs.get(this).edit().putString(Prefs.MODE, mode).apply();
        Prefs.log(this, "Mode set to " + (Prefs.MODE_PHONE.equals(mode) ? "phone" : "head unit"));
        if (setupStep >= 0) goStep(SETUP_DUAA); else buildUi();
    }

    // =====================================================================
    // Main screen
    // =====================================================================

    private void buildMain() {
        content.addView(header(true));
        content.addView(nowPlayingBanner());
        View checklist = checklistCard();
        if (checklist != null) content.addView(checklist);
        content.addView(duaCard());
        if (wide) {
            // Head units: Settings and Log under the "Choose the duaa" box.
            LinearLayout buttons = pageButtons();
            buttons.setPadding(0, 0, 0, ui.dp(14));
            content.addView(buttons);
            // Left: header, checklist, duaa list, buttons.  Right: the selected duaa, test.
            detail.addView(duaDetailCard());
            detail.addView(testCard());
        } else {
            content.addView(testCard());
        }
        updateNowPlaying();
    }

    /** ⚙️ Settings: everything that's set once and rarely changed. */
    private void buildSettingsPage() {
        boolean hu = Prefs.isHeadUnit(this);
        content.addView(pageHeader("Settings"));
        content.addView(installedOnCard());
        content.addView(hu ? headUnitSettingsCard() : phoneSettingsCard());
        LinearLayout right = wide ? detail : content;
        if (wide) right.addView(ui.space(64));   // line up with the left column below the title
        if (hu) right.addView(reliabilityCard());
        right.addView(appearanceCard());
        right.addView(footer());
    }

    /** 📋 Log: what happened at each car start, updated live. */
    private void buildLogPage() {
        content.addView(pageHeader("Event log"));
        TextView hint = ui.text("What the app noticed and did, newest first. Useful when the duaa didn't play.",
                15, Ui.MUTED);
        hint.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(12));
        content.addView(hint);
        logCardView = logCard();
        content.addView(logCardView);
    }

    private void showPage(int p) {
        page = p;
        buildUi();
        scroll.post(() -> scroll.scrollTo(0, 0));
        if (detailScroll != null) detailScroll.post(() -> detailScroll.scrollTo(0, 0));
    }

    /** "← Back   Title" at the top of Settings and Log. */
    private View pageHeader(String title) {
        LinearLayout h = ui.row();
        h.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(14));
        h.addView(ui.button("←  Back", Ui.SECONDARY, v -> showPage(PAGE_MAIN)));
        TextView t = ui.bold(title, wide ? 26 : 28, Ui.TEXT);
        t.setPadding(ui.dp(16), 0, 0, 0);
        h.addView(t, ui.weight(1));
        return h;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    /** Android's back button: Settings / Log → main screen; in the setup → previous step. */
    @Override
    public void onBackPressed() {
        if (setupStep > SETUP_WELCOME) {
            goStep(setupStep == SETUP_DUAA && !Prefs.mode(this).isEmpty() ? SETUP_WHERE : setupStep - 1);
        } else if (setupStep < 0 && page != PAGE_MAIN) {
            showPage(PAGE_MAIN);
        } else {
            super.onBackPressed();
        }
    }

    private View installedOnCard() {
        LinearLayout card = ui.card("Installed on");
        LinearLayout r = ui.row();
        boolean phone = Prefs.isPhone(this);
        r.addView(ui.bold(phone ? "📱  Phone" : "🚗  Head unit", 18, Ui.TEXT), ui.weight(1));
        r.addView(ui.button("Change", Ui.SECONDARY, v -> dialog()
                .setTitle("Change where the app is installed?")
                .setMessage("Opens the setup at that step. Your duaa and recordings are kept.")
                .setPositiveButton("Change", (d, w) -> { page = PAGE_MAIN; startSetup(SETUP_WHERE); })
                .setNegativeButton("Cancel", null)
                .show()));
        card.addView(r);
        return card;
    }

    // =====================================================================
    // Guided setup
    // =====================================================================

    private static final int SETUP_WELCOME = 0, SETUP_THEME = 1, SETUP_WHERE = 2, SETUP_DUAA = 3,
            SETUP_OPTIONS = 4, SETUP_TEST = 5, SETUP_FINISHED = 6;
    private static final int SETUP_STEPS = 6;   // steps after the welcome screen

    private void startSetup(int step) {
        stopService(new Intent(this, DuaaService.class));
        // A fresh setup starts with a fresh test result.
        Prefs.get(this).edit().putBoolean(Prefs.SETUP_DONE, false).remove(Prefs.TEST_SINCE).apply();
        goStep(step);
    }

    private void goStep(int step) {
        if (step == SETUP_OPTIONS || step == SETUP_TEST) {
            // The background service is needed from here on, e.g. for the test.
            if (Prefs.isHeadUnit(this)) sendToService(DuaaService.ACTION_HU_WATCH);
        }
        if (step == SETUP_OPTIONS && !askedPermissions) {
            askedPermissions = true;
            askPermissions();
        }
        setupStep = step;
        Prefs.get(this).edit().putInt(Prefs.SETUP_STEP, step).apply();
        buildUi();
        scroll.post(() -> scroll.scrollTo(0, 0));
        if (detailScroll != null) detailScroll.post(() -> detailScroll.scrollTo(0, 0));
    }

    private void finishSetup() {
        Prefs.get(this).edit().putBoolean(Prefs.SETUP_DONE, true).remove(Prefs.SETUP_STEP).apply();
        Prefs.log(this, "Setup finished");
        setupStep = -1;
        page = PAGE_MAIN;
        buildUi();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void buildSetup() {
        boolean hu = Prefs.isHeadUnit(this);
        columns(setupStep == SETUP_DUAA);   // the duaa step uses both columns on wide screens

        if (setupStep == SETUP_WELCOME) {
            buildWelcome();
            return;
        }

        // Progress: "Step 3 of 6" and dots
        LinearLayout prog = ui.row();
        prog.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(10));
        prog.addView(ui.bold("Step " + setupStep + " of " + SETUP_STEPS, 14, Ui.MUTED), ui.weight(1));
        for (int i = 1; i <= SETUP_STEPS; i++) {
            TextView dot = ui.text(i <= setupStep ? "●" : "○", 14, i <= setupStep ? Ui.ACCENT : Ui.MUTED);
            dot.setPadding(ui.dp(3), 0, ui.dp(3), 0);
            prog.addView(dot);
        }
        content.addView(prog);

        switch (setupStep) {
            case SETUP_THEME:
                setupTitle("Light or dark?", "Dark is easiest on the eyes in the car at night. "
                        + "You can change this later in Settings → Appearance.");
                LinearLayout tc = ui.card(null);
                tc.addView(themeChoices());
                content.addView(tc);
                setupNav(SETUP_WELCOME, "Next", v -> goStep(SETUP_WHERE), true);
                break;

            case SETUP_WHERE:
                setupTitle("Where is this app installed?",
                        "This decides how the app knows the car has started.");
                content.addView(modeOptions());
                setupNav(SETUP_THEME, "Next", v -> goStep(SETUP_DUAA), !Prefs.mode(this).isEmpty());
                break;

            case SETUP_DUAA: {
                setupTitle("Choose the duaa and its audio",
                        "Tap a duaa to see its Arabic text and meaning. Then pick an audio for it, "
                                + "play it, and set the volume.");
                content.addView(duaCard());
                if (wide) detail.addView(duaDetailCard());
                boolean hasAudio = Duas.audioFor(this, Duas.selectedDua(this)) != null;
                if (!hasAudio) {
                    TextView w = ui.text("⚠  This duaa has no audio yet. Add one, or pick a duaa marked 🎵 Ready.",
                            15, Ui.WARN);
                    w.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(8));
                    content.addView(w);
                }
                setupNav(SETUP_WHERE, hasAudio ? "Next" : "Continue without audio",
                        v -> goStep(SETUP_OPTIONS), true);
                break;
            }

            case SETUP_OPTIONS:
                if (hu) {
                    setupTitle("When should it play?",
                            "These defaults suit most head units. If you use CarPlay / Android Auto "
                                    + "(e.g. CPLink), choose it under \"After the duaa, open\".");
                    content.addView(headUnitSettingsCard());
                    content.addView(reliabilityCard());
                } else {
                    setupTitle("Connect your car",
                            "Add the car(s) you drive, and allow the app to run in the background.");
                    content.addView(phoneSettingsCard());
                }
                setupNav(SETUP_DUAA, "Next", v -> goStep(SETUP_TEST), true);
                break;

            case SETUP_TEST:
                buildSetupTest(hu);
                break;

            case SETUP_FINISHED:
                buildSetupDone(hu);
                break;
        }
    }

    private void buildWelcome() {
        LinearLayout box = ui.vertical();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, ui.dp(wide ? 8 : 40), 0, ui.dp(16));

        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        logo.setBackground(ui.rounded(Ui.CARD, 32, 0, 0));
        logo.setClipToOutline(true);
        int size = ui.dp(wide ? 120 : 140);
        box.addView(logo, new LinearLayout.LayoutParams(size, size));

        TextView t = ui.bold("Travel Duaa", 34, Ui.TEXT);
        t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        t.setPadding(0, ui.dp(18), 0, 0);
        box.addView(t, ui.matchWidth());
        TextView ar = ui.text("دعاء السفر", 24, Ui.GOLD);
        ar.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        box.addView(ar, ui.matchWidth());

        TextView d = ui.text("Plays the travel duaa for you every time you start the car: from your "
                + "phone over Bluetooth, or from your car's head unit.", 18, Ui.MUTED);
        d.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        d.setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(24));
        box.addView(d, ui.matchWidth());

        TextView go = ui.button("Get started  →", Ui.PRIMARY, v -> goStep(SETUP_THEME));
        go.setTextSize(19);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ui.dp(280),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        box.addView(go, glp);
        TextView time = ui.text("Takes about 2 minutes", 14, Ui.MUTED);
        time.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        time.setPadding(0, ui.dp(10), 0, 0);
        box.addView(time, ui.matchWidth());
        content.addView(box);
    }

    private void setupTitle(String title, String subtitle) {
        LinearLayout h = ui.vertical();
        h.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(16));
        h.addView(ui.bold(title, wide ? 26 : 28, Ui.TEXT));
        TextView sub = ui.text(subtitle, 16, Ui.MUTED);
        sub.setPadding(0, ui.dp(6), 0, 0);
        h.addView(sub);
        content.addView(h);
    }

    /** Back / Next buttons at the bottom of a setup step. */
    private void setupNav(int backStep, String nextLabel, View.OnClickListener next, boolean nextEnabled) {
        TextView back = ui.button("←  Back", Ui.SECONDARY, v -> goStep(backStep));
        TextView nxt = ui.button(nextLabel + "  →", nextEnabled ? Ui.PRIMARY : Ui.SECONDARY,
                nextEnabled ? next : v -> toast("Choose an option first"));
        if (!nextEnabled) nxt.setAlpha(0.5f);
        showInBottomBar(back, nxt);
    }

    private void showInBottomBar(TextView... buttons) {
        LinearLayout row = ui.buttonRow(buttons);
        row.setPadding(0, 0, 0, 0);
        bottomBar.addView(row, ui.weight(1));
        bottomBar.setVisibility(View.VISIBLE);
    }

    // ---------- Setup: final test ----------

    private boolean testPassed() {
        long since = Prefs.get(this).getLong(Prefs.TEST_SINCE, 0);
        return since > 0 && Prefs.get(this).getLong(Prefs.LAST_PLAYED, 0) >= since;
    }

    private void buildSetupTest(boolean hu) {
        setupTitle("Let's test it", hu
                ? "This checks the real thing: the duaa playing by itself when the car starts."
                : "This checks the real thing: the duaa playing by itself when the phone connects to the car.");
        long since = Prefs.get(this).getLong(Prefs.TEST_SINCE, 0);
        boolean passed = testPassed();
        lastTestPassed = passed;

        LinearLayout card = ui.card(null);
        int mins = Prefs.get(this).getInt(Prefs.HU_MIN_SLEEP_MIN, 1);
        String[] steps = hu ? new String[]{
                "Tap \"Start the test\" below.",
                "Turn the car off, and wait " + (mins == 0 ? "about 15 seconds" : "at least " + mins + " min") + ".",
                "Turn the car back on. The duaa should play by itself.",
                "Open Travel Duaa again. This screen shows the result."
        } : new String[]{
                "Sit in the car with the head unit on, and the phone paired with it.",
                "Tap \"Start the test\" below.",
                "Turn the phone's Bluetooth off, then on again.",
                "The duaa should play through the car speakers within about 10 seconds."
        };
        for (int i = 0; i < steps.length; i++) {
            LinearLayout r = ui.row();
            r.setGravity(Gravity.TOP);
            r.setPadding(0, ui.dp(4), 0, ui.dp(4));
            TextView n = ui.bold((i + 1) + ".", 17, Ui.ACCENT);
            n.setMinWidth(ui.dp(28));
            r.addView(n);
            r.addView(ui.text(steps[i], 17, Ui.TEXT), ui.weight(1));
            card.addView(r);
        }
        card.addView(ui.divider());

        // Result
        TextView status;
        if (passed) {
            status = ui.bold("✅  It worked! The duaa played by itself.", 19, Ui.ACCENT);
        } else if (since > 0) {
            status = ui.bold(hu ? "⏳  Waiting for the car to start…" : "⏳  Waiting for the phone to connect…",
                    18, Ui.TEXT);
        } else {
            status = ui.text("Not started yet.", 17, Ui.MUTED);
        }
        status.setPadding(0, ui.dp(4), 0, ui.dp(8));
        card.addView(status);

        if (!passed) {
            card.addView(ui.buttonRow(
                    ui.button(since > 0 ? "Restart the test" : "Start the test", Ui.PRIMARY, v -> {
                        Prefs.get(this).edit().putLong(Prefs.TEST_SINCE, System.currentTimeMillis()).apply();
                        Prefs.log(this, "Setup test started");
                        buildUi();
                    }),
                    ui.button("Just play it now", Ui.SECONDARY, v -> sendToService(DuaaService.ACTION_TEST))));
        }
        if (since > 0 && !passed) {
            card.addView(ui.text(hu
                    ? "Didn't play after the car started? Go back and switch on the Extra reliability helper, "
                    + "then test again. The event log in the app shows what happened."
                    : "Didn't play? Check the car is in My cars, Bluetooth is allowed, and battery use is "
                    + "Unrestricted, then test again.", 14, Ui.MUTED));
        }
        content.addView(card);
        setupNav(SETUP_OPTIONS, passed ? "Next" : "Skip for now", v -> goStep(SETUP_FINISHED), true);
    }

    // ---------- Setup: done ----------

    private void buildSetupDone(boolean hu) {
        setupTitle("You're all set  🤲", "Travel Duaa will play by itself"
                + (hu ? " when the car starts." : " when your phone connects to your car."));
        SharedPreferences p = Prefs.get(this);
        Duas.Dua d = Duas.selectedDua(this);
        Duas.Recording audio = Duas.audioFor(this, d);
        String theme = p.getString(Prefs.THEME, Ui.THEME_DARK);

        LinearLayout card = ui.card("Summary");
        summaryRow(card, "Installed on", hu ? "🚗 Head unit" : "📱 Phone");
        summaryRow(card, "Duaa", d.title);
        summaryRow(card, "Audio", audio == null ? "None yet"
                : audio.name + " (" + Duas.volume(this, audio) + "%)");
        if (hu) {
            String when = (p.getBoolean(Prefs.HU_ON_BOOT, true) ? "full start-up" : "")
                    + (p.getBoolean(Prefs.HU_ON_BOOT, true) && p.getBoolean(Prefs.HU_SCREEN_WAKE, true) ? " + " : "")
                    + (p.getBoolean(Prefs.HU_SCREEN_WAKE, true) ? "waking from sleep" : "");
            summaryRow(card, "Plays on", when.isEmpty() ? "Nothing (turned off)" : when);
            String after = p.getString(Prefs.AFTER_APP_NAME, "");
            summaryRow(card, "After the duaa", after.isEmpty() ? "Nothing" : "Open " + after);
            summaryRow(card, "Extra reliability", helperEnabled() ? "On" : "Off");
        } else {
            int cars = Prefs.cars(this).size();
            summaryRow(card, "Cars", cars == 0 ? "None yet" : String.valueOf(cars));
        }
        summaryRow(card, "Appearance", Ui.THEME_LIGHT.equals(theme) ? "Light"
                : Ui.THEME_SYSTEM.equals(theme) ? "Follow system" : "Dark");
        summaryRow(card, "Test", testPassed() ? "✅ Passed" : "Not done");
        content.addView(card);

        content.addView(ui.text("Everything can be changed later in the app, and you can run this setup "
                + "again from Settings → Appearance.", 15, Ui.MUTED));
        content.addView(ui.space(8));
        TextView back = ui.button("←  Back", Ui.SECONDARY, v -> goStep(SETUP_TEST));
        TextView done = ui.button("Finish  ✓", Ui.PRIMARY, v -> finishSetup());
        showInBottomBar(back, done);
    }

    private void summaryRow(LinearLayout card, String label, String value) {
        LinearLayout r = ui.row();
        r.setPadding(0, ui.dp(5), 0, ui.dp(5));
        TextView l = ui.text(label, 16, Ui.MUTED);
        l.setMinWidth(ui.dp(150));
        r.addView(l);
        r.addView(ui.bold(value, 16, Ui.TEXT), ui.weight(1));
        card.addView(r);
    }

    // ---------- Now playing banner ----------

    private View nowPlayingBanner() {
        LinearLayout b = ui.row();
        b.setBackground(ui.rounded(Ui.ACCENT, 16, 0, 0));
        b.setPadding(ui.dp(18), ui.dp(10), ui.dp(10), ui.dp(10));
        LinearLayout.LayoutParams lp = ui.matchWidth();
        lp.bottomMargin = ui.dp(14);
        b.setLayoutParams(lp);
        nowPlayingText = ui.bold("", 18, 0xFFFFFFFF);
        b.addView(nowPlayingText, ui.weight(1));
        TextView stop = ui.button("■  Stop", Ui.SECONDARY, v -> sendToService(DuaaService.ACTION_STOP));
        b.addView(stop);
        nowPlayingBanner = b;
        return b;
    }

    private void updateNowPlaying() {
        if (nowPlayingBanner == null) return;
        String title = DuaaService.nowPlaying;
        nowPlayingBanner.setVisibility(title == null ? View.GONE : View.VISIBLE);
        if (title != null) nowPlayingText.setText("▶  Now playing: " + title);
    }

    // ---------- Setup checklist ----------

    /** Things that would stop the duaa playing, each with a button to fix it. Null if all fine. */
    private View checklistCard() {
        List<View> items = new ArrayList<>();
        SharedPreferences p = Prefs.get(this);
        Duas.Dua d = Duas.selectedDua(this);

        if (Duas.audioFor(this, d) == null) {
            items.add(checkItem("No audio chosen for \"" + d.title + "\"", "Add audio", v -> pickFile(d)));
        }
        if (!p.getBoolean(Prefs.ENABLED, true)) {
            items.add(checkItem("Automatic playing is turned off", "Turn on", v -> {
                p.edit().putBoolean(Prefs.ENABLED, true).apply();
                buildUi();
            }));
        }
        if (Prefs.isPhone(this)) {
            if (Compat.needsBluetoothPermission(this)) {
                items.add(checkItem("Bluetooth permission not allowed", "Allow", v -> askPermissions()));
            }
            if (Prefs.cars(this).isEmpty()) {
                items.add(checkItem("No car added, so nothing plays", "Add a car", v -> addCars()));
            }
            if (!batteryUnrestricted()) {
                items.add(checkItem("Battery use is restricted: Android may stop the app",
                        "Fix", v -> openBatterySettings()));
            }
        } else {
            boolean wantsOverlay = !p.getString(Prefs.AFTER_APP, "").isEmpty()
                    || p.getBoolean(Prefs.HU_SHOW_WHILE_PLAYING, false);
            if (wantsOverlay && !canOpenOtherApps()) {
                items.add(checkItem("\"Display over other apps\" not allowed", "Allow", v -> askOverlayPermission()));
            }
            if (!p.getBoolean(Prefs.HU_ON_BOOT, true) && !p.getBoolean(Prefs.HU_SCREEN_WAKE, true)) {
                items.add(checkItem("Both \"Starts up fully\" and \"Wakes from sleep\" are off", "Turn on", v -> {
                    p.edit().putBoolean(Prefs.HU_ON_BOOT, true).putBoolean(Prefs.HU_SCREEN_WAKE, true).apply();
                    buildUi();
                }));
            }
        }
        if (items.isEmpty()) return null;

        LinearLayout card = ui.card("Needs attention");
        card.setBackground(ui.rounded(Ui.CARD, 18, Ui.WARN, 1.5f));
        for (View v : items) card.addView(v);
        return card;
    }

    private View checkItem(String text, String action, View.OnClickListener l) {
        LinearLayout r = ui.row();
        r.setPadding(0, ui.dp(4), 0, ui.dp(4));
        r.addView(ui.text("⚠  " + text, 16, Ui.TEXT), ui.weight(1));
        r.addView(ui.button(action, Ui.PRIMARY, l));
        return r;
    }

    private boolean batteryUnrestricted() {
        if (Build.VERSION.SDK_INT < 23) return true;
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            toast("Find Travel Duaa and choose \"Don't optimise\" / \"Unrestricted\"");
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    // ---------- Appearance ----------

    private View appearanceCard() {
        LinearLayout card = ui.card("Appearance");
        card.addView(themeChoices());
        card.addView(ui.divider());
        card.addView(ui.buttonRow(ui.button("Run the setup again", Ui.SECONDARY, v -> {
            page = PAGE_MAIN;
            startSetup(0);
        })));
        return card;
    }

    /** Dark / Light / Follow system. Changing it restarts the screen in the new colours. */
    private View themeChoices() {
        String current = Prefs.get(this).getString(Prefs.THEME, Ui.THEME_DARK);
        String[][] options = {
                {Ui.THEME_DARK, "🌙  Dark"},
                {Ui.THEME_LIGHT, "☀️  Light"},
                {Ui.THEME_SYSTEM, "⚙️  Follow system"}};
        TextView[] buttons = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            String key = options[i][0];
            boolean on = key.equals(current);
            buttons[i] = ui.button((on ? "✓ " : "") + options[i][1], on ? Ui.PRIMARY : Ui.SECONDARY, v -> {
                if (key.equals(Prefs.get(this).getString(Prefs.THEME, Ui.THEME_DARK))) return;
                Prefs.get(this).edit().putString(Prefs.THEME, key).apply();
                recreate();
            });
        }
        return ui.buttonRow(buttons);
    }

    /** ⚙️ Settings and 📋 Log, side by side. */
    private LinearLayout pageButtons() {
        return ui.buttonRow(
                ui.button("⚙️  Settings", Ui.SECONDARY, v -> showPage(PAGE_SETTINGS)),
                ui.button("📋  Log", Ui.SECONDARY, v -> showPage(PAGE_LOG)));
    }

    private View header(boolean showMode) {
        LinearLayout h = ui.vertical();
        h.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(wide ? 12 : 18));

        LinearLayout top = ui.row();

        // The app's logo (its launcher icon), with rounded corners
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(ui.rounded(Ui.CARD, 16, 0, 0));
        logo.setClipToOutline(true);
        int logoSize = ui.dp(wide ? 56 : 64);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoSize, logoSize);
        logoLp.rightMargin = ui.dp(14);
        top.addView(logo, logoLp);

        LinearLayout titles = ui.vertical();
        titles.addView(ui.bold("Travel Duaa", wide ? 26 : 30, Ui.TEXT));
        titles.addView(ui.text("دعاء السفر", wide ? 18 : 20, Ui.GOLD));
        top.addView(titles, ui.weight(1));

        if (showMode && !wide) {
            // Phones: Settings and Log on their own row under the title.
            h.addView(top);
            LinearLayout buttons = pageButtons();
            buttons.setPadding(0, ui.dp(12), 0, 0);
            h.addView(buttons);
            return h;
        }
        h.addView(top);
        return h;
    }

    // ---------- Duaa picker ----------

    private View duaCard() {
        LinearLayout card = ui.card("Choose the duaa");
        Duas.Dua selected = Duas.selectedDua(this);
        for (Duas.Dua d : Duas.ALL) {
            card.addView(duaOption(d, d.id.equals(selected.id)));
        }
        return card;
    }

    private View duaOption(Duas.Dua d, boolean selected) {
        LinearLayout box = ui.vertical();
        box.setBackground(ui.pressable(selected
                ? ui.rounded(Ui.CARD_2, 14, Ui.ACCENT, 2)
                : ui.rounded(Ui.CARD_2, 14, Ui.STROKE, 1)));
        box.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        LinearLayout.LayoutParams lp = ui.matchWidth();
        lp.bottomMargin = ui.dp(10);
        box.setLayoutParams(lp);

        // Top line: radio dot, titles, audio status
        LinearLayout top = ui.row();
        TextView dot = ui.text(selected ? "◉" : "○", 22, selected ? Ui.ACCENT : Ui.MUTED);
        dot.setPadding(0, 0, ui.dp(12), 0);
        top.addView(dot);

        LinearLayout titles = ui.vertical();
        titles.addView(ui.bold(d.title, 17, Ui.TEXT));
        titles.addView(ui.text(sourceLine(d), 14, Ui.MUTED));
        top.addView(titles, ui.weight(1));

        Duas.Recording audio = Duas.audioFor(this, d);
        top.addView(ui.chip(audio != null ? "🎵 Ready" : "No audio chosen", audio != null ? Ui.ACCENT : Ui.WARN));
        box.addView(top);

        if (!selected) {
            box.setClickable(true);
            box.setOnClickListener(v -> {
                Duas.selectDua(this, d);
                Prefs.log(this, "Selected: " + d.title);
                buildUi();
            });
            return box;
        }
        // Selected: on phones the details open inside the card; on wide screens they're on the right.
        if (!wide) addDuaDetails(box, d, audio, false);
        return box;
    }

    /** Right column on wide screens: the selected duaa in full. */
    private View duaDetailCard() {
        Duas.Dua d = Duas.selectedDua(this);
        Duas.Recording audio = Duas.audioFor(this, d);
        LinearLayout card = ui.card(null);
        card.setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(20));

        LinearLayout top = ui.row();
        LinearLayout titles = ui.vertical();
        titles.addView(ui.bold(d.title, 22, Ui.TEXT));
        titles.addView(ui.text(sourceLine(d), 15, Ui.MUTED));
        top.addView(titles, ui.weight(1));
        top.addView(ui.chip(audio != null ? "🎵 Ready" : "No audio chosen", audio != null ? Ui.ACCENT : Ui.WARN));
        card.addView(top);

        addDuaDetails(card, d, audio, true);
        return card;
    }

    /** Arabic text, English meaning, the audio list, and the Add / Play / Stop buttons. */
    private void addDuaDetails(LinearLayout box, Duas.Dua d, Duas.Recording audio, boolean big) {
        if (!d.text.isEmpty()) {
            TextView ar = ui.arabic(d.text, big ? 24 : 21);
            ar.setBackground(ui.rounded(Ui.BG, 12, 0, 0));
            ar.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
            LinearLayout.LayoutParams alp = ui.matchWidth();
            alp.topMargin = ui.dp(12);
            alp.bottomMargin = ui.dp(6);
            box.addView(ar, alp);

            // English meaning, underneath the Arabic
            TextView meaningLabel = ui.label("Meaning");
            meaningLabel.setPadding(0, ui.dp(8), 0, ui.dp(4));
            box.addView(meaningLabel);
            TextView en = ui.text(d.meaning, big ? 17 : 16, Ui.TEXT);
            en.setLineSpacing(ui.dp(3), 1.1f);
            box.addView(en);
        }

        // The list of audios for this duaa. Tap one to use it.
        TextView label = ui.label("Audio");
        label.setPadding(0, ui.dp(12), 0, ui.dp(6));
        box.addView(label);

        List<Duas.Recording> recs = Duas.recordingsFor(this, d);
        if (recs.isEmpty()) {
            box.addView(ui.text("No audio for this duaa yet. Tap \"＋ Add audio\" to add one.", 15, Ui.WARN));
        }
        for (Duas.Recording r : recs) {
            box.addView(audioRow(d, r, audio != null && r.key.equals(audio.key)));
        }

        box.addView(ui.buttonRow(
                ui.button("＋  Add audio", Ui.SECONDARY, v -> pickFile(d)),
                ui.button("▶  Play", Ui.PRIMARY, v -> sendToService(DuaaService.ACTION_PLAY_NOW)),
                ui.button("■  Stop", Ui.SECONDARY, v -> sendToService(DuaaService.ACTION_STOP))));
    }

    /**
     * "Sahih Muslim 1342  ·  دعاء السفر كاملاً" in that order. The Arabic part is wrapped in
     * direction marks, so mixing English and Arabic on one line doesn't shuffle the order.
     */
    private String sourceLine(Duas.Dua d) {
        return d.source + "  ·  \u2067" + d.titleAr + "\u2069";
    }

    /** One audio in a duaa's list: tap to use it; audios you added can be removed. */
    private View audioRow(Duas.Dua d, Duas.Recording r, boolean chosen) {
        LinearLayout box = ui.vertical();
        box.setBackground(chosen
                ? ui.rounded(Ui.BG, 12, Ui.ACCENT, 1)
                : ui.rounded(Ui.BG, 12, 0, 0));
        box.setPadding(ui.dp(12), ui.dp(6), ui.dp(4), ui.dp(8));
        LinearLayout.LayoutParams lp = ui.matchWidth();
        lp.bottomMargin = ui.dp(6);
        box.setLayoutParams(lp);

        // Top line: tap to use this audio
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(48));
        TextView dot = ui.text(chosen ? "◉" : "○", 20, chosen ? Ui.ACCENT : Ui.MUTED);
        dot.setPadding(0, 0, ui.dp(10), 0);
        row.addView(dot);

        LinearLayout texts = ui.vertical();
        texts.addView(chosen ? ui.bold(r.name, 16, Ui.TEXT) : ui.text(r.name, 16, Ui.TEXT));
        texts.addView(ui.text((r.isBuiltIn() ? "Built into the app" : "Added by you")
                + (chosen ? "  ·  plays when the car starts" : ""), 13, chosen ? Ui.ACCENT : Ui.MUTED));
        row.addView(texts, ui.weight(1));

        if (!r.isBuiltIn()) {
            row.addView(ui.button("✕", Ui.PLAIN, v -> dialog()
                    .setTitle("Remove \"" + r.name + "\"?")
                    .setMessage("It's deleted from this device. Your other audios stay.")
                    .setPositiveButton("Remove", (dlg, w) -> {
                        Duas.delete(this, r);
                        Prefs.log(this, "Removed audio: " + r.name);
                        buildUi();
                    })
                    .setNegativeButton("Cancel", null)
                    .show()));
        }
        if (!chosen) {
            row.setBackground(ui.pressable(ui.rounded(0x00000000, 10, 0, 0)));
            row.setClickable(true);
            row.setOnClickListener(v -> {
                Duas.chooseAudio(this, d, r);
                Prefs.log(this, d.title + " → " + r.name);
                buildUi();
            });
        }
        box.addView(row);

        // Volume slider: 10% to 200%
        LinearLayout volRow = ui.row();
        volRow.setPadding(ui.dp(30), 0, ui.dp(8), 0);
        volRow.addView(ui.text("🔉", 16, Ui.MUTED));
        SeekBar slider = new SeekBar(this);
        slider.setMax(190);                                // 0..190 → 10%..200%
        slider.setProgress(Duas.volume(this, r) - 10);
        volRow.addView(slider, ui.weight(1));
        TextView pct = ui.bold(volumeLabel(Duas.volume(this, r)), 14, Ui.TEXT);
        pct.setMinWidth(ui.dp(92));
        pct.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        volRow.addView(pct);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                int percent = progress + 10;
                pct.setText(volumeLabel(percent));
                Duas.setVolume(MainActivity.this, r, percent);
                // If this audio is playing right now, change its volume straight away.
                if (DuaaService.running) {
                    Intent i = new Intent(MainActivity.this, DuaaService.class);
                    i.setAction(DuaaService.ACTION_VOLUME);
                    i.putExtra(DuaaService.EXTRA_KEY, r.key);
                    i.putExtra(DuaaService.EXTRA_PERCENT, percent);
                    startService(i);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                Prefs.log(MainActivity.this, "Volume for " + r.name + ": " + (sb.getProgress() + 10) + "%");
            }
        });
        box.addView(volRow);
        return box;
    }

    private static String volumeLabel(int percent) {
        return percent > 100 ? percent + "% 🔊" : percent + "%";
    }

    // ---------- Test ----------

    private View testCard() {
        boolean headUnit = Prefs.isHeadUnit(this);
        LinearLayout card = ui.card("Test");
        card.addView(ui.text(headUnit
                ? "Does exactly what happens when the car starts: waits the delay you set, then plays the selected duaa."
                : "Does what happens when the phone connects to the car: checks for the car's Bluetooth audio, "
                + "waits the delay you set, then plays. If the phone isn't connected to the car, it plays on the phone.",
                16, Ui.TEXT));
        card.addView(ui.text("It ignores the \"already played recently\" rule, so you can test as often as you like. "
                + "Open 📋 Log to see each step.", 14, Ui.MUTED));
        card.addView(ui.space(8));
        card.addView(ui.buttonRow(
                ui.button(headUnit ? "🧪  Test car start" : "🧪  Test car connect", Ui.PRIMARY, v -> {
                    sendToService(DuaaService.ACTION_TEST);
                    toast("Test started");
                })));
        return card;
    }

    // ---------- Settings ----------

    private View headUnitSettingsCard() {
        SharedPreferences p = Prefs.get(this);
        LinearLayout card = ui.card("Settings");
        card.addView(ui.switchRow("Play automatically", "Turn off to stop it playing by itself.",
                p.getBoolean(Prefs.ENABLED, true),
                (b, on) -> p.edit().putBoolean(Prefs.ENABLED, on).apply()));
        card.addView(ui.divider());

        TextView when = ui.bold("Play when the head unit…", 17, Ui.TEXT);
        when.setPadding(0, ui.dp(4), 0, 0);
        card.addView(when);
        card.addView(ui.switchRow("Starts up fully",
                "After a full restart, or when it was off longer than its sleep time.",
                p.getBoolean(Prefs.HU_ON_BOOT, true),
                (b, on) -> p.edit().putBoolean(Prefs.HU_ON_BOOT, on).apply()));
        card.addView(ui.switchRow("Wakes from sleep",
                "Car turned off, then back on, without a full restart. "
                        + "This is what most head units do day to day.",
                p.getBoolean(Prefs.HU_SCREEN_WAKE, true),
                (b, on) -> p.edit().putBoolean(Prefs.HU_SCREEN_WAKE, on).apply()));
        card.addView(minutesStepper("Car must be off for at least",
                "0 min = play every time it wakes, even after a quick off and on.", Prefs.HU_MIN_SLEEP_MIN, 1));
        card.addView(ui.divider());
        card.addView(stepper("Delay after the car starts",
                "Gives the head unit time to wake its speakers.", Prefs.HU_DELAY, 5));
        card.addView(ui.divider());

        // After the duaa, open another app (e.g. CPLink / CarPlay)
        LinearLayout afterRow = ui.row();
        LinearLayout texts = ui.vertical();
        texts.addView(ui.text("After the duaa, open", 17, Ui.TEXT));
        String afterName = p.getString(Prefs.AFTER_APP_NAME, "");
        texts.addView(ui.text(afterName.isEmpty() ? "Nothing" : afterName, 14,
                afterName.isEmpty() ? Ui.MUTED : Ui.ACCENT));
        afterRow.addView(texts, ui.weight(1));
        afterRow.addView(ui.button("Choose", Ui.SECONDARY, v -> chooseAfterApp()));
        card.addView(afterRow);
        if (!afterName.isEmpty()) {
            card.addView(ui.text("Tip: turn off " + afterName + "'s own auto-start, so it doesn't open "
                    + "before the duaa has finished.", 14, Ui.MUTED));
        }
        card.addView(ui.switchRow("Show Travel Duaa while the duaa plays",
                "Brings the app to the front during the duaa (e.g. over CPLink)"
                        + (afterName.isEmpty() ? "." : ", then opens " + afterName + "."),
                p.getBoolean(Prefs.HU_SHOW_WHILE_PLAYING, false),
                (b, on) -> {
                    p.edit().putBoolean(Prefs.HU_SHOW_WHILE_PLAYING, on).apply();
                    if (on && !canOpenOtherApps()) askOverlayPermission();
                    buildUi();
                }));
        boolean wantsOverlay = !afterName.isEmpty() || p.getBoolean(Prefs.HU_SHOW_WHILE_PLAYING, false);
        if (wantsOverlay && !canOpenOtherApps()) {
            TextView warn = ui.text("⚠  These need \"Display over other apps\".", 15, Ui.WARN);
            warn.setPadding(0, ui.dp(6), 0, 0);
            card.addView(warn);
            card.addView(ui.buttonRow(ui.button("Allow it", Ui.PRIMARY, v -> askOverlayPermission())));
        }
        return card;
    }

    /**
     * Options for head units where the duaa doesn't play after waking from sleep.
     * Both run alongside the standard method; whichever notices the car start first plays it.
     */
    private View reliabilityCard() {
        LinearLayout card = ui.card("If it doesn't play after the car wakes");
        boolean on = helperEnabled();

        // Option 1: accessibility helper
        LinearLayout row = ui.row();
        LinearLayout texts = ui.vertical();
        texts.addView(ui.text("Extra reliability helper", 17, Ui.TEXT));
        texts.addView(ui.text("For head units that close apps while they sleep. Android keeps this "
                + "helper running and restarts it by itself. It doesn't read your screen.", 14, Ui.MUTED));
        row.addView(texts, ui.weight(1));
        TextView chip = ui.chip(on ? "On" : "Off", on ? Ui.ACCENT : Ui.MUTED);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = ui.dp(10);
        row.addView(chip, clp);
        card.addView(row);

        card.addView(ui.buttonRow(ui.button(on ? "Turn off in Accessibility" : "Turn on in Accessibility",
                on ? Ui.SECONDARY : Ui.PRIMARY, v -> openAccessibilitySettings())));
        if (!on) {
            card.addView(ui.text("In Accessibility, find \"Travel Duaa – car start helper\" and switch it on. "
                    + "If it's greyed out (Android 13+): open App info, tap ⋮ at the top right, choose "
                    + "\"Allow restricted settings\", then try again.", 14, Ui.MUTED));
            card.addView(ui.buttonRow(ui.button("Open App info", Ui.PLAIN, v ->
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))));
        }
        card.addView(ui.divider());

        // Option 2: last resort
        card.addView(ui.text("Last resort: \"Do not sleep\"", 17, Ui.TEXT));
        card.addView(ui.text("In your head unit's own settings, set Sleep to \"Do not sleep\" (or \"Off\"). "
                + "It then fully shuts down when the car is off, and every start is a full start-up, "
                + "which always plays the duaa. Start-up takes longer (about 20–40 s).", 14, Ui.MUTED));
        return card;
    }

    /** True if the user has switched on the accessibility helper. */
    private boolean helperEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            toast("Open Settings → Accessibility on the head unit");
        }
    }

    /** Lists the apps on the head unit; the chosen one opens when the duaa finishes. */
    private void chooseAfterApp() {
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(main, 0);
        List<String[]> apps = new ArrayList<>();   // {name, package}
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            String name = String.valueOf(ri.loadLabel(getPackageManager()));
            boolean dup = false;
            for (String[] a : apps) if (a[1].equals(pkg)) dup = true;
            if (!dup) apps.add(new String[]{name, pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));

        String[] labels = new String[apps.size() + 1];
        labels[0] = "Nothing";
        for (int i = 0; i < apps.size(); i++) labels[i + 1] = apps.get(i)[0];

        dialog()
                .setTitle("After the duaa, open…")
                .setItems(labels, (d, which) -> {
                    SharedPreferences.Editor e = Prefs.get(this).edit();
                    if (which == 0) {
                        e.remove(Prefs.AFTER_APP).remove(Prefs.AFTER_APP_NAME).apply();
                        Prefs.log(this, "After the duaa: open nothing");
                    } else {
                        String[] app = apps.get(which - 1);
                        e.putString(Prefs.AFTER_APP, app[1]).putString(Prefs.AFTER_APP_NAME, app[0]).apply();
                        Prefs.log(this, "After the duaa: open " + app[0]);
                        if (!canOpenOtherApps()) askOverlayPermission();
                    }
                    buildUi();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Android 10+ only lets background apps open other apps with "Display over other apps". */
    private boolean canOpenOtherApps() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void askOverlayPermission() {
        dialog()
                .setTitle("Allow \"Display over other apps\"")
                .setMessage("Android needs this so Travel Duaa can open apps (itself, or e.g. CPLink) "
                        + "while the car starts. "
                        + "On the next screen, turn it on for Travel Duaa, then come back.")
                .setPositiveButton("Open settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ex) {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private View phoneSettingsCard() {
        SharedPreferences p = Prefs.get(this);
        LinearLayout card = ui.card("Settings");
        card.addView(ui.switchRow("Play when the phone connects to the car", null,
                p.getBoolean(Prefs.ENABLED, true),
                (b, on) -> p.edit().putBoolean(Prefs.ENABLED, on).apply()));
        card.addView(ui.divider());

        card.addView(ui.text("My cars", 17, Ui.TEXT));
        card.addView(ui.text("The duaa only plays when one of these connects. "
                + "Headphones, speakers and other devices are ignored.", 14, Ui.MUTED));
        card.addView(ui.space(6));

        Map<String, String> cars = Prefs.cars(this);
        if (cars.isEmpty()) {
            TextView none = ui.text("⚠  No car added yet, so nothing will play automatically.", 15, Ui.WARN);
            none.setPadding(0, ui.dp(4), 0, ui.dp(4));
            card.addView(none);
        } else {
            List<Map.Entry<String, String>> sorted = new ArrayList<>(cars.entrySet());
            Collections.sort(sorted, (a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
            for (Map.Entry<String, String> car : sorted) card.addView(carRow(car.getKey(), car.getValue()));
        }
        card.addView(ui.buttonRow(ui.button("＋  Add a car", Ui.SECONDARY, v -> addCars())));
        card.addView(ui.divider());

        card.addView(stepper("Extra wait after Bluetooth audio connects",
                "The app already waits until the car's audio is ready.", Prefs.PHONE_DELAY, 2));
        card.addView(ui.divider());

        card.addView(ui.text("Keep it working in the background", 17, Ui.TEXT));
        card.addView(ui.text("Samsung, Xiaomi and Huawei phones can stop apps in the background. "
                + "Open app settings → Battery → Unrestricted.", 14, Ui.MUTED));
        card.addView(ui.buttonRow(ui.button("Open app settings", Ui.SECONDARY, v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))));
        return card;
    }

    /** "Title   [−] 5 s [+]" */
    private View stepper(String title, String subtitle, String key, int def) {
        LinearLayout r = ui.row();
        r.setPadding(0, ui.dp(4), 0, ui.dp(4));
        LinearLayout texts = ui.vertical();
        texts.addView(ui.text(title, 17, Ui.TEXT));
        texts.addView(ui.text(subtitle, 14, Ui.MUTED));
        r.addView(texts, ui.weight(1));

        TextView value = ui.bold(Prefs.get(this).getInt(key, def) + " s", 18, Ui.TEXT);
        value.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        value.setMinWidth(ui.dp(56));
        TextView minus = ui.button("−", Ui.SECONDARY, v -> value.setText(changeBy(key, def, -1) + " s"));
        TextView plus = ui.button("+", Ui.SECONDARY, v -> value.setText(changeBy(key, def, 1) + " s"));
        minus.setMinWidth(ui.dp(52));
        plus.setMinWidth(ui.dp(52));
        r.addView(minus);
        r.addView(value);
        r.addView(plus);
        return r;
    }

    /** Like stepper(), in minutes (1 to 30). */
    private View minutesStepper(String title, String subtitle, String key, int def) {
        LinearLayout r = ui.row();
        r.setPadding(0, ui.dp(4), 0, ui.dp(4));
        LinearLayout texts = ui.vertical();
        texts.addView(ui.text(title, 17, Ui.TEXT));
        texts.addView(ui.text(subtitle, 14, Ui.MUTED));
        r.addView(texts, ui.weight(1));
        TextView value = ui.bold(Prefs.get(this).getInt(key, def) + " min", 18, Ui.TEXT);
        value.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        value.setMinWidth(ui.dp(64));
        TextView minus = ui.button("−", Ui.SECONDARY, v -> value.setText(changeMinutes(key, def, -1) + " min"));
        TextView plus = ui.button("+", Ui.SECONDARY, v -> value.setText(changeMinutes(key, def, 1) + " min"));
        minus.setMinWidth(ui.dp(52));
        plus.setMinWidth(ui.dp(52));
        r.addView(minus);
        r.addView(value);
        r.addView(plus);
        return r;
    }

    private int changeMinutes(String key, int def, int by) {
        SharedPreferences p = Prefs.get(this);
        int v = Math.max(0, Math.min(30, p.getInt(key, def) + by));   // 0 = no minimum
        p.edit().putInt(key, v).apply();
        return v;
    }

    private int changeBy(String key, int def, int by) {
        SharedPreferences p = Prefs.get(this);
        int v = Math.max(0, Math.min(60, p.getInt(key, def) + by));
        p.edit().putInt(key, v).apply();
        return v;
    }

    // ---------- Log and footer ----------

    private View logCard() {
        LinearLayout card = ui.card(null);
        LinearLayout top = ui.row();
        top.addView(ui.label("Event log"), ui.weight(1));
        top.addView(ui.button("Clear", Ui.PLAIN, v -> {
            Prefs.get(this).edit().remove(Prefs.LOG).apply();
            updateLog();
        }));
        card.addView(top);

        logText = ui.text("", 13, Ui.MUTED);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        card.addView(logText);
        updateLog();
        return card;
    }

    private View footer() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        TextView t = ui.text("Version " + version, 13, Ui.MUTED);
        t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        t.setPadding(0, ui.dp(8), 0, 0);
        return t;
    }

    private void updateLog() {
        if (logText == null) return;
        String log = Prefs.get(this).getString(Prefs.LOG, "");
        String shown = log.isEmpty() ? "Nothing yet." : log.trim();
        if (!shown.contentEquals(logText.getText())) logText.setText(shown);
    }

    // =====================================================================
    // Actions
    // =====================================================================

    /** Opens the phone's file picker to add an audio for this duaa. */
    private void pickFile(Duas.Dua d) {
        Prefs.get(this).edit().putString(Prefs.PENDING_PICK, d.id).apply();
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choose an audio file"), PICK_AUDIO);
        } catch (Exception e) {
            toast("No file picker found on this device");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null) return;
        Duas.Dua d = Duas.byId(Prefs.get(this).getString(Prefs.PENDING_PICK, Duas.CUSTOM));
        File dest = Duas.newUserFile(this, d);
        // Copy the file into the app so it still works if the USB / SD card is removed.
        try (InputStream in = getContentResolver().openInputStream(data.getData());
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            toast("Could not copy the file: " + e.getMessage());
            return;
        }
        Duas.saveUserName(this, dest, fileTitle(data.getData()));

        // Added to the list. The current choice stays, unless this is the duaa's first audio.
        Prefs.log(this, "Added to " + d.title + ": " + fileTitle(data.getData()));
        Duas.selectDua(this, d);
        toast("Added to your list. Tap it to use it.");
        buildUi();
    }

    /** The picked file's name without ".mp3", used as the title for "Something else". */
    private String fileTitle(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                int dot = name.lastIndexOf('.');
                return dot > 0 ? name.substring(0, dot) : name;
            }
        } catch (Exception ignored) { }
        return "My recording";
    }

    /** One car in the list: name, and a Remove button. */
    private View carRow(String address, String name) {
        LinearLayout r = ui.row();
        r.setBackground(ui.rounded(Ui.CARD_2, 12, Ui.STROKE, 1));
        r.setPadding(ui.dp(14), ui.dp(6), ui.dp(6), ui.dp(6));
        LinearLayout.LayoutParams lp = ui.matchWidth();
        lp.bottomMargin = ui.dp(8);
        r.setLayoutParams(lp);

        TextView icon = ui.text("🚗", 20, Ui.TEXT);
        icon.setPadding(0, 0, ui.dp(12), 0);
        r.addView(icon);
        LinearLayout texts = ui.vertical();
        texts.addView(ui.bold(name, 17, Ui.TEXT));
        texts.addView(ui.text(address, 12, Ui.MUTED));
        r.addView(texts, ui.weight(1));
        r.addView(ui.button("Remove", Ui.PLAIN, v -> {
            Map<String, String> cars = Prefs.cars(this);
            cars.remove(address);
            Prefs.saveCars(this, cars);
            Prefs.log(this, "Removed car: " + name);
            buildUi();
        }));
        return r;
    }

    /**
     * Shows the phone's paired Bluetooth devices with tick boxes.
     * Audio devices (car kits, speakers) are listed first.
     */
    @SuppressWarnings("MissingPermission")
    private void addCars() {
        if (Compat.needsBluetoothPermission(this)) {
            toast("Allow the Bluetooth permission first");
            askPermissions();
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            toast("This device has no Bluetooth");
            return;
        }
        Set<BluetoothDevice> paired;
        try {
            paired = adapter.getBondedDevices();
        } catch (SecurityException e) {
            toast("Bluetooth permission needed");
            return;
        }

        Map<String, String> already = Prefs.cars(this);
        List<BluetoothDevice> audio = new ArrayList<>();
        List<BluetoothDevice> other = new ArrayList<>();
        if (paired != null) {
            for (BluetoothDevice d : paired) {
                if (already.containsKey(d.getAddress().toUpperCase())) continue;
                BluetoothClass cls = d.getBluetoothClass();
                boolean isAudio = cls != null
                        && cls.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
                (isAudio ? audio : other).add(d);
            }
        }
        List<BluetoothDevice> devices = new ArrayList<>(audio);
        devices.addAll(other);
        if (devices.isEmpty()) {
            dialog()
                    .setTitle("No devices to add")
                    .setMessage(already.isEmpty()
                            ? "Pair your phone with the car in Bluetooth settings first, then come back."
                            : "All paired devices are already added. To add another car, pair it in "
                            + "Bluetooth settings first.")
                    .setPositiveButton("Open Bluetooth settings",
                            (d, w) -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                    .setNegativeButton("Close", null)
                    .show();
            return;
        }

        String[] labels = new String[devices.size()];
        boolean[] ticked = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            String n = devices.get(i).getName();
            labels[i] = (i < audio.size() ? "🔊  " : "      ") + (n == null ? devices.get(i).getAddress() : n);
        }

        dialog()
                .setTitle("Tick your car(s)")
                .setMultiChoiceItems(labels, ticked, (d, which, isChecked) -> ticked[which] = isChecked)
                .setPositiveButton("Add", (d, w) -> {
                    Map<String, String> cars = Prefs.cars(this);
                    int added = 0;
                    for (int i = 0; i < devices.size(); i++) {
                        if (!ticked[i]) continue;
                        BluetoothDevice dev = devices.get(i);
                        String n = dev.getName() == null ? dev.getAddress() : dev.getName();
                        cars.put(dev.getAddress().toUpperCase(), n);
                        Prefs.log(this, "Added car: " + n);
                        added++;
                    }
                    Prefs.saveCars(this, cars);
                    if (added == 0) toast("Nothing ticked");
                    buildUi();
                })
                .setNeutralButton("Pair a new one", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askPermissions() {
        List<String> needed = new ArrayList<>();
        if (Prefs.isPhone(this) && Compat.needsBluetoothPermission(this)) {
            needed.add(Compat.bluetoothPermission());
        }
        if (Compat.needsNotificationPermission(this)) {
            needed.add(Compat.notificationPermission());
        }
        if (!needed.isEmpty() && Build.VERSION.SDK_INT >= 23) {
            requestPermissions(needed.toArray(new String[0]), ASK_PERMISSIONS);
        }
    }

    private void sendToService(String action) {
        Intent i = new Intent(this, DuaaService.class);
        i.setAction(action);
        Compat.startService(this, i);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
