package com.sif.travelduaa;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * English / Arabic interface.
 *
 * Screen text is written in English in the code. tr() swaps it for the Arabic version when
 * Arabic is chosen. Text with numbers or names uses f() with a template, e.g.
 * f("Step %1$d of %2$d", 3, 6).
 *
 * The event log stays in English on purpose: it's for troubleshooting.
 */
public class Lang {
    private static boolean arabic = false;

    public static void load(Context c) {
        arabic = "ar".equals(Prefs.get(c).getString(Prefs.LANGUAGE, "en"));
    }

    public static boolean isArabic() { return arabic; }

    public static String tr(String en) {
        if (!arabic || en == null) return en;
        String ar = AR.get(en);
        return ar != null ? ar : en;
    }

    public static String f(String enTemplate, Object... args) {
        return String.format(Locale.ROOT, tr(enTemplate), args);
    }

    private static final Map<String, String> AR = new HashMap<>();
    private static void put(String en, String ar) { AR.put(en, ar); }

    static {
        ArabicStrings.load(AR);
    }
}
