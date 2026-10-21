package com.cylonid.nativealpha.browser;

import android.content.res.ColorStateList;
import android.graphics.Color;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

/** Shared reading colors for the article, toolbar and contextual translation. */
public final class ReaderAppearance {
    public final int paper, surface, ink, muted, accent, line, error;
    private ReaderAppearance(int paper, int surface, int ink, int muted, int accent, int line, int error) {
        this.paper = paper; this.surface = surface; this.ink = ink; this.muted = muted;
        this.accent = accent; this.line = line; this.error = error;
    }
    private static final ReaderAppearance[] THEMES = {
        new ReaderAppearance(0xfffafaf7, 0xffffffff, 0xff202124, 0xff606762, 0xff365e96, 0xffd7dad4, 0xff9f241f),
        new ReaderAppearance(0xfff4ecd8, 0xfffaf4e8, 0xff463a2a, 0xff685c4b, 0xff76511f, 0xffd6cbb6, 0xff9f241f),
        new ReaderAppearance(0xff202124, 0xff292b2e, 0xffe4e4e4, 0xffb4b7b9, 0xff9fc3ff, 0xff474b4f, 0xffffb4ab),
        new ReaderAppearance(0xff000000, 0xff141414, 0xffdddddd, 0xffaab0b6, 0xff9fc3ff, 0xff3b3f43, 0xffffb4ab)
    };
    public static ReaderAppearance of(int theme) { return THEMES[Math.max(0, Math.min(theme, 3))]; }
    public static String css(int color) { return String.format(Locale.ROOT, "#%06x", color & 0xffffff); }
    public void styleAction(MaterialButton button) {
        ColorStateList colors = new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{muted, accent});
        button.setTextColor(colors);
        button.setIconTint(colors);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setRippleColor(ColorStateList.valueOf((accent & 0xffffff) | 0x20000000));
    }
}
