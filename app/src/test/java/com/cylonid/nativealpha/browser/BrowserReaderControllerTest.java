package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BrowserReaderController}, verifying article extraction script generation,
 * eligibility checks, font & theme controls, and translation panel coordination.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BrowserReaderControllerTest {

    private Activity activity;
    private WebApp webApp;
    private WebView webView;
    private FrameLayout content;
    private View controls;
    private TestCallback callback;
    private BrowserReaderController controller;

    private static class TestCallback implements BrowserReaderController.Callback {
        WebApp app;
        WebView wv;
        boolean mediaShowing = false;
        boolean boundsUpdated = false;
        boolean findBarClosed = false;
        String navigatedUrl = null;

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
        public boolean isMediaShowing() {
            return mediaShowing;
        }

        @Override
        public void onUpdateContentBounds() {
            boundsUpdated = true;
        }

        @Override
        public void onCloseFindBar() {
            findBarClosed = true;
        }

        @Override
        public void onNavigateUrl(@NonNull String url) {
            navigatedUrl = url;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme);

        content = new FrameLayout(activity);
        activity.getLayoutInflater().inflate(R.layout.browser_reader_controls, content, true);
        controls = content.findViewById(R.id.browserReaderControls);

        webApp = new WebApp("https://example.org", 0);
        webView = new WebView(activity);
        webView.getSettings().setJavaScriptEnabled(true);

        callback = new TestCallback(webApp, webView);
        controller = new BrowserReaderController(activity, callback);
        controller.init(content, controls);
    }

    @After
    public void tearDown() {
        controller.destroy();
        webView.destroy();
    }

    @Test
    public void isReaderEligible_validatesCorrectly() {
        assertFalse(BrowserReaderController.isReaderEligible("https://example.org", false));
        assertFalse(BrowserReaderController.isReaderEligible(null, true));
        assertFalse(BrowserReaderController.isReaderEligible("   ", true));
        assertFalse(BrowserReaderController.isReaderEligible("javascript:void(0)", true));

        assertTrue(BrowserReaderController.isReaderEligible("https://example.org/article", true));
        assertTrue(BrowserReaderController.isReaderEligible("http://news.ycombinator.com", true));
    }

    @Test
    public void buildExtractionScript_embedsUrlAndFunctions() {
        String readability = "var Readability = {};";
        String extract = "function shellAlphaExtractArticle(doc, url) { return { title: 'Test', content: 'Body' }; }";
        String script = BrowserReaderController.buildExtractionScript(readability, extract, "https://example.com/post?id=42");

        assertTrue(script.startsWith("(function(){try{"));
        assertTrue(script.contains(org.json.JSONObject.quote("https://example.com/post?id=42")));
        assertTrue(script.contains("shellAlphaExtractArticle"));
        assertTrue(script.endsWith("}catch(e){return null;}})()"));
    }

    @Test
    public void open_whenMediaShowing_doesNotOpen() {
        callback.mediaShowing = true;
        controller.open();
        assertFalse(controller.isOpen());
        assertFalse(controller.isActive());
    }

    @Test
    public void open_whenNullWebApp_doesNotOpen() {
        callback.app = null;
        controller.open();
        assertFalse(controller.isOpen());
    }

    @Test
    public void handleBackPressed_whenNotActive_returnsFalse() {
        assertFalse(controller.handleBackPressed());
    }

    @Test
    public void adjustReaderControlsBounds_whenVisible_adjustsConstraints() {
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(100, 100);
        params.topToTop = 10;
        params.topToBottom = 20;

        ConstraintLayout.LayoutParams readerParams = new ConstraintLayout.LayoutParams(100, 100);
        controls.setLayoutParams(readerParams);
        controls.setVisibility(View.VISIBLE);

        controller.adjustReaderControlsBounds(params, controls);

        ConstraintLayout.LayoutParams updated = (ConstraintLayout.LayoutParams) controls.getLayoutParams();
        assertEquals(10, updated.topToTop);
        assertEquals(20, updated.topToBottom);
        assertEquals(R.id.browserReaderControls, params.topToBottom);
    }

    @Test
    public void adjustReaderControlsBounds_whenControlsGone_doesNotAlterParams() {
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(100, 100);
        params.topToTop = 10;
        params.topToBottom = 20;

        controls.setVisibility(View.GONE);
        controller.adjustReaderControlsBounds(params, controls);

        assertEquals(10, params.topToTop);
        assertEquals(20, params.topToBottom);
    }

    @Test
    public void presentationControls_fontSizeAndTheme() {
        assertNotNull(controller.getReaderModeController());

        controller.setFontSize(24);
        assertEquals(24, controller.getFontSize());

        controller.increaseFontSize();
        assertEquals(26, controller.getFontSize());

        controller.decreaseFontSize();
        assertEquals(24, controller.getFontSize());

        controller.setTheme(1);
        assertEquals(1, controller.getTheme());

        controller.cycleTheme();
        assertEquals(2, controller.getTheme());
    }

    @Test
    public void translationControls_toggleAndState() {
        boolean initial = controller.isTranslationEnabled();
        controller.toggleTranslation();
        assertEquals(!initial, controller.isTranslationEnabled());

        controller.setTranslationEnabled(true);
        assertTrue(controller.isTranslationEnabled());

        controller.setTranslationEnabled(false);
        assertFalse(controller.isTranslationEnabled());
    }

    @Test
    public void destroy_cleansUpReaderMode() {
        controller.destroy();
        assertNull(controller.getReaderModeController());
        assertFalse(controller.isOpen());
        assertFalse(controller.isActive());
        assertNull(controller.getReaderWebView());
    }
}
