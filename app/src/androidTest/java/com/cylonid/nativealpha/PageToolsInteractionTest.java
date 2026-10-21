package com.cylonid.nativealpha;

import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Uses real WebView matching and clipboard; the fixture needs no network or user account. */
@RunWith(AndroidJUnit4.class)
public class PageToolsInteractionTest {
    @Test public void copyAndNativeFindWorkWithoutCoveringThePage() throws Exception { runScenario(false); }
    @Test public void framelessToolsExposeReaderWithoutSettings() throws Exception { runScenario(true); }
    @Test public void deliveredArticleBodyIsAvailableThroughExistingReaderButton() throws Exception { runScenario(false, true); }

    private void runScenario(boolean frameless) throws Exception {
        runScenario(frameless, false);
    }

    private void runScenario(boolean frameless, boolean structuredBody) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.SharedPreferences readerPreferences = instrumentation.getTargetContext()
                .getSharedPreferences("reader_presentation", android.content.Context.MODE_PRIVATE);
        boolean hadTheme = readerPreferences.contains("theme");
        boolean hadSize = readerPreferences.contains("fontSize");
        int savedTheme = readerPreferences.getInt("theme", 0);
        int savedSize = readerPreferences.getInt("fontSize", 20);
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        int id = manager.getIncrementedID();
        WebApp app = new WebApp("https://verification.invalid/", id);
        app.setTitle("Page tools verification");
        app.setContainerId(-1);
        app.setUseContainer(false);
        app.setOverrideGlobalSettings(true);
        app.setUseAdblock(false);
        app.setAllowMediaPlaybackInBackground(false);
        app.setFrameless(frameless);
        app.setAllowJs(true);
        manager.addWebsite(app);
        WebViewActivity activity = null;
        try {
            activity = (WebViewActivity) instrumentation.startActivitySync(new Intent(
                    instrumentation.getTargetContext(), WebViewActivity.class)
                    .putExtra(Const.INTENT_WEBAPPID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle());
            final WebViewActivity browser = activity;
            final String currentUrl = "https://verification.invalid/article?source=copy#section";
            instrumentation.runOnMainSync(() -> {
                WebView web = browser.findViewById(R.id.webview);
                web.stopLoading();
                web.loadDataWithBaseURL(currentUrl, "<meta name='viewport' content='width=device-width'><article><h1>Page tools</h1><p>Orchid first. Reading an article should preserve the complete document, its paragraphs, and the original website position. This substantial introduction explains why a clear reading surface makes long writing easier to follow without changing the website session.</p><p>Orchid second. A mature extraction library can distinguish substantial article prose from navigation links and other unrelated panels. These sentences provide enough natural connected writing to exercise actual article extraction in the Android WebView.</p><p>Orchid third. Returning to the original page should keep the same browser document rather than reload it. Text size and reading colors should improve comfort while keeping scripts and network requests disabled in the generated reading document.</p><p style='min-height:1800px'>Supplemental article prose explains reading continuity while leaving room for a long source document.</p></article><button style='position:fixed;bottom:0;right:0'>Website bottom control</button>", "text/html", "UTF-8", currentUrl);
            });
            await(instrumentation, () -> {
                WebView web = browser.findViewById(R.id.webview);
                return currentUrl.equals(web.getUrl()) && web.getProgress() >= 100;
            }, "Fixture must finish loading");
            await(instrumentation, browser::hasWindowFocus, "Test activity must own the foreground before touch injection");
            AtomicReference<String> originalDom = new AtomicReference<>();
            if (structuredBody) {
                instrumentation.runOnMainSync(() -> ((WebView) browser.findViewById(R.id.webview)).evaluateJavascript(
                        "(function(){var data=document.createElement('script');data.type='application/ld+json';"
                        + "data.textContent=JSON.stringify({'@type':'NewsArticle',url:location.href,headline:'Page tools',"
                        + "articleBody:document.querySelector('article').textContent+' Additional already delivered article prose. '.repeat(20)+'DeliveredEndingMarker'});"
                        + "document.head.appendChild(data);var hydration=document.createElement('script');hydration.type='application/json';"
                        + "hydration.textContent=JSON.stringify({state:'x'.repeat(1600000)});document.body.appendChild(hydration);"
                        + "return document.documentElement.outerHTML;})()", originalDom::set));
                await(instrumentation, () -> originalDom.get() != null, "Structured fixture must be ready");
            }
            // Exercise the same quick-action clicks used on the real device.
            openTools(instrumentation, browser);
            tapView(instrumentation, browser, frameless ? R.id.btnPageToolsCopy : R.id.cardCopyUrl);
            SystemClock.sleep(350);
            instrumentation.runOnMainSync(() -> {
                ClipboardManager clipboard = browser.getSystemService(ClipboardManager.class);
                assertEquals("Copy must use the active article URL, including query and fragment", currentUrl,
                        clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            });
            openTools(instrumentation, browser);
            tapView(instrumentation, browser, frameless ? R.id.btnPageToolsFind : R.id.cardFindInPage);
            instrumentation.runOnMainSync(() -> ((EditText) browser.findViewById(R.id.findPageQuery)).setText("Orchid"));
            await(instrumentation, () -> ((TextView) browser.findViewById(R.id.findPageCount)).getText().toString().endsWith("/3"),
                    "Native WebView must report all three matches");
            AtomicReference<String> first = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                first.set(((TextView) browser.findViewById(R.id.findPageCount)).getText().toString());
            });
            tapView(instrumentation, browser, R.id.btnFindNext);
            await(instrumentation, () -> !first.get().equals(((TextView) browser.findViewById(R.id.findPageCount)).getText().toString()), "Next match must move");
            tapView(instrumentation, browser, R.id.btnFindPrevious);
            await(instrumentation, () -> first.get().equals(((TextView) browser.findViewById(R.id.findPageCount)).getText().toString()), "Previous match must return");
            instrumentation.runOnMainSync(() -> {
                View bar = browser.findViewById(R.id.browserFindBar);
                View content = browser.findViewById(R.id.browserWebContent);
                assertTrue("Find bar must reserve its own space", content.getTop() >= bar.getBottom());
                browser.getSystemService(android.view.inputmethod.InputMethodManager.class)
                        .hideSoftInputFromWindow(bar.getWindowToken(), 0);
            });
            SystemClock.sleep(400);
            // Optional: screen sharing on Android preview builds can own UiAutomation.
            if ("true".equals(InstrumentationRegistry.getArguments().getString("captureScreenshots"))) {
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(
                    instrumentation.getTargetContext().getExternalFilesDir(null), "page-tools-native-find.png"))) {
                instrumentation.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            }
            }
            instrumentation.runOnMainSync(() -> ((EditText) browser.findViewById(R.id.findPageQuery)).setText("zz-no-such-word"));
            await(instrumentation, () -> !browser.findViewById(R.id.btnFindNext).isEnabled()
                    && ((TextView) browser.findViewById(R.id.findPageCount)).length() > 0, "No matches disables navigation");
            instrumentation.runOnMainSync(browser::onBackPressed);
            instrumentation.runOnMainSync(() -> {
                assertEquals(View.GONE, browser.findViewById(R.id.browserFindBar).getVisibility());
                assertFalse("Closing find must keep the Lite App open", browser.isFinishing());
                assertEquals(currentUrl, ((WebView) browser.findViewById(R.id.webview)).getUrl());
            });
            instrumentation.runOnMainSync(() -> ((WebView) browser.findViewById(R.id.webview)).scrollTo(0, 300));
            openTools(instrumentation, browser);
            tapView(instrumentation, browser, frameless ? R.id.btnPageToolsReader : R.id.cardReaderMode);
            await(instrumentation, () -> browser.findViewById(R.id.browserReaderControls).getVisibility() == View.VISIBLE,
                    "Reader must extract an actual article");
            await(instrumentation, () -> !((androidx.drawerlayout.widget.DrawerLayout) browser.findViewById(
                    R.id.browser_drawer_container)).isDrawerVisible(androidx.core.view.GravityCompat.END),
                    "Quick panel must finish its closing animation");
            await(instrumentation, () -> readerView(browser).getProgress() == 100, "Reader document must finish rendering");
            if (structuredBody && "true".equals(InstrumentationRegistry.getArguments().getString("readerVisualCheck"))) {
                android.os.Bundle status = new android.os.Bundle();
                status.putString("stream", "READER_VISUAL_READY\n");
                instrumentation.sendStatus(2, status);
                SystemClock.sleep(3000);
            }
            instrumentation.runOnMainSync(() -> {
                assertTrue("Host selection inspection requires JavaScript; CSP blocks website scripts",
                        readerView(browser).getSettings().getJavaScriptEnabled());
                assertTrue("Reader document must not fetch remote resources", readerView(browser).getSettings().getBlockNetworkLoads());
                assertFalse(readerView(browser).getSettings().getAllowFileAccess());
                assertEquals("Original page remains intact behind reader", View.GONE,
                        browser.findViewById(R.id.webview).getVisibility());
                browser.findViewById(R.id.btnReaderTheme).performClick();
                browser.findViewById(R.id.btnReaderLarger).performClick();
            });
            await(instrumentation, () -> readerView(browser).getProgress() == 100, "Reader settings must finish rendering");
            openTools(instrumentation, browser);
            tapView(instrumentation, browser, frameless ? R.id.btnPageToolsFind : R.id.cardFindInPage);
            instrumentation.runOnMainSync(() -> ((EditText) browser.findViewById(R.id.findPageQuery)).setText("Orchid"));
            await(instrumentation, () -> ((TextView) browser.findViewById(R.id.findPageCount)).getText().toString().endsWith("/3"),
                    "Find must search the local reader document");
            if (structuredBody) {
                instrumentation.runOnMainSync(() -> ((EditText) browser.findViewById(R.id.findPageQuery)).setText("DeliveredEndingMarker"));
                await(instrumentation, () -> ((TextView) browser.findViewById(R.id.findPageCount)).getText().toString().endsWith("/1"),
                        "Existing Reader button must expose the ending received only in JSON-LD");
            }
            await(instrumentation, () -> !((androidx.drawerlayout.widget.DrawerLayout) browser.findViewById(
                    R.id.browser_drawer_container)).isDrawerVisible(androidx.core.view.GravityCompat.END), "Panel must close");
            instrumentation.runOnMainSync(browser::onBackPressed);
            instrumentation.runOnMainSync(() -> assertEquals(View.VISIBLE,
                    browser.findViewById(R.id.browserReaderControls).getVisibility()));
            instrumentation.runOnMainSync(browser::onBackPressed);
            instrumentation.runOnMainSync(() -> {
                assertEquals(View.GONE, browser.findViewById(R.id.browserReaderControls).getVisibility());
                assertEquals(View.VISIBLE, browser.findViewById(R.id.webview).getVisibility());
                assertEquals("Exit reader must keep original URL", currentUrl,
                        ((WebView) browser.findViewById(R.id.webview)).getUrl());
                assertFalse(browser.isFinishing());
            });
            await(instrumentation, () -> Math.abs(((WebView) browser.findViewById(R.id.webview)).getScrollY() - 300) <= 2,
                    "Exit reader must restore the original scroll position after layout");
            if (structuredBody) {
                AtomicReference<String> restoredDom = new AtomicReference<>();
                instrumentation.runOnMainSync(() -> ((WebView) browser.findViewById(R.id.webview)).evaluateJavascript(
                        "document.documentElement.outerHTML", restoredDom::set));
                await(instrumentation, () -> restoredDom.get() != null, "Original document must remain accessible");
                assertEquals("Extraction must not change the source document", originalDom.get(), restoredDom.get());
            }
        } finally {
            if (activity != null) {
                final WebViewActivity closing = activity;
                instrumentation.runOnMainSync(closing::finish);
                instrumentation.waitForIdleSync();
            }
            android.content.SharedPreferences.Editor restoreReaderPreferences = readerPreferences.edit();
            if (hadTheme) restoreReaderPreferences.putInt("theme", savedTheme); else restoreReaderPreferences.remove("theme");
            if (hadSize) restoreReaderPreferences.putInt("fontSize", savedSize); else restoreReaderPreferences.remove("fontSize");
            restoreReaderPreferences.commit();
            manager.loadAppData();
            manager.getWebsites().removeIf(candidate -> candidate.getID() == id);
            manager.saveWebAppData();
        }
    }
    private static WebView readerView(WebViewActivity browser) {
        android.widget.FrameLayout content = browser.findViewById(R.id.browserWebContent);
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof com.cylonid.nativealpha.browser.ReaderWebView)
                return (WebView) content.getChildAt(i);
        }
        throw new AssertionError("Reader WebView missing");
    }
    private static void openTools(Instrumentation instrumentation, WebViewActivity browser) {
        AtomicReference<Boolean> frameless = new AtomicReference<>(false);
        instrumentation.runOnMainSync(() -> frameless.set(browser.findViewById(R.id.browser_toolbar_container).getVisibility() == View.GONE));
        if (!frameless.get()) {
            tapView(instrumentation, browser, R.id.btnToolbarQuickSettings);
        } else {
            int[] bounds = new int[4];
            instrumentation.runOnMainSync(() -> {
                View drawer = browser.findViewById(R.id.browser_drawer_container);
                drawer.getLocationOnScreen(bounds);
                bounds[2] = drawer.getWidth();
                bounds[3] = drawer.getHeight();
            });
            float density = browser.getResources().getDisplayMetrics().density;
            int x = bounds[0] + bounds[2] - 2;
            int y = bounds[1] + bounds[3] - (int) (100 * density);
            long time = SystemClock.uptimeMillis();
            for (int i = 0; i <= 12; i++) {
                int action = i == 0 ? 0 : i == 12 ? 1 : 2;
                android.view.MotionEvent event = android.view.MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                        x - bounds[2] * .75f * i / 12f, y, 0);
                instrumentation.sendPointerSync(event);
                event.recycle();
                SystemClock.sleep(25);
            }
        }
        SystemClock.sleep(400);
        instrumentation.runOnMainSync(() -> {
            androidx.drawerlayout.widget.DrawerLayout drawer = browser.findViewById(R.id.browser_drawer_container);
            assertTrue("Lower-edge swipe must open page tools", drawer.isDrawerOpen(androidx.core.view.GravityCompat.END));
            if (frameless.get()) {
                assertEquals(View.VISIBLE, browser.findViewById(R.id.framelessPageTools).getVisibility());
                assertFalse("App settings must not be exposed", browser.findViewById(R.id.btnQuickMoreSettings).isShown());
                assertFalse(browser.findViewById(R.id.switchQuickJavaScript).isShown());
                assertFalse(browser.findViewById(R.id.btnQuickSwitchSandbox).isShown());
            }
        });
    }
    private static void tapView(Instrumentation instrumentation, WebViewActivity browser, int id) {
        int[] point = new int[2];
        instrumentation.runOnMainSync(() -> {
            View view = browser.findViewById(id);
            assertTrue("Touch target must be visible", view.isShown());
            view.getLocationOnScreen(point);
            point[0] += view.getWidth() / 2;
            point[1] += view.getHeight() / 2;
        });
        long now = SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, 0, point[0], point[1], 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 60, 1, point[0], point[1], 0);
        instrumentation.sendPointerSync(down);
        instrumentation.sendPointerSync(up);
        down.recycle(); up.recycle();
        instrumentation.waitForIdleSync();
    }
    private interface Check { boolean ready(); }
    private static void await(Instrumentation instrumentation, Check check, String message) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            instrumentation.runOnMainSync(() -> ready.set(check.ready()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail(message);
    }
}
