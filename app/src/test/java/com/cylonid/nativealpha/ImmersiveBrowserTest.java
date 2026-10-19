package com.cylonid.nativealpha;

import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.cylonid.nativealpha.model.WebApp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class ImmersiveBrowserTest {
    @Test public void immersiveContentRemainsTappableAndDrawerRemainsAvailable() {
        checkBrowser(360, 720, true, false);
        checkBrowser(720, 360, false, true);
    }

    private void checkBrowser(int width, int height, boolean fullscreen, boolean frameless) {
        // Exercise the real browser layout/chrome without starting network or adblock services.
        WebViewActivity activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);
        WebApp app = new WebApp("https://example.org", 0);
        app.setShowFullscreen(fullscreen);
        app.setFrameless(frameless);
        ReflectionHelpers.setField(activity, "webapp", app);
        ReflectionHelpers.setField(activity, "browserToolbarContainer",
                activity.findViewById(R.id.browser_toolbar_container));
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");

        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
        // Robolectric's WebView does not measure page content. Use a native page target
        // in the real web-content container to verify touch routing through the shell.
        FrameLayout pageContainer = activity.findViewById(R.id.browserWebContent);
        View website = new View(activity);
        pageContainer.addView(website, new FrameLayout.LayoutParams(-1, -1));
        drawer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        drawer.layout(0, 0, width, height);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        assertEquals(View.GONE, activity.findViewById(R.id.browser_toolbar_container).getVisibility());

        // The position previously covered by the floating gear now reaches the website.
        AtomicInteger taps = new AtomicInteger();
        website.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) taps.incrementAndGet();
            return true;
        });
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, width - 30, 30, 0);
        MotionEvent up = MotionEvent.obtain(0, 30, MotionEvent.ACTION_UP, width - 30, 30, 0);
        drawer.dispatchTouchEvent(down);
        drawer.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        assertEquals(1, taps.get());

        drawer.openDrawer(GravityCompat.END, false);
        assertTrue(drawer.isDrawerOpen(GravityCompat.END));
        drawer.closeDrawer(GravityCompat.END, false);
        assertFalse(drawer.isDrawerOpen(GravityCompat.END));

        // A deliberate inward drag from the edge reaches the native drawer.
        sendTouch(drawer, 1000, MotionEvent.ACTION_DOWN, width - 1, height - 100);
        sendTouch(drawer, 1020, MotionEvent.ACTION_MOVE, width - 60, height - 100);
        sendTouch(drawer, 1040, MotionEvent.ACTION_MOVE, width - 160, height - 100);
        assertTrue(drawer.isDrawerVisible(GravityCompat.END));
        sendTouch(drawer, 1060, MotionEvent.ACTION_CANCEL, width - 160, height - 100);
        drawer.closeDrawer(GravityCompat.END, false);

        if (Build.VERSION.SDK_INT >= 29) {
            View content = activity.findViewById(R.id.webviewActivity);
            List<Rect> exclusions = content.getSystemGestureExclusionRects();
            assertEquals(1, exclusions.size());
            Rect edge = exclusions.get(0);
            assertEquals(content.getWidth(), edge.right);
            assertTrue(edge.top >= 0);
            assertTrue(edge.bottom < content.getHeight());
            assertTrue(edge.height() <= 200 * activity.getResources().getDisplayMetrics().density);
        }

        app.setShowFullscreen(false);
        app.setFrameless(false);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        assertEquals(View.VISIBLE, activity.findViewById(R.id.browser_toolbar_container).getVisibility());
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(activity.findViewById(R.id.webviewActivity).getSystemGestureExclusionRects().isEmpty());
        }
    }

    private void sendTouch(View view, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(1000, time, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
}
