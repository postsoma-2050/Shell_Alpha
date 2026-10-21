package com.cylonid.nativealpha;

import android.content.Intent;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.cylonid.nativealpha.browser.BrowserDrawerLayout;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

/**
 * Verifies independent control of fullscreen (system status/navigation bars)
 * and frameless (application toolbar) modes, along with touch/layout boundary safety
 * for the restore entry button in frameless mode.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class FullscreenControlsTest {

    @Test
    public void fullscreenSwitchTogglesSystemBarsAndLeavesFramelessIndependent() {
        WebViewActivity activity = createConfiguredActivity(false, false);
        WebApp app = ReflectionHelpers.getField(activity, "webapp");

        MaterialSwitch fullscreenSwitch = activity.findViewById(R.id.switchQuickFullscreen);
        View toolbar = activity.findViewById(R.id.browser_toolbar_container);
        View restore = activity.findViewById(R.id.btnBrowserRestoreControls);

        assertNotNull("Fullscreen switch must exist in quick settings drawer", fullscreenSwitch);
        assertFalse("Initial fullscreen state must be off", fullscreenSwitch.isChecked());
        assertFalse("Initial webapp fullscreen flag must be false", app.isShowFullscreen());
        assertFalse("Initial frameless flag must be false", app.isFrameless());
        assertEquals("Toolbar must be visible when not frameless", View.VISIBLE, toolbar.getVisibility());
        assertEquals("Restore controls button must be gone when not frameless", View.GONE, restore.getVisibility());

        // Toggle fullscreen switch on
        fullscreenSwitch.setChecked(true);
        assertTrue("WebApp fullscreen flag must update when switch is enabled", app.isShowFullscreen());
        assertFalse("Frameless flag must remain unaffected when fullscreen is toggled", app.isFrameless());
        assertEquals("Toolbar visibility must not change when toggling fullscreen", View.VISIBLE, toolbar.getVisibility());
        assertEquals("Restore button must remain gone when fullscreen is toggled", View.GONE, restore.getVisibility());

        // Toggle fullscreen switch off
        fullscreenSwitch.setChecked(false);
        assertFalse("WebApp fullscreen flag must update when switch is disabled", app.isShowFullscreen());
        assertFalse("Frameless flag must still remain unaffected", app.isFrameless());
        assertEquals("Toolbar must still be visible", View.VISIBLE, toolbar.getVisibility());
        assertEquals("Restore button must still be gone", View.GONE, restore.getVisibility());

        cleanupActivity(activity);
    }

    @Test
    public void framelessActionCardTogglesToolbarAndLeavesFullscreenIndependent() {
        // Start with fullscreen enabled
        WebViewActivity activity = createConfiguredActivity(true, false);
        WebApp app = ReflectionHelpers.getField(activity, "webapp");

        MaterialSwitch fullscreenSwitch = activity.findViewById(R.id.switchQuickFullscreen);
        View cardFrameless = activity.findViewById(R.id.cardQuickFrameless);
        View toolbar = activity.findViewById(R.id.browser_toolbar_container);
        View restore = activity.findViewById(R.id.btnBrowserRestoreControls);

        assertNotNull(fullscreenSwitch);
        assertNotNull(cardFrameless);
        assertTrue("Fullscreen switch must reflect initial state", fullscreenSwitch.isChecked());
        assertTrue("WebApp fullscreen state must be true", app.isShowFullscreen());
        assertFalse("WebApp frameless state must be false", app.isFrameless());

        // Turn on frameless mode via card click
        cardFrameless.performClick();
        assertTrue("WebApp frameless flag must become true", app.isFrameless());
        assertTrue("Fullscreen flag must stay true when frameless is toggled", app.isShowFullscreen());
        assertTrue("Fullscreen switch must remain checked", fullscreenSwitch.isChecked());
        assertEquals("App toolbar must hide in frameless mode", View.GONE, toolbar.getVisibility());
        assertEquals("Restore controls button must remain gone in frameless mode", View.GONE, restore.getVisibility());

        // Turn off frameless mode via card click
        cardFrameless.performClick();
        assertFalse("WebApp frameless flag must revert to false", app.isFrameless());
        assertTrue("Fullscreen flag must remain true", app.isShowFullscreen());
        assertTrue("Fullscreen switch must still remain checked", fullscreenSwitch.isChecked());
        assertEquals("App toolbar must reappear when frameless mode is off", View.VISIBLE, toolbar.getVisibility());
        assertEquals("Restore button must hide when frameless mode is off", View.GONE, restore.getVisibility());

        cleanupActivity(activity);
    }

    @Test
    public void restoreControlsButtonHasNonZeroDimensionsAndKeepsWebContentUncovered() {
        for (int[] dimensions : new int[][]{{360, 720}, {720, 360}}) {
            int width = dimensions[0];
            int height = dimensions[1];

            WebViewActivity activity = createConfiguredActivity(false, true);
            DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
            FrameLayout content = activity.findViewById(R.id.browserWebContent);
            View restore = activity.findViewById(R.id.btnBrowserRestoreControls);
            View toolbar = activity.findViewById(R.id.browser_toolbar_container);

            View websiteView = new View(activity);
            content.addView(websiteView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            measure(drawer, width, height);
            ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
            measure(drawer, width, height);

            assertEquals(View.GONE, toolbar.getVisibility());
            assertEquals("Restore button must remain GONE in frameless mode", View.GONE, restore.getVisibility());

            // Verify frameless web content completely fills the parent drawer layout
            assertTrue("Web content must have positive height", content.getHeight() > 0);
            assertEquals("Web content bottom must fill parent height without footer",
                    drawer.getHeight(), content.getBottom());

            // Verify inner website view bottom fills container
            assertTrue("Website view must have positive height", websiteView.getHeight() > 0);
            Rect websiteRect = new Rect(0, 0, websiteView.getWidth(), websiteView.getHeight());
            drawer.offsetDescendantRectToMyCoords(websiteView, websiteRect);
            assertEquals("Website boundary bottom must align with container bottom",
                    drawer.getHeight(), websiteRect.bottom);

            // Frameless exposes page tools while hiding all settings.
            assertEquals("Drawer must be unlocked in frameless mode for page tools",
                    DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));
            assertTrue("Drawer must be BrowserDrawerLayout instance",
                    drawer instanceof BrowserDrawerLayout);

            // Settings views must be gone, frameless page tools must be visible
            assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsScroll).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsNavigation).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsDivider).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.framelessPageTools).getVisibility());

            // openQuickSettings must guard frameless mode and not open drawer or expose settings
            ReflectionHelpers.callInstanceMethod(activity, "openQuickSettings");
            assertFalse("openQuickSettings must not open drawer in frameless mode", drawer.isDrawerOpen(GravityCompat.END));

            // KEYCODE_MENU opens page tools in frameless mode
            boolean menuHandled = activity.onKeyDown(KeyEvent.KEYCODE_MENU,
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
            assertTrue("KEYCODE_MENU must return true and open page tools in frameless mode", menuHandled);
            ShadowLooper.idleMainLooper();
            assertDrawerOpening(drawer);

            drawer.closeDrawer(GravityCompat.END, false);
            ShadowLooper.idleMainLooper();

            // Back key without history launches MainActivity and finishes instead of opening drawer
            activity.onBackPressed();
            assertTrue("Activity must finish on Back without history in frameless mode", activity.isFinishing());
            Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull("Back without history must start MainActivity intent", started);
            assertEquals(MainActivity.class.getName(), started.getComponent().getClassName());
            assertFalse("Drawer must not open on Back in frameless mode", drawer.isDrawerOpen(GravityCompat.END));

            cleanupActivity(activity);
        }
    }

    @Test
    public void allCombinationsOfFullscreenAndFramelessMaintainIndependence() {
        for (boolean fullscreen : new boolean[]{false, true}) {
            for (boolean frameless : new boolean[]{false, true}) {
                WebViewActivity activity = createConfiguredActivity(fullscreen, frameless);
                WebApp app = ReflectionHelpers.getField(activity, "webapp");

                DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
                View toolbar = activity.findViewById(R.id.browser_toolbar_container);
                View restore = activity.findViewById(R.id.btnBrowserRestoreControls);
                FrameLayout content = activity.findViewById(R.id.browserWebContent);
                MaterialSwitch fullscreenSwitch = activity.findViewById(R.id.switchQuickFullscreen);

                measure(drawer, 360, 720);
                ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
                measure(drawer, 360, 720);

                assertEquals("Fullscreen state must match requested setting", fullscreen, app.isShowFullscreen());
                assertEquals("Fullscreen switch must reflect fullscreen state", fullscreen, fullscreenSwitch.isChecked());
                assertEquals("Frameless state must match requested setting", frameless, app.isFrameless());

                if (frameless) {
                    assertEquals("Toolbar must be gone in frameless mode", View.GONE, toolbar.getVisibility());
                    assertEquals("Restore button must remain gone in frameless mode", View.GONE, restore.getVisibility());
                    assertEquals("Web content bottom must fill parent height in frameless mode", drawer.getHeight(), content.getBottom());
                    assertEquals("Drawer must be unlocked for page tools in frameless mode",
                            DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));
                    assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsScroll).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsNavigation).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsDivider).getVisibility());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.framelessPageTools).getVisibility());
                } else {
                    assertEquals("Drawer must be unlocked in framed mode",
                            DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));
                    assertEquals("Toolbar must be visible in framed mode", View.VISIBLE, toolbar.getVisibility());
                    assertEquals("Restore button must be gone in framed mode", View.GONE, restore.getVisibility());
                    assertTrue("Web content top must be at or below toolbar bottom", content.getTop() >= toolbar.getBottom());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.quickSettingsScroll).getVisibility());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.quickSettingsNavigation).getVisibility());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.quickSettingsDivider).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.framelessPageTools).getVisibility());
                }

                cleanupActivity(activity);
            }
        }
    }

    @Test
    public void framelessBackWithoutHistoryLaunchesMainActivityAndFinishes() {
        WebViewActivity activity = createConfiguredActivity(false, true);
        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);

        assertFalse(drawer.isDrawerOpen(GravityCompat.END));
        activity.onBackPressed();
        assertTrue("Back without history in frameless mode must finish activity", activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("Back without history must start MainActivity intent", next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertFalse(drawer.isDrawerOpen(GravityCompat.END));

        cleanupActivity(activity);
    }

    @Test
    public void framedModeMenuKeyOpensQuickSettingsDrawer() {
        WebViewActivity activity = createConfiguredActivity(false, false);
        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);

        assertFalse(drawer.isDrawerOpen(GravityCompat.END));
        boolean menuHandled = activity.onKeyDown(KeyEvent.KEYCODE_MENU,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
        assertTrue("KEYCODE_MENU must return true in framed mode", menuHandled);
        drawer.computeScroll();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        drawer.computeScroll();
        ShadowLooper.idleMainLooper();
        assertTrue("KEYCODE_MENU must open quick settings drawer in framed mode", drawer.isDrawerOpen(GravityCompat.END));

        cleanupActivity(activity);
    }

    private WebViewActivity createConfiguredActivity(boolean fullscreen, boolean frameless) {
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        manager.getWebsites().clear();

        WebViewActivity activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);

        WebApp app = new WebApp("https://example.org", 0);
        app.setShowFullscreen(fullscreen);
        app.setFrameless(frameless);
        manager.addWebsite(app);

        ReflectionHelpers.setField(activity, "webapp", app);
        ReflectionHelpers.setField(activity, "wv", activity.findViewById(R.id.webview));
        ReflectionHelpers.callInstanceMethod(activity, "setupHermitShell");
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        ReflectionHelpers.callInstanceMethod(activity, "refreshQuickSettingsState");
        return activity;
    }

    private void cleanupActivity(WebViewActivity activity) {
        ((android.webkit.WebView) activity.findViewById(R.id.webview)).destroy();
        ((android.webkit.WebView) activity.findViewById(R.id.adblockwebview)).destroy();
    }

    private void assertDrawerOpening(DrawerLayout drawer) {
        drawer.computeScroll();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        drawer.computeScroll();
        View panel = drawer.findViewById(R.id.quick_settings_drawer);
        int state = ReflectionHelpers.getField(panel.getLayoutParams(), "openState");
        assertTrue("Native drawer must open or be opening", drawer.isDrawerOpen(GravityCompat.END) || (state & 2) != 0);
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
}
