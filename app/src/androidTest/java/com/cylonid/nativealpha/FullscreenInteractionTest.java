package com.cylonid.nativealpha;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.CompoundButton;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises real system bars and touch routing on the attached device. */
@RunWith(AndroidJUnit4.class)
public class FullscreenInteractionTest {
    @Test public void framelessShowsOnlyWebsiteAndReturnsToLibraryForSettings() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        int id = manager.getIncrementedID();
        WebApp app = new WebApp("about:blank", id);
        app.setTitle("Fullscreen interaction verification");
        app.setContainerId(-1);
        app.setUseContainer(false);
        app.setOverrideGlobalSettings(true);
        app.setUseAdblock(false);
        app.setAllowMediaPlaybackInBackground(false);
        app.setFrameless(true);
        app.setShowFullscreen(false);
        manager.addWebsite(app);
        WebViewActivity activity = null;
        try {
            Intent intent = new Intent(instrumentation.getTargetContext(), WebViewActivity.class)
                    .putExtra(Const.INTENT_WEBAPPID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (WebViewActivity) instrumentation.startActivitySync(intent);
            final WebViewActivity browser = activity;
            instrumentation.runOnMainSync(() -> ((WebView) browser.findViewById(R.id.webview))
                    .loadData("<meta name='viewport' content='width=device-width'><button style='position:fixed;bottom:0;right:0' onclick='this.textContent=\"Tapped\"'>Bottom website button</button>", "text/html", "UTF-8"));
            instrumentation.waitForIdleSync();
            // Allow Android's system-bar animation to finish before checking actual visibility.
            SystemClock.sleep(1200);
            instrumentation.runOnMainSync(() -> {
                WindowInsets insets = browser.getWindow().getDecorView().getRootWindowInsets();
                assertNotNull(insets);
                assertFalse("Status bar must disappear", insets.isVisible(WindowInsets.Type.statusBars()));
                assertFalse("Navigation bar must disappear", insets.isVisible(WindowInsets.Type.navigationBars()));
                View restore = browser.findViewById(R.id.btnBrowserRestoreControls);
                View content = browser.findViewById(R.id.browserWebContent);
                assertEquals("No persistent recovery footer", View.GONE, restore.getVisibility());
                assertEquals(View.GONE, browser.findViewById(R.id.browser_toolbar_container).getVisibility());
                assertEquals("Website must fill the shell", ((View) content.getParent()).getHeight(), content.getBottom());
                int[] origin = new int[2];
                content.getLocationOnScreen(origin);
                assertEquals("Website must extend into the camera area", 0, origin[1]);
                assertEquals(0, origin[0]);
                android.graphics.Rect display = browser.getWindowManager().getCurrentWindowMetrics().getBounds();
                assertEquals("Website must fill physical display height", display.height(), content.getHeight());
                assertEquals(display.width(), content.getWidth());
            });
            // Locate a fixed-bottom website button in CSS pixels, then tap its real screen bounds.
            java.util.concurrent.LinkedBlockingQueue<String> geometry = new java.util.concurrent.LinkedBlockingQueue<>();
            instrumentation.runOnMainSync(() -> ((WebView) browser.findViewById(R.id.webview))
                    .evaluateJavascript("(()=>{const r=document.querySelector('button').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,devicePixelRatio]})()", geometry::add));
            String boundsJson = geometry.poll(5, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull("Website must return button geometry", boundsJson);
            org.json.JSONArray bounds = new org.json.JSONArray(boundsJson);
            int[] webLocation = new int[2];
            instrumentation.runOnMainSync(() -> browser.findViewById(R.id.webview).getLocationOnScreen(webLocation));
            tap(instrumentation, webLocation[0] + (int) (bounds.getDouble(0) * bounds.getDouble(2)),
                    webLocation[1] + (int) (bounds.getDouble(1) * bounds.getDouble(2)));
            instrumentation.waitForIdleSync();
            java.util.concurrent.LinkedBlockingQueue<String> result = new java.util.concurrent.LinkedBlockingQueue<>();
            instrumentation.runOnMainSync(() -> ((WebView) browser.findViewById(R.id.webview))
                    .evaluateJavascript("document.querySelector('button').textContent", result::add));
            assertEquals("Native controls must not intercept a website's bottom button", "\"Tapped\"",
                    result.poll(5, java.util.concurrent.TimeUnit.SECONDS));
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MENU);
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                DrawerLayout drawer = browser.findViewById(R.id.browser_drawer_container);
                assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, drawer.getDrawerLockMode(GravityCompat.END));
                assertFalse("Frameless mode must not expose settings", browser.findViewById(R.id.btnQuickMoreSettings).isShown());
                assertEquals(View.VISIBLE, browser.findViewById(R.id.framelessPageTools).getVisibility());
                drawer.closeDrawer(GravityCompat.END, false);
                ((WebView) browser.findViewById(R.id.webview)).clearHistory();
            });
            // Optional: screen sharing on Android preview builds can own UiAutomation.
            if ("true".equals(InstrumentationRegistry.getArguments().getString("captureScreenshots"))) {
            try (java.io.FileOutputStream screen = new java.io.FileOutputStream(new java.io.File(
                    instrumentation.getTargetContext().getExternalFilesDir(null), "frameless-website-only.png"))) {
                instrumentation.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, screen);
            }
            }
            instrumentation.runOnMainSync(browser::onBackPressed);
            instrumentation.waitForIdleSync();
            assertTrue("Back at website root must exit to the library", browser.isFinishing());
        } finally {
            if (activity != null) {
                final WebViewActivity closing = activity;
                instrumentation.runOnMainSync(closing::finish);
                instrumentation.waitForIdleSync();
            }
            manager.loadAppData();
            manager.getWebsites().removeIf(candidate -> candidate.getID() == id);
            manager.saveWebAppData();
        }
    }
    private static void tap(Instrumentation instrumentation, int x, int y) {
        long time = SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time, 0, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 50, 1, x, y, 0);
        instrumentation.sendPointerSync(down);
        instrumentation.sendPointerSync(up);
        down.recycle();
        up.recycle();
    }
}
