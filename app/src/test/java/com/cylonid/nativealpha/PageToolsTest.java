package com.cylonid.nativealpha;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.webkit.WebView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.drawerlayout.widget.DrawerLayout;

import com.cylonid.nativealpha.browser.PageFindController;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for Page Tools: Find in Page ({@link PageFindController}) and Copy URL,
 * verifying clipboard selection, frameless mode restrictions, back press behavior,
 * layout clearance (no overlap at 320dp width), search debounce, and match navigation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class PageToolsTest {

    private WebViewActivity activity;
    private FakeFindWebView webView;
    private WebApp webApp;

    @Before
    public void setUp() {
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        manager.getWebsites().clear();

        activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);

        webApp = new WebApp("https://example.org/base", 0);
        webApp.setShowFullscreen(false);
        webApp.setFrameless(false);
        manager.addWebsite(webApp);

        webView = new FakeFindWebView(activity);
        ReflectionHelpers.setField(activity, "webapp", webApp);
        ReflectionHelpers.setField(activity, "wv", webView);
        ReflectionHelpers.setField(activity, "rootWebView", webView);

        ReflectionHelpers.callInstanceMethod(activity, "setupHermitShell");
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        ReflectionHelpers.callInstanceMethod(activity, "refreshQuickSettingsState");
    }

    @After
    public void tearDown() {
        PageFindController pageFind = ReflectionHelpers.getField(activity, "pageFind");
        if (pageFind != null) {
            pageFind.close();
        }
        webView.destroy();
        View nativeWv = activity.findViewById(R.id.webview);
        if (nativeWv instanceof WebView) {
            ((WebView) nativeWv).destroy();
        }
        View adblockWv = activity.findViewById(R.id.adblockwebview);
        if (adblockWv instanceof WebView) {
            ((WebView) adblockWv).destroy();
        }
    }

    @Test
    public void copyCurrentUrl_copiesNavigatedUrl_whenWebViewHasUrl() {
        webView.fakeUrl = "https://example.org/articles/quantum?ref=feed#summary";

        View cardCopy = activity.findViewById(R.id.cardCopyUrl);
        assertNotNull("Copy URL card must exist in quick actions", cardCopy);
        cardCopy.performClick();

        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertNotNull(clipboard);
        ClipData primaryClip = clipboard.getPrimaryClip();
        assertNotNull("Primary clip must not be null after copying URL", primaryClip);
        assertEquals("Navigated WebView URL must be copied to clipboard rather than base URL",
                "https://example.org/articles/quantum?ref=feed#summary",
                primaryClip.getItemAt(0).getText().toString());
    }

    @Test
    public void copyCurrentUrl_fallsBackToBaseUrl_andHandlesNullOrEmptyGracefully() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertNotNull(clipboard);

        // Fallback to base URL when webView.getUrl() returns null
        webView.fakeUrl = null;
        ReflectionHelpers.callInstanceMethod(activity, "copyCurrentUrl");
        ClipData clip = clipboard.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("Base URL must be copied when webView.getUrl() is null",
                "https://example.org/base", clip.getItemAt(0).getText().toString());

        // Fallback to base URL when webView.getUrl() is empty whitespace
        webView.fakeUrl = "   ";
        ReflectionHelpers.callInstanceMethod(activity, "copyCurrentUrl");
        clip = clipboard.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("Base URL must be copied when webView.getUrl() is blank",
                "https://example.org/base", clip.getItemAt(0).getText().toString());

        // When both webView.getUrl() and webApp.getBaseUrl() are empty/null, clipboard is unchanged
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", "https://sentinel.invalid"));
        webView.fakeUrl = null;
        ReflectionHelpers.setField(webApp, "baseUrl", "");
        ReflectionHelpers.callInstanceMethod(activity, "copyCurrentUrl");
        assertEquals("Sentinel URL must remain untouched when both URLs are empty",
                "https://sentinel.invalid", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void framelessModeKeepsFindAvailableAndHidesSettings() {
        View cardFind = activity.findViewById(R.id.cardFindInPage);
        View findBar = activity.findViewById(R.id.browserFindBar);
        PageFindController pageFind = ReflectionHelpers.getField(activity, "pageFind");
        assertNotNull(cardFind);
        assertNotNull(findBar);
        assertNotNull(pageFind);

        // Verify opening works in framed mode first
        cardFind.performClick();
        assertTrue("Find bar must be open in framed mode", pageFind.isOpen());
        assertEquals(View.VISIBLE, findBar.getVisibility());

        webApp.setFrameless(true);
        ReflectionHelpers.callInstanceMethod(activity, "updateToolbarVisibility");
        assertTrue("Entering frameless must keep the active tool", pageFind.isOpen());
        assertEquals(View.VISIBLE, findBar.getVisibility());
        pageFind.close();
        activity.findViewById(R.id.btnPageToolsFind).performClick();
        assertTrue("Tools-only entry must allow find in frameless", pageFind.isOpen());
        assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsScroll).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.quickSettingsNavigation).getVisibility());
    }

    @Test
    public void onBackPressed_withOpenFindBar_closesFindWithoutNavigatingHistoryOrFinishing() {
        PageFindController pageFind = ReflectionHelpers.getField(activity, "pageFind");
        View findBar = activity.findViewById(R.id.browserFindBar);
        assertNotNull(pageFind);
        assertNotNull(findBar);

        // Open Find in page
        pageFind.open(webView);
        assertTrue(pageFind.isOpen());
        assertEquals(View.VISIBLE, findBar.getVisibility());

        // Configure webView with navigable history
        webView.canGoBackValue = true;
        assertEquals(0, webView.backs);

        // Back press must close find bar only, without navigating history or finishing activity
        activity.onBackPressed();
        assertFalse("Find bar must be closed by back press", pageFind.isOpen());
        assertEquals(View.GONE, findBar.getVisibility());
        assertEquals("WebView back must not be called when closing find bar", 0, webView.backs);
        assertFalse("Activity must not finish on back press that dismisses find bar", activity.isFinishing());

        // Subsequent back press should navigate history
        activity.onBackPressed();
        assertEquals("Subsequent back press must navigate WebView history", 1, webView.backs);
    }

    @Test
    public void layoutContentBounds_noOverlapWithTopOrBottomToolbarAndFindBar_at320dp() {
        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);
        View toolbar = activity.findViewById(R.id.browser_toolbar_container);
        View findBar = activity.findViewById(R.id.browserFindBar);
        View content = activity.findViewById(R.id.browserWebContent);
        PageFindController pageFind = ReflectionHelpers.getField(activity, "pageFind");
        assertNotNull(drawer);
        assertNotNull(toolbar);
        assertNotNull(findBar);
        assertNotNull(content);
        assertNotNull(pageFind);

        final int width = 320;
        final int height = 640;

        // Case 1: Top Toolbar + Find Bar open at 320dp width
        ConstraintLayout.LayoutParams topToolbarParams =
                (ConstraintLayout.LayoutParams) toolbar.getLayoutParams();
        topToolbarParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        topToolbarParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        toolbar.setLayoutParams(topToolbarParams);

        pageFind.open(webView);
        measure(drawer, width, height);

        assertEquals(View.VISIBLE, toolbar.getVisibility());
        assertEquals(View.VISIBLE, findBar.getVisibility());

        ConstraintLayout.LayoutParams findParams =
                (ConstraintLayout.LayoutParams) findBar.getLayoutParams();
        ConstraintLayout.LayoutParams contentParams =
                (ConstraintLayout.LayoutParams) content.getLayoutParams();

        assertEquals("Find bar must anchor to bottom of top toolbar",
                R.id.browser_toolbar_container, findParams.topToBottom);
        assertEquals("Content top must anchor to bottom of find bar",
                R.id.browserFindBar, contentParams.topToBottom);
        assertEquals("Content bottom must anchor to parent",
                ConstraintLayout.LayoutParams.PARENT_ID, contentParams.bottomToBottom);

        assertTrue("Find bar must be at or below top toolbar bottom",
                findBar.getTop() >= toolbar.getBottom());
        assertTrue("Content must be at or below find bar bottom",
                content.getTop() >= findBar.getBottom());
        assertEquals("Content bottom must align with drawer height",
                drawer.getHeight(), content.getBottom());

        // Case 2: Bottom Toolbar + Find Bar open at 320dp width
        ConstraintLayout.LayoutParams bottomToolbarParams =
                (ConstraintLayout.LayoutParams) toolbar.getLayoutParams();
        bottomToolbarParams.topToTop = ConstraintLayout.LayoutParams.UNSET;
        bottomToolbarParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        toolbar.setLayoutParams(bottomToolbarParams);

        ReflectionHelpers.callInstanceMethod(activity, "updateBrowserContentBounds");
        measure(drawer, width, height);

        findParams = (ConstraintLayout.LayoutParams) findBar.getLayoutParams();
        contentParams = (ConstraintLayout.LayoutParams) content.getLayoutParams();

        assertEquals("Find bar must anchor to top of parent when toolbar is at bottom",
                ConstraintLayout.LayoutParams.PARENT_ID, findParams.topToTop);
        assertEquals("Content top must anchor to bottom of find bar",
                R.id.browserFindBar, contentParams.topToBottom);
        assertEquals("Content bottom must anchor to top of bottom toolbar",
                R.id.browser_toolbar_container, contentParams.bottomToTop);

        assertTrue("Content must be at or below find bar bottom",
                content.getTop() >= findBar.getBottom());
        assertTrue("Content bottom must be at or above bottom toolbar top",
                content.getBottom() <= toolbar.getTop());
    }

    @Test
    public void pageFindSearch_debouncesAndCancelsPreviousQuery_andClearMatchesOnClose() {
        PageFindController pageFind = ReflectionHelpers.getField(activity, "pageFind");
        EditText query = activity.findViewById(R.id.findPageQuery);
        assertNotNull(pageFind);
        assertNotNull(query);

        pageFind.open(webView);
        assertEquals("Initial open with empty query must not search", 0, webView.findAllAsyncCalls.size());

        // Enter first search text
        query.setText("first");

        // Advance looper by 100ms (< 180ms debounce window)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        assertEquals("Debounce must delay search execution before 180ms", 0, webView.findAllAsyncCalls.size());

        // Type replacement search text before debounce fires
        query.setText("firstUpdated");

        // Advance looper by 100ms (first query would have completed without cancellation)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        assertEquals("First query must be cancelled by new input", 0, webView.findAllAsyncCalls.size());

        // Advance looper past the 180ms window for the second query
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120));
        assertEquals("Only debounced second query should be submitted", 1, webView.findAllAsyncCalls.size());
        assertEquals("firstUpdated", webView.findAllAsyncCalls.get(0));

        // Close find controller: matches must be cleared and listener reset
        pageFind.close();
        assertTrue("Matches must be cleared on close", webView.clearMatchesCalls > 0);
        assertNull("Find listener must be null after close", webView.currentFindListener);
        assertFalse("PageFindController must be closed", pageFind.isOpen());
        assertEquals("Query text must be reset on close", "", query.getText().toString());
    }

    @Test
    public void pageFindNavigation_routesNextAndPreviousToWebView_onlyWhenMatchesExist() {
        PageFindController pageFind = ReflectionHelpers.getField(activity, "pageFind");
        EditText query = activity.findViewById(R.id.findPageQuery);
        TextView count = activity.findViewById(R.id.findPageCount);
        View btnPrev = activity.findViewById(R.id.btnFindPrevious);
        View btnNext = activity.findViewById(R.id.btnFindNext);

        assertNotNull(pageFind);
        assertNotNull(query);
        assertNotNull(count);
        assertNotNull(btnPrev);
        assertNotNull(btnNext);

        pageFind.open(webView);
        query.setText("keyword");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));

        assertNotNull("Find listener must be registered when query executes", webView.currentFindListener);

        // Scenario A: 0 matches found
        webView.currentFindListener.onFindResultReceived(0, 0, true);
        assertFalse("Previous button must be disabled with 0 matches", btnPrev.isEnabled());
        assertFalse("Next button must be disabled with 0 matches", btnNext.isEnabled());
        assertEquals("0/0", count.getText().toString());

        btnNext.performClick();
        btnPrev.performClick();
        assertEquals("No findNext calls should be routed when matches count is 0",
                0, webView.findNextCalls.size());

        // Scenario B: 4 matches found, active match 0 (1/4)
        webView.currentFindListener.onFindResultReceived(0, 4, true);
        assertTrue("Previous button must be enabled with matches", btnPrev.isEnabled());
        assertTrue("Next button must be enabled with matches", btnNext.isEnabled());
        assertEquals("1/4", count.getText().toString());

        // Forward navigation
        btnNext.performClick();
        assertEquals(1, webView.findNextCalls.size());
        assertTrue("btnFindNext must route findNext(true)", webView.findNextCalls.get(0));

        // Backward navigation
        btnPrev.performClick();
        assertEquals(2, webView.findNextCalls.size());
        assertFalse("btnFindPrevious must route findNext(false)", webView.findNextCalls.get(1));
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static final class FakeFindWebView extends WebView {
        final List<String> findAllAsyncCalls = new ArrayList<>();
        final List<Boolean> findNextCalls = new ArrayList<>();
        int clearMatchesCalls;
        FindListener currentFindListener;
        int backs;
        boolean canGoBackValue;
        String fakeUrl;

        FakeFindWebView(Context context) {
            super(context);
        }

        @Override
        public String getUrl() {
            return fakeUrl;
        }

        @Override
        public boolean canGoBack() {
            return canGoBackValue;
        }

        @Override
        public void goBack() {
            backs++;
        }

        @Override
        public void setFindListener(FindListener listener) {
            this.currentFindListener = listener;
        }

        @Override
        public void findAllAsync(String find) {
            findAllAsyncCalls.add(find);
        }

        @Override
        public void findNext(boolean forward) {
            findNextCalls.add(forward);
        }

        @Override
        public void clearMatches() {
            clearMatchesCalls++;
        }
    }
}
