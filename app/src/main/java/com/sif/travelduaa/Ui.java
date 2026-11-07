package com.sif.travelduaa;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Colours and small building blocks for the screen, so MainActivity stays readable. */
public class Ui {
    // Dark green theme, easy to read in a car at night
    public static final int BG = 0xFF0E1714;
    public static final int CARD = 0xFF16231E;
    public static final int CARD_2 = 0xFF1D2D27;
    public static final int STROKE = 0xFF2A3D35;
    public static final int ACCENT = 0xFF1FA37A;
    public static final int GOLD = 0xFFF2C94C;
    public static final int TEXT = 0xFFEAF2EE;
    public static final int MUTED = 0xFF9DB2A8;
    public static final int WARN = 0xFFE8A13A;

    public static final int PRIMARY = 0, SECONDARY = 1, PLAIN = 2;

    private final Context c;
    private final float density;

    public Ui(Context c) {
        this.c = c;
        this.density = c.getResources().getDisplayMetrics().density;
    }

    public int dp(float v) { return Math.round(v * density); }

    public GradientDrawable rounded(int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    /** Adds the touch ripple effect on top of a background. */
    public Drawable pressable(Drawable bg) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null);
    }

    // ---------- Layout ----------

    public LinearLayout vertical() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** A rounded box that groups one section of the screen. */
    public LinearLayout card(String label) {
        LinearLayout card = vertical();
        card.setBackground(rounded(CARD, 18, STROKE, 1));
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        if (label != null) card.addView(label(label));
        return card;
    }

    public LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, w);
        return lp;
    }

    public View space(int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    // ---------- Text ----------

    public TextView text(String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        // Line up with the screen's start (left), even if the text begins with Arabic.
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    public TextView bold(String s, float size, int color) {
        TextView t = text(s, size, color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Small grey heading at the top of a card. */
    public TextView label(String s) {
        TextView t = bold(s.toUpperCase(), 13, MUTED);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    /** Arabic text, right-to-left, larger and spaced out for reading. */
    public TextView arabic(String s, float size) {
        TextView t = text(s, size, TEXT);
        t.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setLineSpacing(dp(6), 1.1f);
        return t;
    }

    // ---------- Controls ----------

    public TextView button(String s, int style, View.OnClickListener l) {
        TextView b = bold(s, 16, style == PRIMARY ? 0xFFFFFFFF : (style == SECONDARY ? TEXT : ACCENT));
        b.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setMinHeight(dp(52));   // big enough to tap while in a car
        if (style == PRIMARY) {
            b.setBackground(pressable(rounded(ACCENT, 14, 0, 0)));
        } else if (style == SECONDARY) {
            b.setBackground(pressable(rounded(CARD_2, 14, STROKE, 1)));
        } else {
            b.setBackground(pressable(rounded(0x00000000, 14, 0, 0)));
        }
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Several buttons side by side, sharing the width equally. */
    public LinearLayout buttonRow(TextView... buttons) {
        LinearLayout r = row();
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams lp = weight(1);
            if (i > 0) lp.leftMargin = dp(8);
            r.addView(buttons[i], lp);
        }
        r.setPadding(0, dp(6), 0, dp(4));
        return r;
    }

    public TextView chip(String s, int color) {
        TextView t = bold(s, 13, color);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(rounded((color & 0x00FFFFFF) | 0x26000000, 20, 0, 0));
        return t;
    }

    /** Title + description on the left, on/off switch on the right. */
    public LinearLayout switchRow(String title, String subtitle, boolean on,
                                  android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        LinearLayout texts = vertical();
        texts.addView(text(title, 17, TEXT));
        if (subtitle != null) texts.addView(text(subtitle, 14, MUTED));
        r.addView(texts, weight(1));
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        s.setPadding(dp(12), 0, 0, 0);
        r.addView(s);
        return r;
    }

    public View divider() {
        View v = new View(c);
        v.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        v.setLayoutParams(lp);
        return v;
    }
}
