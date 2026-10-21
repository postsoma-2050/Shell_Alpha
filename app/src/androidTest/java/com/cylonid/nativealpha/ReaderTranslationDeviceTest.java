package com.cylonid.nativealpha;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.cylonid.nativealpha.browser.ReaderTranslation;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Reader scope checks; the separately selected live probe uses only synthetic text. */
@RunWith(AndroidJUnit4.class)
public class ReaderTranslationDeviceTest {
    @Test public void translationControlsExistOnlyDuringReaderSession() throws Exception {
        android.app.Instrumentation instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        android.app.Activity activity = instrumentation.startActivitySync(new android.content.Intent()
                .setClassName(instrumentation.getTargetContext(), "com.cylonid.nativealpha.testing.ReaderSelectionProbeActivity")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<android.webkit.WebView> source = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<android.view.View> controls = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<com.cylonid.nativealpha.browser.ReaderModeController> controller = new java.util.concurrent.atomic.AtomicReference<>();
        android.content.SharedPreferences preferences = activity.getSharedPreferences("reader_presentation", 0);
        boolean previous = preferences.getBoolean("translationEnabled", true);
        try {
            instrumentation.runOnMainSync(() -> {
                android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
                root.setOrientation(android.widget.LinearLayout.VERTICAL);
                android.widget.FrameLayout content = new android.widget.FrameLayout(activity);
                root.addView(content, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
                android.view.View bar = activity.getLayoutInflater().inflate(R.layout.browser_reader_controls, root, false);
                controls.set(bar);
                root.addView(bar);
                activity.setContentView(root);
                preferences.edit().putBoolean("translationEnabled", true).apply();
                controller.set(new com.cylonid.nativealpha.browser.ReaderModeController(content, bar, () -> { }, () -> { }, url -> { }));
                android.webkit.WebView view = new android.webkit.WebView(activity);
                source.set(view);
                content.addView(view);
                view.getSettings().setJavaScriptEnabled(true);
                view.getSettings().setBlockNetworkLoads(true);
                view.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override public void onPageFinished(android.webkit.WebView v, String url) { loaded.countDown(); }
                });
                view.loadDataWithBaseURL("https://news.example.org/article", "<title>Reading session</title><article><h1>Reading session</h1><div itemprop='articleBody'><p>"
                        + new String(new char[20]).replace("\0", "Reading should be comfortable. This paragraph is available to the reader without changing the original page. ")
                        + "</p></div></article>", "text/html", "UTF-8", "https://news.example.org/article");
                assertEquals(android.view.View.GONE, bar.getVisibility());
            });
            assertTrue(loaded.await(8, java.util.concurrent.TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> controller.get().open(source.get()));
            java.util.concurrent.atomic.AtomicBoolean open = new java.util.concurrent.atomic.AtomicBoolean();
            long deadline = android.os.SystemClock.uptimeMillis() + 8000;
            while (!open.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync(() -> open.set(controller.get().isOpen()));
                if (!open.get()) Thread.sleep(50);
            }
            assertTrue("The synthetic article must open Reader Mode", open.get());
            instrumentation.runOnMainSync(() -> {
                assertEquals(android.view.View.VISIBLE, controls.get().getVisibility());
                assertEquals(android.view.View.GONE, source.get().getVisibility());
                android.view.View toggle = controls.get().findViewById(R.id.btnReaderTranslation);
                assertTrue(toggle.isSelected());
                toggle.performClick();
                assertFalse(toggle.isSelected());
                toggle.performClick();
                assertTrue(toggle.isSelected());
                controls.get().findViewById(R.id.btnReaderClose).performClick();
                assertFalse(controller.get().isOpen());
                assertNull(controller.get().getWebView());
                assertEquals(android.view.View.GONE, controls.get().getVisibility());
                assertEquals(android.view.View.VISIBLE, source.get().getVisibility());
                assertTrue("Reader settings must not alter the original page JS setting", source.get().getSettings().getJavaScriptEnabled());
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (controller.get() != null) controller.get().destroy();
                if (source.get() != null) source.get().destroy();
                preferences.edit().putBoolean("translationEnabled", previous).apply();
                activity.finish();
            });
        }
    }
    @Test public void readerCspBlocksPageScriptsWhileHostCanReadSelection() throws Exception {
        android.app.Instrumentation instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        android.app.Activity activity = instrumentation.startActivitySync(new android.content.Intent()
                .setClassName(instrumentation.getTargetContext(), "com.cylonid.nativealpha.testing.ReaderSelectionProbeActivity")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch evaluated = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<android.webkit.WebView> web = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> result = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> {
                android.webkit.WebView view = new com.cylonid.nativealpha.browser.ReaderWebView(activity, text -> { });
                web.set(view);
                activity.setContentView(view);
                view.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override public void onPageFinished(android.webkit.WebView v, String url) { loaded.countDown(); }
                });
                try {
                    org.json.JSONObject article = new org.json.JSONObject();
                    article.put("title", "Reader security check");
                    article.put("content", "<p>" + new String(new char[12]).replace("\0", "Reader selection. ") + "</p>");
                    com.cylonid.nativealpha.browser.ReaderDocument document =
                            com.cylonid.nativealpha.browser.ReaderDocument.fromJavascript(
                                    org.json.JSONObject.quote(article.toString()), "https://news.example.org/article");
                    // Deliberately append a script AFTER sanitation to exercise the actual document CSP.
                    view.loadDataWithBaseURL("https://reader.shellalpha.invalid/", document.html(0, 20)
                            .replace("</body>", "<script>window.pageScriptRan=true;</script></body>"),
                            "text/html", "UTF-8", "https://reader.shellalpha.invalid/");
                } catch (Exception error) { throw new AssertionError(error); }
            });
            assertTrue(loaded.await(8, java.util.concurrent.TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> web.get().evaluateJavascript(
                    "(function(){var r=document.createRange();r.selectNodeContents(document.querySelector('article p'));"
                    + "var s=window.getSelection();s.removeAllRanges();s.addRange(r);return s.toString()+':'+typeof window.pageScriptRan;})()",
                    value -> { result.set(value); evaluated.countDown(); }));
            assertTrue(evaluated.await(5, java.util.concurrent.TimeUnit.SECONDS));
            String decoded = (String) new org.json.JSONTokener(result.get()).nextValue();
            assertTrue(decoded.startsWith("Reader selection."));
            assertTrue("Inline page scripts must be blocked by CSP", decoded.endsWith(":undefined"));
            instrumentation.runOnMainSync(() -> {
                assertTrue(web.get().getSettings().getBlockNetworkLoads());
                assertFalse(web.get().getSettings().getAllowFileAccess());
                assertFalse(web.get().getSettings().getAllowContentAccess());
            });
        } finally {
            instrumentation.runOnMainSync(() -> { if (web.get() != null) web.get().destroy(); activity.finish(); });
        }
    }
    @Test public void noGlobalProcessTextRegistration() {
        android.content.Context context = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getTargetContext();
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain").setPackage(context.getPackageName());
        assertTrue(context.getPackageManager().queryIntentActivities(intent, 0).isEmpty());
    }
    @Test public void googleTranslatesSyntheticPhraseOnDevice() throws Exception {
        String result = new ReaderTranslation.Request(androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getTargetContext()).execute("Reading should be comfortable.", "zh-CN");
        assertFalse(result.isEmpty());
        assertNotEquals("Reading should be comfortable.", result);
    }
}
