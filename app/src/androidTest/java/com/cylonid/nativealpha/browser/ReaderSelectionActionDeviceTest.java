package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.cylonid.nativealpha.R;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReaderSelectionActionDeviceTest {
    @Test public void explicitClickDeliversSelectionEvenWhenNativeMenuClosesFirst() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(new Intent()
                .setClassName(instrumentation.getTargetContext(), "com.cylonid.nativealpha.testing.ReaderSelectionProbeActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<ReaderWebView> web = new AtomicReference<>();
        AtomicReference<String> selected = new AtomicReference<>();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        CountDownLatch rangeReady = new CountDownLatch(1);
        String text = "Reading should be comfortable. "
                + "The translation action belongs to this reader document. "
                + "Selecting prose must not send a request before an explicit tap. "
                + "Closing the native selection menu must not discard that tap.";
        try {
            JSONObject article = new JSONObject().put("title", "Selection test").put("content", "<p>" + text + "</p>");
            String html = ReaderDocument.fromJavascript(JSONObject.quote(article.toString()), "https://news.example.org/article").html(0, 20);
            instrumentation.runOnMainSync(() -> {
                ReaderWebView view = new ReaderWebView(activity, value -> { selected.set(value); delivered.countDown(); });
                web.set(view);
                activity.setContentView(view);
                view.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView v, String url) { loaded.countDown(); }
                });
                // Reproduces the old loader: history URL absent; getUrl may be about:blank.
                view.loadDataWithBaseURL("https://reader.shellalpha.invalid/", html, "text/html", "UTF-8", null);
            });
            assertTrue(loaded.await(8, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> web.get().evaluateJavascript(
                    "(function(){var r=document.createRange();r.selectNodeContents(document.querySelector('article p'));"
                    + "var s=window.getSelection();s.removeAllRanges();s.addRange(r);return s.toString();})()",
                    value -> rangeReady.countDown()));
            assertTrue(rangeReady.await(5, TimeUnit.SECONDS));
            assertNull("Selection alone must not translate", selected.get());
            instrumentation.runOnMainSync(() -> {
                ReaderWebView view = web.get();
                ActionMode.Callback callback = view.selectionCallback(new ActionMode.Callback() {
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) { return true; }
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
                    public void onDestroyActionMode(ActionMode mode) { }
                });
                TestActionMode mode = new TestActionMode();
                Menu menu = new MenuBuilder(activity);
                assertTrue(callback.onCreateActionMode(mode, menu));
                assertTrue(callback.onActionItemClicked(mode, menu.findItem(R.id.readerTranslateSelection)));
                callback.onDestroyActionMode(mode);
            });
            assertTrue("An explicit translate tap must deliver text instead of silently returning", delivered.await(5, TimeUnit.SECONDS));
            assertEquals(text, selected.get());
        } finally {
            instrumentation.runOnMainSync(() -> { if (web.get() != null) { web.get().deactivate(); web.get().destroy(); } activity.finish(); });
        }
    }

    private static final class TestActionMode extends ActionMode {
        public void invalidate() { }
        public void finish() { }
        public void setTitle(CharSequence value) { }
        public void setTitle(int value) { }
        public void setSubtitle(CharSequence value) { }
        public void setSubtitle(int value) { }
        public void setCustomView(View view) { }
        public CharSequence getTitle() { return ""; }
        public CharSequence getSubtitle() { return ""; }
        public View getCustomView() { return null; }
        public Menu getMenu() { return null; }
        public android.view.MenuInflater getMenuInflater() { return null; }
    }
}
