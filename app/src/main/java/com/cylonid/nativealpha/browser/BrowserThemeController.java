package com.cylonid.nativealpha.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.cylonid.nativealpha.BuildConfig;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.ColorPalette;
import com.cylonid.nativealpha.util.DateUtils;
import com.cylonid.nativealpha.util.ShortcutIconUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.Calendar;

/**
 * Controller responsible for managing WebApp visual identity, theme accent colors,
 * system status bar appearance adaptation, and algorithmic dark mode / force dark strategy.
 * Aligned with Hermit Lite Apps Theme Architecture.
 */
public class BrowserThemeController {

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        @Nullable
        WebView getActiveWebView();

        @Nullable
        ProgressBar getProgressBar();

        @Nullable
        View getTitleCapsule();

        @Nullable
        View getContainerDot();

        void setLocalNightMode(int mode);
    }

    private final Activity activity;
    private final Callback callback;
    private Boolean darkModeSupportedOverride = null;

    public BrowserThemeController(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void setDarkModeSupportedOverride(@Nullable Boolean override) {
        this.darkModeSupportedOverride = override;
    }

    public boolean isDarkModeSupported() {
        if (darkModeSupportedOverride != null) {
            return darkModeSupportedOverride;
        }
        return BuildConfig.FLAVOR.contains("extended");
    }

    /**
     * Determines whether the given webapp requires forced dark mode,
     * taking into account configured timespan intervals or persistent force-dark toggle.
     */
    public boolean needsForcedDarkMode(@Nullable WebApp webapp) {
        if (webapp == null) return false;
        return (webapp.isUseTimespanDarkMode() &&
                DateUtils.isInInterval(
                        DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeBegin()),
                        Calendar.getInstance(),
                        DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeEnd())))
                || (!webapp.isUseTimespanDarkMode() && webapp.isForceDarkMode());
    }

    /**
     * Applies theme identity to window status bar, progress bar, title capsule, container dot,
     * and multi-tasking description based on the active WebApp's theme and container color.
     */
    public void applyThemeIdentity() {
        WebApp webapp = callback.getWebApp();
        if (webapp == null) return;

        int accentColor = ColorPalette.colorForTheme(webapp.getThemeColor());

        // Apply status bar color & light/dark status bar icons adaptation
        try {
            activity.getWindow().setStatusBarColor(accentColor);
            WindowInsetsControllerCompat insetsController =
                    WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
            if (insetsController != null) {
                boolean isLightBackground = ColorUtils.calculateLuminance(accentColor) > 0.5;
                insetsController.setAppearanceLightStatusBars(isLightBackground);
            }
        } catch (Throwable ignored) {}

        // Apply accent color to progress bar
        ProgressBar progressBar = callback.getProgressBar();
        if (progressBar != null) {
            progressBar.setProgressTintList(ColorStateList.valueOf(accentColor));
        }

        // Apply accent color to title capsule stroke
        View titleCapsule = callback.getTitleCapsule();
        if (titleCapsule instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) titleCapsule;
            card.setStrokeColor(accentColor);
            int strokeWidth = Math.round(1 * activity.getResources().getDisplayMetrics().density);
            card.setStrokeWidth(strokeWidth);
        }

        // Apply container sandbox color to container dot indicator
        View containerDot = callback.getContainerDot();
        if (containerDot != null) {
            int containerColor = ColorPalette.colorForContainer(webapp.getContainerId());
            containerDot.setBackgroundTintList(ColorStateList.valueOf(containerColor));
        }

        // Update Android TaskDescription for recent apps switcher
        try {
            Bitmap favicon = ShortcutIconUtils.loadFavicon(activity, webapp.getID());
            ActivityManager.TaskDescription description = favicon == null
                    ? new ActivityManager.TaskDescription(webapp.getTitle(), null, accentColor)
                    : new ActivityManager.TaskDescription(webapp.getTitle(), favicon, accentColor);
            activity.setTaskDescription(description);
        } catch (Throwable ignored) {}
    }

    /**
     * Configures WebView dark mode, algorithmic darkening, web theme darkening strategy,
     * and AppCompat local night mode.
     */
    @SuppressLint("RequiresFeature")
    public void setDarkModeIfNeeded() {
        if (!isDarkModeSupported()) {
            return;
        }

        WebApp webapp = callback.getWebApp();
        WebView wv = callback.getActiveWebView();
        if (webapp == null || wv == null) return;

        boolean needsForcedDark = needsForcedDarkMode(webapp);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean isForceDarkSupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK);
            boolean isForceDarkStrategySupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY);
            boolean isAlgorithmicDarkeningSupported = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING);

            if (needsForcedDark) {
                wv.setBackgroundColor(Color.BLACK);
                try {
                    wv.setForceDarkAllowed(true);
                } catch (Throwable ignored) {}
                callback.setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);

                try {
                    if (isForceDarkSupported) {
                        WebSettingsCompat.setForceDark(wv.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
                    }
                    if (isForceDarkStrategySupported) {
                        WebSettingsCompat.setForceDarkStrategy(wv.getSettings(),
                                WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
                    }
                    if (isAlgorithmicDarkeningSupported) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), true);
                    }
                } catch (Throwable ignored) {}
            } else {
                callback.setLocalNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                wv.setBackgroundColor(Color.WHITE);

                try {
                    if (isForceDarkSupported) {
                        WebSettingsCompat.setForceDark(wv.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
                    }
                    if (isForceDarkStrategySupported) {
                        WebSettingsCompat.setForceDarkStrategy(wv.getSettings(),
                                WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);
                    }
                    if (isAlgorithmicDarkeningSupported) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), false);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
}
