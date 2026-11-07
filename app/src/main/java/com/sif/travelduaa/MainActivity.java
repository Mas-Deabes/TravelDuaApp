package com.sif.travelduaa;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private LinearLayout detail;
    private boolean wide;               // landscape screen wide enough for two columns
    private int sidePadding;            // single-column padding
    private TextView logText;
    private View logCardView;
    private boolean askedPermissions = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Updates the event log on screen once a second, so tests show up live. */
    private final Runnable liveLog = new Runnable() {
        @Override public void run() {
            updateLog();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);

        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
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
            content.setPadding(ui.dp(8), ui.dp(18), ui.dp(8), ui.dp(24));

            detailScroll = new ScrollView(this);
            detailScroll.setFillViewport(true);
            detailScroll.setVerticalScrollBarEnabled(false);
            detail = ui.vertical();
            detail.setPadding(ui.dp(8), ui.dp(18), ui.dp(8), ui.dp(24));
            detailScroll.addView(detail);

            root.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            root.addView(detailScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f));
            setContentView(root);
        } else {
            scroll.setBackgroundColor(Ui.BG);
            sidePadding = (int) Math.max(16, (widthDp - 760) / 2);
            content.setPadding(ui.dp(sidePadding), ui.dp(24), ui.dp(sidePadding), ui.dp(32));
            setContentView(scroll);
        }
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(liveLog);
        if (Prefs.isHeadUnit(this) && !Prefs.get(this).getString(Prefs.AFTER_APP, "").isEmpty()) buildUi();
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
        if (Prefs.mode(this).isEmpty()) {
            if (detailScroll != null) detailScroll.setVisibility(View.GONE);
            buildModeChooser();
        } else {
            if (detailScroll != null) detailScroll.setVisibility(View.VISIBLE);
            buildMain();
            if (!askedPermissions) {
                askedPermissions = true;
                askPermissions();
            }
            if (Prefs.isHeadUnit(this)) sendToService(DuaaService.ACTION_HU_WATCH);
        }
        scroll.post(() -> scroll.scrollTo(0, scrollY));
        if (detailScroll != null) detailScroll.post(() -> detailScroll.scrollTo(0, detailY));
    }

    // =====================================================================
    // First screen: where is the app installed?
    // =====================================================================

    private void buildModeChooser() {
        content.addView(header(false));
        TextView q = ui.bold("Where is this app installed?", 22, Ui.TEXT);
        q.setPadding(0, ui.dp(8), 0, ui.dp(16));
        content.addView(q);

        View phone = modeOption("📱", "On my phone",
                "Plays through the car speakers when your phone connects to the car by Bluetooth.",
                Prefs.MODE_PHONE);
        View unit = modeOption("🚗", "On the car head unit",
                "Plays when the car starts or wakes up. No Bluetooth needed.",
                Prefs.MODE_HEAD_UNIT);
        if (wide) {
            LinearLayout r = ui.row();
            r.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams a = ui.weight(1);
            LinearLayout.LayoutParams b = ui.weight(1);
            b.leftMargin = ui.dp(14);
            r.addView(phone, a);
            r.addView(unit, b);
            content.addView(r);
        } else {
            content.addView(phone);
            content.addView(unit);
        }
    }

    private View modeOption(String emoji, String title, String desc, String mode) {
        LinearLayout card = ui.card(null);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ui.pressable(ui.rounded(Ui.CARD, 18, Ui.STROKE, 1)));
        card.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(22));

        TextView icon = ui.text(emoji, 40, Ui.TEXT);
        icon.setPadding(0, 0, ui.dp(18), 0);
        card.addView(icon);

        LinearLayout texts = ui.vertical();
        texts.addView(ui.bold(title, 20, Ui.TEXT));
        texts.addView(ui.text(desc, 15, Ui.MUTED));
        card.addView(texts, ui.weight(1));

        card.addView(ui.bold("›", 32, Ui.ACCENT));
        card.setClickable(true);
        card.setOnClickListener(v -> setMode(mode));
        return card;
    }

    private void setMode(String mode) {
        stopService(new Intent(this, DuaaService.class));
        Prefs.get(this).edit().putString(Prefs.MODE, mode).apply();
        Prefs.log(this, "Mode set to " + (Prefs.MODE_PHONE.equals(mode) ? "phone" : "head unit"));
        buildUi();
    }

    // =====================================================================
    // Main screen
    // =====================================================================

    private void buildMain() {
        if (wide) {
            // Left: header, duaa list, settings, log.  Right: the selected duaa, test.
            content.addView(header(true));
            content.addView(duaCard());
            content.addView(Prefs.isPhone(this) ? phoneSettingsCard() : headUnitSettingsCard());
            logCardView = logCard();
            content.addView(logCardView);
            content.addView(footer());

            detail.addView(duaDetailCard());
            detail.addView(testCard());
        } else {
            content.addView(header(true));
            content.addView(duaCard());
            content.addView(testCard());
            content.addView(Prefs.isPhone(this) ? phoneSettingsCard() : headUnitSettingsCard());
            logCardView = logCard();
            content.addView(logCardView);
            content.addView(footer());
        }
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

        if (showMode) {
            boolean phone = Prefs.isPhone(this);
            TextView chip = ui.chip(phone ? "📱 Phone  ·  Change" : "🚗 Head unit  ·  Change", Ui.ACCENT);
            chip.setClickable(true);
            chip.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Change where the app is installed?")
                    .setMessage("You'll go back to the first screen. Your duaa and recordings are kept.")
                    .setPositiveButton("Change", (d, w) -> {
                        stopService(new Intent(this, DuaaService.class));
                        Prefs.get(this).edit().remove(Prefs.MODE).apply();
                        buildUi();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            top.addView(chip);
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
        LinearLayout row = ui.row();
        row.setBackground(ui.pressable(chosen
                ? ui.rounded(Ui.BG, 12, Ui.ACCENT, 1)
                : ui.rounded(Ui.BG, 12, 0, 0)));
        row.setPadding(ui.dp(12), ui.dp(6), ui.dp(4), ui.dp(6));
        row.setMinimumHeight(ui.dp(52));
        LinearLayout.LayoutParams lp = ui.matchWidth();
        lp.bottomMargin = ui.dp(6);
        row.setLayoutParams(lp);

        TextView dot = ui.text(chosen ? "◉" : "○", 20, chosen ? Ui.ACCENT : Ui.MUTED);
        dot.setPadding(0, 0, ui.dp(10), 0);
        row.addView(dot);

        LinearLayout texts = ui.vertical();
        texts.addView(chosen ? ui.bold(r.name, 16, Ui.TEXT) : ui.text(r.name, 16, Ui.TEXT));
        texts.addView(ui.text((r.isBuiltIn() ? "Built into the app" : "Added by you")
                + (chosen ? "  ·  plays when the car starts" : ""), 13, chosen ? Ui.ACCENT : Ui.MUTED));
        row.addView(texts, ui.weight(1));

        if (!r.isBuiltIn()) {
            row.addView(ui.button("✕", Ui.PLAIN, v -> new AlertDialog.Builder(this)
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
            row.setClickable(true);
            row.setOnClickListener(v -> {
                Duas.chooseAudio(this, d, r);
                Prefs.log(this, d.title + " → " + r.name);
                buildUi();
            });
        }
        return row;
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
                + "Watch the event log below.", 14, Ui.MUTED));
        card.addView(ui.space(8));
        card.addView(ui.buttonRow(
                ui.button(headUnit ? "🧪  Test car start" : "🧪  Test car connect", Ui.PRIMARY, v -> {
                    sendToService(DuaaService.ACTION_TEST);
                    toast("Test started");
                    // Jump to the log so the steps can be seen.
                    if (logCardView != null) scroll.post(() -> scroll.smoothScrollTo(0, logCardView.getTop()));
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
                "So switching off and straight back on doesn't replay it.", Prefs.HU_MIN_SLEEP_MIN, 1));
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
            if (!canOpenOtherApps()) {
                TextView warn = ui.text("⚠  Needs \"Display over other apps\" to open " + afterName + ".", 15, Ui.WARN);
                warn.setPadding(0, ui.dp(6), 0, 0);
                card.addView(warn);
                card.addView(ui.buttonRow(ui.button("Allow it", Ui.PRIMARY, v -> askOverlayPermission())));
            }
        }
        return card;
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

        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
                .setTitle("Allow \"Display over other apps\"")
                .setMessage("Android needs this so Travel Duaa can open another app after the duaa. "
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
        int v = Math.max(1, Math.min(30, p.getInt(key, def) + by));
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
            new AlertDialog.Builder(this)
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

        new AlertDialog.Builder(this)
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
