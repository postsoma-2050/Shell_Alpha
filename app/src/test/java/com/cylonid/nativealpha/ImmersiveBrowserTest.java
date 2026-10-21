package com.cylonid.nativealpha;

import android.content.Intent;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.cylonid.nativealpha.model.WebApp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class ImmersiveBrowserTest {
    @Test public void systemBarsAndKeyboardKeepBrowserAndDrawerInsideSafeArea() {
        com.cylonid.nativealpha.model.DataManager manager = com.cylonid.nativealpha.model.DataManager.getInstance();
        manager.loadAppData();
        manager.getWebsites().clear();
        WebViewActivity activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);
        WebApp app = new WebApp("https://example.org", 0);
        app.setFrameless(false);
        manager.addWebsite(app);
        ReflectionHelpers.setField(activity, "webapp", app);
        ReflectionHelpers.setField(activity, "wv", activity.findViewById(R.id.webview));
        ReflectionHelpers.callInstanceMethod(activity, "setupHermitShell");
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");

        View root = activity.findViewById(R.id.browserWindowRoot);
        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
        // Before API 30, IME detection needs an attached window; synthetic per-type
        // insets do not survive platform dispatch in this unattached layout test.
        int[] keyboardHeights = Build.VERSION.SDK_INT >= 30 ? new int[]{0, 240, 0} : new int[]{0};
        for (int keyboard : keyboardHeights) {
            WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 32, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(12, 0, 0, 24))
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(12, 0, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
                    .build();
            ViewCompat.dispatchApplyWindowInsets(root, insets);
            measure(root, 360, 720);
            int safeBottom = 720 - Math.max(24, keyboard);
            assertInsideSafeArea(drawer, activity.findViewById(R.id.browser_toolbar_container), safeBottom);
            assertInsideSafeArea(drawer, activity.findViewById(R.id.browserWebContent), safeBottom);
            drawer.openDrawer(GravityCompat.END, false);
            measure(root, 360, 720);
            assertInsideSafeArea(drawer, activity.findViewById(R.id.quick_settings_drawer), safeBottom);
            assertInsideSafeArea(drawer, activity.findViewById(R.id.quickSettingsNavigation), safeBottom);
            drawer.closeDrawer(GravityCompat.END, false);
        }
        ((android.webkit.WebView) activity.findViewById(R.id.webview)).destroy();
        ((android.webkit.WebView) activity.findViewById(R.id.adblockwebview)).destroy();
    }

    private void assertDrawerOpening(DrawerLayout drawer) {
        // These layout fixtures are unattached, so ViewDragHelper's real frame loop
        // does not finish. Assert the requested opening state; device checks cover display.
        View panel = drawer.findViewById(R.id.quick_settings_drawer);
        int state = ReflectionHelpers.getField(panel.getLayoutParams(), "openState");
        assertTrue("Settings request must begin opening the drawer",
                drawer.isDrawerOpen(GravityCompat.END) || (state & 2) != 0);
    }

    private void assertInsideSafeArea(DrawerLayout drawer, View child, int bottom) {
        Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
        drawer.offsetDescendantRectToMyCoords(child, bounds);
        bounds.offset(drawer.getLeft(), drawer.getTop());
        assertTrue("Control must have usable height", bounds.height() > 0);
        assertTrue("Control overlaps cutout: " + bounds, bounds.left >= 12);
        assertTrue("Control overlaps status bar: " + bounds, bounds.top >= 32);
        assertTrue("Control overlaps navigation bar or keyboard: " + bounds, bounds.bottom <= bottom);
    }

    @Test public void allDisplayModesReserveChromeSpaceAndKeepPageTappable() {
        for (boolean fullscreen : new boolean[]{false, true}) {
            for (boolean frameless : new boolean[]{false, true}) {
                for (boolean bottom : new boolean[]{false, true}) {
                    checkBrowser(360, 720, fullscreen, frameless, bottom);
                    checkBrowser(720, 360, fullscreen, frameless, bottom);
                }
            }
        }
    }

    @Test public void mediaCustomViewClearsGestureExclusionAndRestoresItWhenHidden() {
        if (Build.VERSION.SDK_INT < 29) return;
        WebViewActivity activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);
        WebApp app = new WebApp("https://example.org", 0);
        app.setFrameless(false);
        ReflectionHelpers.setField(activity, "webapp", app);
        View toolbar = activity.findViewById(R.id.browser_toolbar_container);
        ReflectionHelpers.setField(activity, "browserToolbarContainer", toolbar);
        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
        measure(drawer, 360, 720);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        measure(drawer, 360, 720);

        View shell = activity.findViewById(R.id.webviewActivity);
        assertEquals(1, shell.getSystemGestureExclusionRects().size());

        View media = new View(activity);
        ReflectionHelpers.setField(activity, "mediaCustomView", media);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        assertEquals(0, shell.getSystemGestureExclusionRects().size());

        ReflectionHelpers.setField(activity, "mediaCustomView", null);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        assertEquals(1, shell.getSystemGestureExclusionRects().size());

        ((android.webkit.WebView) activity.findViewById(R.id.webview)).destroy();
        ((android.webkit.WebView) activity.findViewById(R.id.adblockwebview)).destroy();
    }

    @Test public void quickSettingsControlsAndDrawerOpenInBothFramelessAndToolbarModes() {
        for (boolean frameless : new boolean[]{false, true}) {
            com.cylonid.nativealpha.model.DataManager manager = com.cylonid.nativealpha.model.DataManager.getInstance();
            manager.loadAppData();
            manager.getWebsites().clear();
            WebViewActivity activity = Robolectric.buildActivity(WebViewActivity.class).get();
            activity.setTheme(R.style.AppTheme);
            activity.setContentView(R.layout.full_webview);
            WebApp app = new WebApp("https://example.org", 0);
            app.setFrameless(frameless);
            manager.addWebsite(app);
            ReflectionHelpers.setField(activity, "webapp", app);
            View wv = activity.findViewById(R.id.webview);
            ReflectionHelpers.setField(activity, "wv", wv);
            ReflectionHelpers.callInstanceMethod(activity, "setupHermitShell");
            ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");

            DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
            measure(drawer, 360, 720);

            if (frameless) {
                View restore = activity.findViewById(R.id.btnBrowserRestoreControls);
                assertEquals(View.GONE, restore.getVisibility());
                assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));

                assertEquals(View.VISIBLE, activity.findViewById(R.id.framelessPageTools).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsScroll).getVisibility());
                // MENU opens only webpage tools in frameless.
                boolean menuHandled = activity.onKeyDown(KeyEvent.KEYCODE_MENU,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                assertTrue(menuHandled);
                assertDrawerOpening(drawer);
                drawer.closeDrawer(GravityCompat.END, false);

                // Back without history launches MainActivity and finishes
                activity.onBackPressed();
                assertTrue(activity.isFinishing());
                Intent next = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
                assertNotNull(next);
                assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
                assertFalse(drawer.isDrawerOpen(GravityCompat.END));
            } else {
                assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));
                View titleCapsule = activity.findViewById(R.id.browserTitleCapsule);
                assertNotNull(titleCapsule);
                titleCapsule.performClick();
                drawer.computeScroll();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
                drawer.computeScroll();
                ShadowLooper.idleMainLooper();
                assertDrawerOpening(drawer);
                drawer.closeDrawer(GravityCompat.END, false);
                ShadowLooper.idleMainLooper();

                // Framed mode MENU opens drawer
                boolean menuHandled = activity.onKeyDown(KeyEvent.KEYCODE_MENU,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                assertTrue(menuHandled);
                drawer.computeScroll();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
                drawer.computeScroll();
                ShadowLooper.idleMainLooper();
                assertDrawerOpening(drawer);
                drawer.closeDrawer(GravityCompat.END, false);
                ShadowLooper.idleMainLooper();

                View quickSettingsBtn = activity.findViewById(R.id.btnToolbarQuickSettings);
                quickSettingsBtn.performClick();
                drawer.computeScroll();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
                drawer.computeScroll();
                ShadowLooper.idleMainLooper();
                assertDrawerOpening(drawer);
                drawer.closeDrawer(GravityCompat.END, false);
                ShadowLooper.idleMainLooper();
            }

            if (!frameless) {
                // Quick action cards (available in framed mode drawer)
                View cardDesktop = activity.findViewById(R.id.cardQuickDesktop);
                boolean oldDesktop = app.isRequestDesktop();
                cardDesktop.performClick();
                assertEquals(!oldDesktop, app.isRequestDesktop());

                View cardDarkMode = activity.findViewById(R.id.cardQuickDarkMode);
                boolean oldDark = app.isForceDarkMode();
                cardDarkMode.performClick();
                assertEquals(!oldDark, app.isForceDarkMode());

                View cardFrameless = activity.findViewById(R.id.cardQuickFrameless);
                boolean oldFrameless = app.isFrameless();
                cardFrameless.performClick();
                assertEquals(!oldFrameless, app.isFrameless());

                // Bottom navigation buttons
                View btnHome = activity.findViewById(R.id.btnQuickHome);
                View btnBack = activity.findViewById(R.id.btnQuickBack);
                View btnRefresh = activity.findViewById(R.id.btnQuickRefresh);
                View btnForward = activity.findViewById(R.id.btnQuickForward);
                View btnMoreSettings = activity.findViewById(R.id.btnQuickMoreSettings);
                assertNotNull(btnHome);
                assertNotNull(btnBack);
                assertNotNull(btnRefresh);
                assertNotNull(btnForward);
                assertNotNull(btnMoreSettings);
            }

            ((android.webkit.WebView) activity.findViewById(R.id.webview)).destroy();
            ((android.webkit.WebView) activity.findViewById(R.id.adblockwebview)).destroy();
        }
    }

    private void checkBrowser(int width, int height, boolean fullscreen, boolean frameless,
                              boolean bottomToolbar) {
        WebViewActivity activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);
        WebApp app = new WebApp("https://example.org", 0);
        app.setShowFullscreen(fullscreen);
        app.setFrameless(frameless);
        ReflectionHelpers.setField(activity, "webapp", app);
        View toolbar = activity.findViewById(R.id.browser_toolbar_container);
        ReflectionHelpers.setField(activity, "browserToolbarContainer", toolbar);
        ConstraintLayout.LayoutParams toolbarParams = (ConstraintLayout.LayoutParams) toolbar.getLayoutParams();
        toolbarParams.topToTop = bottomToolbar ? ConstraintLayout.LayoutParams.UNSET : 0;
        toolbarParams.bottomToBottom = bottomToolbar ? 0 : ConstraintLayout.LayoutParams.UNSET;
        toolbar.setLayoutParams(toolbarParams);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");

        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
        ReflectionHelpers.setField(activity, "drawerLayout", drawer);
        FrameLayout content = activity.findViewById(R.id.browserWebContent);
        View website = new View(activity);
        content.addView(website, new FrameLayout.LayoutParams(-1, -1));
        measure(drawer, width, height);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        measure(drawer, width, height);
        View restore = activity.findViewById(R.id.btnBrowserRestoreControls);
        assertEquals(frameless ? View.GONE : View.VISIBLE, toolbar.getVisibility());
        assertEquals(View.GONE, restore.getVisibility());
        assertTrue(content.getHeight() > 0);
        if (frameless) {
            assertEquals("Web content bottom must fill parent height in frameless mode", drawer.getHeight(), content.getBottom());
        } else if (bottomToolbar) {
            assertTrue(content.getBottom() <= toolbar.getTop());
        } else {
            assertTrue(content.getTop() >= toolbar.getBottom());
        }

        AtomicInteger taps = new AtomicInteger();
        website.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) taps.incrementAndGet();
            return true;
        });
        // These activities are intentionally unattached: screen coordinates are not defined.
        Rect click = new Rect(website.getWidth() - 30, 20, website.getWidth() - 29, 21);
        drawer.offsetDescendantRectToMyCoords(website, click);
        float x = click.left;
        float y = click.top;
        sendTouch(drawer, 0, MotionEvent.ACTION_DOWN, x, y);
        sendTouch(drawer, 30, MotionEvent.ACTION_UP, x, y);
        assertEquals(1, taps.get());

        if (frameless) {
            assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));
            boolean menuHandled = activity.onKeyDown(KeyEvent.KEYCODE_MENU,
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
            assertTrue(menuHandled);
            assertDrawerOpening(drawer);
            assertFalse(activity.findViewById(R.id.btnQuickMoreSettings).isShown());
            drawer.closeDrawer(GravityCompat.END, false);
        } else {
            assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));
            boolean menuHandled = activity.onKeyDown(KeyEvent.KEYCODE_MENU,
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
            assertTrue(menuHandled);
            drawer.computeScroll();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
            drawer.computeScroll();
            ShadowLooper.idleMainLooper();
            assertDrawerOpening(drawer);
            drawer.closeDrawer(GravityCompat.END, false);
            ShadowLooper.idleMainLooper();
            assertFalse(drawer.isDrawerOpen(GravityCompat.END));
        }
        if (Build.VERSION.SDK_INT >= 29) {
            View shell = activity.findViewById(R.id.webviewActivity);
            List<Rect> exclusions = shell.getSystemGestureExclusionRects();
            {
                assertEquals(1, exclusions.size());
                Rect edge = exclusions.get(0);
                assertEquals(shell.getWidth(), edge.right);
                assertTrue(edge.top >= 0);
                assertTrue(edge.bottom < shell.getHeight());
                assertTrue(edge.height() <= 200 * activity.getResources().getDisplayMetrics().density);
            }
        }
        ((android.webkit.WebView) activity.findViewById(R.id.webview)).destroy();
        ((android.webkit.WebView) activity.findViewById(R.id.adblockwebview)).destroy();
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private void sendTouch(View view, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
}
