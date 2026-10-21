package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserBarsControllerTest {

    private Activity activity;
    private WebApp webApp;
    private WebView webView;
    private TestCallback callback;
    private BrowserBarsController controller;

    private static class TestCallback implements BrowserBarsController.Callback {
        WebApp app;
        WebView wv;
        View currentMediaView;
        WebChromeClient.CustomViewCallback currentCallback;
        boolean drawerClosed = false;
        boolean toolbarUpdated = false;
        boolean drawerInsetsRequested = false;

        TestCallback(WebApp app, WebView wv) {
            this.app = app;
            this.wv = wv;
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

        @Override
        public void onCustomViewStateChanged(@Nullable View view, @Nullable WebChromeClient.CustomViewCallback callback) {
            this.currentMediaView = view;
            this.currentCallback = callback;
        }

        @Override
        public void closeOpenDrawer() {
            drawerClosed = true;
        }

        @Override
        public void updateToolbarVisibility() {
            toolbarUpdated = true;
        }

        @Override
        public void requestApplyInsetsOnDrawer() {
            drawerInsetsRequested = true;
        }

        @Nullable
        @Override
        public View getMediaCustomView() {
            return currentMediaView;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme);
        FrameLayout rootLayout = new FrameLayout(activity);
        rootLayout.setId(R.id.browserWindowRoot);
        activity.setContentView(rootLayout);

        webApp = new WebApp("https://example.org", 1);
        webView = new WebView(activity);
        callback = new TestCallback(webApp, webView);
        controller = new BrowserBarsController(activity, callback);
    }

    @After
    public void tearDown() {
        controller.hideMediaCustomView();
        webView.destroy();
    }

    @Test
    public void isMediaCustomViewShowingInitiallyFalse() {
        assertFalse(controller.isMediaCustomViewShowing());
        assertNull(controller.getMediaCustomView());
        assertNull(controller.getMediaCustomViewCallback());
    }

    @Test
    public void applySystemBarsSetsFramelessCutoutAndHidesBars() {
        webApp.setFrameless(true);
        controller.applySystemBars();

        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        assertTrue(attributes.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT);
    }

    @Test
    public void applySystemBarsStandardSetsDefaultCutout() {
        webApp.setFrameless(false);
        webApp.setShowFullscreen(false);
        controller.applySystemBars();

        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT, attributes.layoutInDisplayCutoutMode);
    }

    @Test
    public void onWindowFocusChangedAppliesSystemBarsWhenFocused() {
        webApp.setFrameless(true);
        controller.onWindowFocusChanged(true);

        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        assertTrue(attributes.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT);
    }

    @Test
    public void onShowCustomViewRejectsDifferentOwner() {
        WebView differentWebView = new WebView(activity);
        View mediaView = new View(activity);
        AtomicInteger hiddenCount = new AtomicInteger(0);
        WebChromeClient.CustomViewCallback viewCallback = hiddenCount::incrementAndGet;

        controller.onShowCustomView(mediaView, viewCallback, differentWebView);

        assertEquals(1, hiddenCount.get());
        assertFalse(controller.isMediaCustomViewShowing());
        assertNull(controller.getMediaCustomView());
        differentWebView.destroy();
    }

    @Test
    public void onShowCustomViewAddsViewAndUpdatesState() {
        View mediaView = new View(activity);
        AtomicInteger hiddenCount = new AtomicInteger(0);
        WebChromeClient.CustomViewCallback viewCallback = hiddenCount::incrementAndGet;

        controller.onShowCustomView(mediaView, viewCallback, webView);

        assertTrue(controller.isMediaCustomViewShowing());
        assertSame(mediaView, controller.getMediaCustomView());
        assertSame(viewCallback, controller.getMediaCustomViewCallback());
        assertTrue(callback.drawerClosed);
        assertTrue(callback.toolbarUpdated);

        // Verify view is in DecorView hierarchy
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        boolean found = false;
        for (int i = 0; i < decor.getChildCount(); i++) {
            if (decor.getChildAt(i) == mediaView) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void onShowCustomViewRejectsReentrantCall() {
        View mediaView1 = new View(activity);
        AtomicInteger hiddenCount1 = new AtomicInteger(0);
        controller.onShowCustomView(mediaView1, hiddenCount1::incrementAndGet, webView);

        View mediaView2 = new View(activity);
        AtomicInteger hiddenCount2 = new AtomicInteger(0);
        controller.onShowCustomView(mediaView2, hiddenCount2::incrementAndGet, webView);

        assertEquals(1, hiddenCount2.get());
        assertSame(mediaView1, controller.getMediaCustomView());
    }

    @Test
    public void hideMediaCustomViewRestoresStateAndInvokesCallback() {
        View mediaView = new View(activity);
        AtomicInteger hiddenCount = new AtomicInteger(0);
        WebChromeClient.CustomViewCallback viewCallback = hiddenCount::incrementAndGet;

        controller.onShowCustomView(mediaView, viewCallback, webView);
        callback.toolbarUpdated = false;

        controller.hideMediaCustomView();

        assertFalse(controller.isMediaCustomViewShowing());
        assertNull(controller.getMediaCustomView());
        assertNull(controller.getMediaCustomViewCallback());
        assertEquals(1, hiddenCount.get());
        assertTrue(callback.toolbarUpdated);
        assertTrue(callback.drawerInsetsRequested);

        // Verify view was removed from DecorView
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        for (int i = 0; i < decor.getChildCount(); i++) {
            assertTrue(decor.getChildAt(i) != mediaView);
        }

        // Idempotent second call
        controller.hideMediaCustomView();
        assertEquals(1, hiddenCount.get());
    }

    @Test
    public void handleBackPressedConsumesWhenShowing() {
        View mediaView = new View(activity);
        AtomicInteger hiddenCount = new AtomicInteger(0);
        controller.onShowCustomView(mediaView, hiddenCount::incrementAndGet, webView);

        boolean handled = controller.handleBackPressed();
        assertTrue(handled);
        assertFalse(controller.isMediaCustomViewShowing());
        assertEquals(1, hiddenCount.get());

        // Back pressed again when no custom view is showing returns false
        boolean handledSecond = controller.handleBackPressed();
        assertFalse(handledSecond);
    }
}
