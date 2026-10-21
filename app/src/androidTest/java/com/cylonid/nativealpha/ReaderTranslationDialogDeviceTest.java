package com.cylonid.nativealpha;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.cylonid.nativealpha.browser.ReaderModeController;
import com.cylonid.nativealpha.browser.ReaderWebView;
import com.cylonid.nativealpha.browser.ReaderAppearance;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Explicit live check through Reader Mode; retained class name for existing test commands. */
@RunWith(AndroidJUnit4.class)
public class ReaderTranslationDialogDeviceTest {
    @Test public void selectedTextProducesVisibleTranslationAndCopyAction() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(new Intent()
                .setClassName(instrumentation.getTargetContext(), "com.cylonid.nativealpha.testing.ReaderSelectionProbeActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<ReaderModeController> owner = new AtomicReference<>();
        AtomicReference<FrameLayout> host = new AtomicReference<>();
        AtomicReference<WebView> source = new AtomicReference<>();
        AtomicReference<View> controls = new AtomicReference<>();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch selected = new CountDownLatch(1);
        android.content.SharedPreferences preferences = activity.getSharedPreferences("reader_presentation", 0);
        String previousLanguage = preferences.getString("translationLanguage", "zh-CN");
        int previousTheme = preferences.getInt("theme", 0);
        int theme = Integer.parseInt(InstrumentationRegistry.getArguments().getString("readerTheme", "0"));
        try {
            instrumentation.runOnMainSync(() -> {
                preferences.edit().putString("translationLanguage", "zh-CN").putInt("theme", theme).apply();
                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                View bar = activity.getLayoutInflater().inflate(R.layout.browser_reader_controls, root, false);
                controls.set(bar);
                root.addView(bar);
                FrameLayout content = new FrameLayout(activity);
                host.set(content);
                root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
                activity.setContentView(root);
                WebView page = new WebView(activity);
                source.set(page);
                content.addView(page);
                page.getSettings().setJavaScriptEnabled(true);
                page.getSettings().setBlockNetworkLoads(true);
                owner.set(new ReaderModeController(content, bar, () -> { }, () -> { }, url -> { }));
                page.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView view, String url) { loaded.countDown(); }
                });
                String text = "Reading should be comfortable. The reader keeps the article in view while translating selected prose. "
                        + "The original text and translated text should be easy to distinguish, with only relevant actions visible. "
                        + "Closing this panel should return to the same reading position.";
                page.loadDataWithBaseURL("https://news.example.org/article", "<title>A comfortable reading experience</title>"
                        + "<article><h1>A comfortable reading experience</h1><div itemprop='articleBody'><p>" + text
                        + "</p><p>" + text + "</p><p>" + text + "</p></div></article>", "text/html", "UTF-8", "https://news.example.org/article");
            });
            assertTrue(loaded.await(8, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> owner.get().open(source.get()));
            await(instrumentation, () -> owner.get().isOpen() && owner.get().getWebView().getProgress() == 100);
            final int originalWindowFlags = activity.getWindow().getAttributes().flags;
            instrumentation.runOnMainSync(() -> owner.get().getWebView().evaluateJavascript(
                    "(function(){var r=document.createRange();r.selectNodeContents(document.querySelector('article p'));"
                    + "var s=window.getSelection();s.removeAllRanges();s.addRange(r);return s.toString();})()", value -> selected.countDown()));
            assertTrue(selected.await(5, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> ((ReaderWebView) owner.get().getWebView()).translateSelection());
            await(instrumentation, () -> host.get().findViewById(R.id.readerTranslationCopy) != null
                    && host.get().findViewById(R.id.readerTranslationCopy).isEnabled());
            instrumentation.runOnMainSync(() -> {
                View panel = host.get().findViewById(R.id.readerTranslationPanel);
                String translation = ((TextView) panel.findViewById(R.id.readerTranslationResult)).getText().toString();
                assertTrue("A real Chinese translation must be visible", translation.matches("(?s).*[\\p{IsHan}].*"));
                assertSame("Panel must use the Reader window", host.get().getWindowToken(), panel.getWindowToken());
                assertEquals(originalWindowFlags, activity.getWindow().getAttributes().flags);
                assertEquals(ReaderAppearance.of(theme).ink, ((TextView) panel.findViewById(R.id.readerTranslationResult)).getCurrentTextColor());
                assertFalse(panel.findViewById(R.id.readerTranslationErrorActions).isShown());
                assertTrue("Article context remains visible", panel.getHeight() < host.get().getHeight());
            });
            if ("true".equals(InstrumentationRegistry.getArguments().getString("readerVisualCheck"))) {
                android.graphics.Bitmap capture = instrumentation.getUiAutomation().takeScreenshot();
                java.io.File output = new java.io.File(activity.getExternalFilesDir(null), "reader-translation-theme-" + theme + ".png");
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
                    capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
                } finally { capture.recycle(); }
            }
            instrumentation.runOnMainSync(() -> {
                assertTrue(owner.get().dismissTranslation());
                assertNull(host.get().findViewById(R.id.readerTranslationPanel));
                assertTrue("Dismiss translation must keep reading open", owner.get().isOpen());
                assertEquals(View.VISIBLE, controls.get().getVisibility());
                assertFalse(owner.get().dismissTranslation());
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (owner.get() != null) owner.get().destroy();
                if (source.get() != null) source.get().destroy();
                preferences.edit().putString("translationLanguage", previousLanguage).putInt("theme", previousTheme).apply();
                activity.finish();
            });
        }
    }
    private interface Condition { boolean ready(); }
    private static void await(Instrumentation instrumentation, Condition condition) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 11000;
        while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> ready.set(condition.ready()));
            if (!ready.get()) Thread.sleep(50);
        }
        assertTrue("Reader/translation must complete within its request timeout", ready.get());
    }
}
