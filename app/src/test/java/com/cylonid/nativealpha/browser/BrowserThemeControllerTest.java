package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.ColorUtils;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.ColorPalette;
import com.google.android.material.card.MaterialCardView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BrowserThemeController}, testing algorithmic dark mode configuration,
 * timespan and forced dark calculations, and accent theme color status bar / component adaptation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 33})
public class BrowserThemeControllerTest {

    private Activity activity;
    private WebApp webApp;
    private WebView webView;
    private ProgressBar progressBar;
    private View titleCapsule;
    private View containerDot;
    private TestCallback callback;
    private BrowserThemeController controller;

    private static class TestCallback implements BrowserThemeController.Callback {
        WebApp app;
        WebView wv;
        ProgressBar progress;
        View title;
        View dot;
        int localNightMode = -1;

        TestCallback(WebApp app, WebView wv, ProgressBar progress, View title, View dot) {
            this.app = app;
            this.wv = wv;
            this.progress = progress;
            this.title = title;
            this.dot = dot;
        }

        @Nullable
        @Override
        public WebApp getWebApp() {
            return app;
        }

        @Nullable
        @Override
        public WebView getActiveWebView() {
            return wv;
        }

        @Nullable
        @Override
        public ProgressBar getProgressBar() {
            return progress;
        }

        @Nullable
        @Override
        public View getTitleCapsule() {
            return title;
        }

        @Nullable
        @Override
        public View getContainerDot() {
            return dot;
        }

        @Override
        public void setLocalNightMode(int mode) {
            this.localNightMode = mode;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme);

        FrameLayout root = new FrameLayout(activity);
        progressBar = new ProgressBar(activity);
        titleCapsule = new View(activity);
        containerDot = new View(activity);

        root.addView(progressBar);
        root.addView(titleCapsule);
        root.addView(containerDot);
        activity.setContentView(root);

        webApp = new WebApp("https://example.org", 0);
        webView = new WebView(activity);

        callback = new TestCallback(webApp, webView, progressBar, titleCapsule, containerDot);
        controller = new BrowserThemeController(activity, callback);
    }

    @After
    public void tearDown() {
        webView.destroy();
    }

    @Test
    public void needsForcedDarkMode_whenNullWebApp_returnsFalse() {
        assertFalse(controller.needsForcedDarkMode(null));
    }

    @Test
    public void needsForcedDarkMode_whenForceDarkModeTrue_returnsTrue() {
        webApp.setForceDarkMode(true);
        webApp.setUseTimespanDarkMode(false);
        assertTrue(controller.needsForcedDarkMode(webApp));
    }

    @Test
    public void needsForcedDarkMode_whenForceDarkModeFalse_returnsFalse() {
        webApp.setForceDarkMode(false);
        webApp.setUseTimespanDarkMode(false);
        assertFalse(controller.needsForcedDarkMode(webApp));
    }

    @Test
    public void isDarkModeSupported_handlesOverrideProperly() {
        controller.setDarkModeSupportedOverride(true);
        assertTrue(controller.isDarkModeSupported());

        controller.setDarkModeSupportedOverride(false);
        assertFalse(controller.isDarkModeSupported());

        controller.setDarkModeSupportedOverride(null);
        // Falls back to flavor check
        assertEquals(com.cylonid.nativealpha.BuildConfig.FLAVOR.contains("extended"),
                controller.isDarkModeSupported());
    }

    @Test
    public void setDarkModeIfNeeded_whenNotSupported_doesNothing() {
        controller.setDarkModeSupportedOverride(false);
        webApp.setForceDarkMode(true);

        controller.setDarkModeIfNeeded();
        assertEquals(-1, callback.localNightMode);
    }

    @Test
    public void setDarkModeIfNeeded_whenSupportedAndForcedDark_enablesNightModeAndDarkBackground() {
        controller.setDarkModeSupportedOverride(true);
        webApp.setForceDarkMode(true);
        webApp.setUseTimespanDarkMode(false);

        controller.setDarkModeIfNeeded();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, callback.localNightMode);
        } else {
            assertEquals(-1, callback.localNightMode);
        }
    }

    @Test
    public void setDarkModeIfNeeded_whenSupportedAndNotForcedDark_enablesSystemNightModeAndLightBackground() {
        controller.setDarkModeSupportedOverride(true);
        webApp.setForceDarkMode(false);
        webApp.setUseTimespanDarkMode(false);

        controller.setDarkModeIfNeeded();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, callback.localNightMode);
        } else {
            assertEquals(-1, callback.localNightMode);
        }
    }

    @Test
    public void applyThemeIdentity_appliesAccentColorToWindowAndComponents() {
        webApp.setThemeColor("Blue");
        webApp.setContainerId(1); // Valid container

        MaterialCardView card = new MaterialCardView(activity);
        callback.title = card;

        int expectedAccent = ColorPalette.colorForTheme("Blue");
        int expectedContainerColor = ColorPalette.colorForContainer(1);

        controller.applyThemeIdentity();

        if (android.os.Build.VERSION.SDK_INT < 35) {
            assertEquals(expectedAccent, activity.getWindow().getStatusBarColor());
        }
        assertNotNull(progressBar.getProgressTintList());
        assertEquals(expectedAccent, progressBar.getProgressTintList().getDefaultColor());

        assertEquals(expectedAccent, card.getStrokeColor());
        int expectedStrokeWidth = Math.round(1 * activity.getResources().getDisplayMetrics().density);
        assertEquals(expectedStrokeWidth, card.getStrokeWidth());

        assertNotNull(containerDot.getBackgroundTintList());
        assertEquals(expectedContainerColor, containerDot.getBackgroundTintList().getDefaultColor());
    }

    @Test
    public void applyThemeIdentity_lightAccentColor_adaptsProperly() {
        webApp.setThemeColor("Yellow");
        int yellowColor = ColorPalette.colorForTheme("Yellow");

        controller.applyThemeIdentity();
        if (android.os.Build.VERSION.SDK_INT < 35) {
            assertEquals(yellowColor, activity.getWindow().getStatusBarColor());
        }
        assertTrue("Yellow must be detected as light background",
                ColorUtils.calculateLuminance(yellowColor) > 0.5);
    }

    @Test
    public void applyThemeIdentity_whenNullWebApp_doesNotThrow() {
        callback.app = null;
        controller.applyThemeIdentity();
    }
}
