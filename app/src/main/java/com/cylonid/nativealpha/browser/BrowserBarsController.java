package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

/**
 * Controller responsible for managing immersive system bars (status bar, navigation bar,
 * cutout mode) and fullscreen custom media views (HTML5 video full screen).
 * Aligned with Hermit Lite Apps immersive system bar architecture.
 */
public class BrowserBarsController {

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        @Nullable
        WebView getActiveWebView();

        void onCustomViewStateChanged(@Nullable View view, @Nullable WebChromeClient.CustomViewCallback callback);

        void closeOpenDrawer();

        void updateToolbarVisibility();

        void requestApplyInsetsOnDrawer();

        @Nullable
        View getMediaCustomView();
    }

    private final Activity activity;
    private final Callback callback;

    private View mediaCustomView;
    private WebChromeClient.CustomViewCallback mediaCustomViewCallback;
    private int mediaOriginalOrientation;
    private int mediaOriginalSystemUiVisibility;
    private int mediaOriginalSystemBars;
    private int mediaOriginalBarsBehavior;

    public BrowserBarsController(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public boolean isMediaCustomViewShowing() {
        return mediaCustomView != null || callback.getMediaCustomView() != null;
    }

    public void applySystemBars() {
        WebApp webapp = callback.getWebApp();
        boolean hasMedia = isMediaCustomViewShowing();
        boolean isFramelessOrFullscreen = webapp != null && (webapp.isFrameless() || webapp.isShowFullscreen());

        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        int cutoutMode = isFramelessOrFullscreen
                ? (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        if (attributes.layoutInDisplayCutoutMode != cutoutMode) {
            attributes.layoutInDisplayCutoutMode = cutoutMode;
            activity.getWindow().setAttributes(attributes);
        }

        if (hasMedia || isFramelessOrFullscreen) {
            hideSystemBars();
        } else {
            showSystemBars();
        }

        View root = activity.findViewById(R.id.browserWindowRoot);
        if (root != null) {
            ViewCompat.requestApplyInsets(root);
        }
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && callback.getWebApp() != null) {
            applySystemBars();
        }
    }

    public void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    public void showSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback customViewCallback, WebView owner) {
        if (isMediaCustomViewShowing() || owner != callback.getActiveWebView()) {
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            return;
        }

        mediaOriginalSystemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
        mediaOriginalSystemBars = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                mediaOriginalBarsBehavior = controller.getSystemBarsBehavior();
            }
            WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
            WebApp webapp = callback.getWebApp();
            if (insets != null) {
                if (insets.isVisible(WindowInsets.Type.statusBars())) {
                    mediaOriginalSystemBars |= WindowInsets.Type.statusBars();
                }
                if (insets.isVisible(WindowInsets.Type.navigationBars())) {
                    mediaOriginalSystemBars |= WindowInsets.Type.navigationBars();
                }
            } else if (webapp != null && !webapp.isShowFullscreen()) {
                mediaOriginalSystemBars = WindowInsets.Type.systemBars();
            }
        }
        mediaOriginalOrientation = activity.getRequestedOrientation();
        mediaCustomView = view;
        mediaCustomViewCallback = customViewCallback;
        callback.onCustomViewStateChanged(mediaCustomView, mediaCustomViewCallback);

        callback.closeOpenDrawer();
        ((FrameLayout) activity.getWindow().getDecorView()).addView(view, new FrameLayout.LayoutParams(-1, -1));
        callback.updateToolbarVisibility();
        hideSystemBars();
    }

    public void onHideCustomView() {
        hideMediaCustomView();
    }

    public void hideMediaCustomView() {
        View customView = mediaCustomView != null ? mediaCustomView : callback.getMediaCustomView();
        if (customView == null) return;

        WebChromeClient.CustomViewCallback cb = mediaCustomViewCallback;
        mediaCustomView = null;
        mediaCustomViewCallback = null;
        callback.onCustomViewStateChanged(null, null);

        ((FrameLayout) activity.getWindow().getDecorView()).removeView(customView);
        activity.setRequestedOrientation(mediaOriginalOrientation);
        activity.getWindow().getDecorView().setSystemUiVisibility(mediaOriginalSystemUiVisibility);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                int systemBars = WindowInsets.Type.systemBars();
                controller.show(mediaOriginalSystemBars);
                controller.hide(systemBars & ~mediaOriginalSystemBars);
                controller.setSystemBarsBehavior(mediaOriginalBarsBehavior);
            }
        }

        callback.updateToolbarVisibility();
        callback.requestApplyInsetsOnDrawer();
        if (cb != null) {
            cb.onCustomViewHidden();
        }
    }

    public boolean handleBackPressed() {
        if (isMediaCustomViewShowing()) {
            hideMediaCustomView();
            return true;
        }
        return false;
    }

    @Nullable
    public View getMediaCustomView() {
        return mediaCustomView;
    }

    @Nullable
    public WebChromeClient.CustomViewCallback getMediaCustomViewCallback() {
        return mediaCustomViewCallback;
    }
}
