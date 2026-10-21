package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Controller responsible for managing Reader Mode, DOM extraction script generation,
 * text size & theme presentation settings, and translation engine collaboration.
 * Aligned with Hermit Lite Apps reader mode architecture.
 */
public class BrowserReaderController {

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        @Nullable
        WebView getActiveWebView();

        boolean isMediaShowing();

        void onUpdateContentBounds();

        void onCloseFindBar();

        void onNavigateUrl(@NonNull String url);
    }

    private final Activity activity;
    private final Callback callback;
    private ReaderModeController readerMode;

    public BrowserReaderController(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void init(@NonNull FrameLayout content, @NonNull View controls) {
        readerMode = new ReaderModeController(
                content,
                controls,
                callback::onUpdateContentBounds,
                callback::onCloseFindBar,
                callback::onNavigateUrl
        );
    }

    @VisibleForTesting
    public void setReaderModeController(@Nullable ReaderModeController readerMode) {
        this.readerMode = readerMode;
    }

    @Nullable
    public ReaderModeController getReaderModeController() {
        return readerMode;
    }

    public boolean isOpen() {
        return readerMode != null && readerMode.isOpen();
    }

    public boolean isActive() {
        return readerMode != null && readerMode.isActive();
    }

    @Nullable
    public WebView getReaderWebView() {
        return readerMode != null ? readerMode.getWebView() : null;
    }

    public void open() {
        if (callback.getWebApp() == null || callback.isMediaShowing()) return;
        WebView wv = callback.getActiveWebView();
        if (wv == null) return;
        open(wv);
    }

    public void open(@NonNull WebView source) {
        if (callback.isMediaShowing()) return;
        if (readerMode != null) {
            readerMode.open(source);
        }
    }

    public void close() {
        if (readerMode != null) {
            readerMode.close();
        }
    }

    public boolean dismissTranslation() {
        return readerMode != null && readerMode.dismissTranslation();
    }

    public boolean handleBackPressed() {
        if (readerMode != null && readerMode.isActive()) {
            if (readerMode.dismissTranslation()) {
                return true;
            }
            readerMode.close();
            return true;
        }
        return false;
    }

    public void adjustReaderControlsBounds(@NonNull ConstraintLayout.LayoutParams params, @Nullable View readerControls) {
        if (readerControls != null && readerControls.getVisibility() == View.VISIBLE) {
            ConstraintLayout.LayoutParams readerParams = (ConstraintLayout.LayoutParams) readerControls.getLayoutParams();
            readerParams.topToTop = params.topToTop;
            readerParams.topToBottom = params.topToBottom;
            readerControls.setLayoutParams(readerParams);
            params.topToBottom = R.id.browserReaderControls;
        }
    }

    public int getTheme() {
        return readerMode != null ? readerMode.getTheme() : 0;
    }

    public void setTheme(int theme) {
        if (readerMode != null) {
            readerMode.setTheme(theme);
        }
    }

    public void cycleTheme() {
        if (readerMode != null) {
            readerMode.cycleTheme();
        }
    }

    public int getFontSize() {
        return readerMode != null ? readerMode.getFontSize() : 20;
    }

    public void setFontSize(int fontSize) {
        if (readerMode != null) {
            readerMode.setFontSize(fontSize);
        }
    }

    public void increaseFontSize() {
        if (readerMode != null) {
            readerMode.resize(2);
        }
    }

    public void decreaseFontSize() {
        if (readerMode != null) {
            readerMode.resize(-2);
        }
    }

    public boolean isTranslationEnabled() {
        return readerMode != null && readerMode.isTranslationEnabled();
    }

    public void setTranslationEnabled(boolean enabled) {
        if (readerMode != null) {
            readerMode.setTranslationEnabled(enabled);
        }
    }

    public void toggleTranslation() {
        if (readerMode != null) {
            readerMode.toggleTranslation();
        }
    }

    public void destroy() {
        if (readerMode != null) {
            readerMode.destroy();
            readerMode = null;
        }
    }

    /**
     * Determines whether the given URL and JavaScript settings are eligible for reader mode.
     */
    public static boolean isReaderEligible(@Nullable String url, boolean javaScriptEnabled) {
        if (!javaScriptEnabled || url == null || url.trim().isEmpty()) {
            return false;
        }
        return BrowserOriginPolicy.origin(url) != null;
    }

    /**
     * Constructs the article extraction JavaScript IIFE snippet wrapping Readability and ExtractArticle.
     */
    @NonNull
    public static String buildExtractionScript(@NonNull String readabilityJs, @NonNull String extractArticleJs, @NonNull String url) {
        String script = readabilityJs + "\n" + extractArticleJs;
        return "(function(){try{" + script + "\n"
                + "var article=shellAlphaExtractArticle(document," + JSONObject.quote(url) + ");"
                + "if(!article||!article.content||article.content.length>350000)return null;"
                + "return JSON.stringify({title:article.title,byline:article.byline||'',dir:article.dir||'',content:article.content});"
                + "}catch(e){return null;}})()";
    }

    /**
     * Helper to read an asset script safely with UTF-8 encoding.
     */
    @NonNull
    public static String readAssetScript(@NonNull Context context, @NonNull String assetPath) throws Exception {
        try (InputStream asset = context.getAssets().open(assetPath)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int length;
            while ((length = asset.read(chunk)) != -1) {
                buffer.write(chunk, 0, length);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
