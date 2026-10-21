package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.webkit.WebView;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BrowserPrintController}, testing print document adapter generation,
 * job name sanitization, and system PrintManager collaboration.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BrowserPrintControllerTest {

    private Activity activity;
    private WebApp webApp;
    private WebView webView;
    private TestCallback callback;
    private BrowserPrintController controller;
    private TestPrintExecutor testExecutor;

    private static class TestCallback implements BrowserPrintController.Callback {
        WebApp app;
        WebView wv;

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
    }

    private static class TestPrintExecutor implements BrowserPrintController.PrintExecutor {
        boolean executed = false;
        String capturedJobName = null;
        PrintDocumentAdapter capturedAdapter = null;
        PrintAttributes capturedAttributes = null;
        boolean resultToReturn = true;

        @Override
        public boolean print(@NonNull String printJobName, @NonNull PrintDocumentAdapter printAdapter, @NonNull PrintAttributes attributes) {
            this.executed = true;
            this.capturedJobName = printJobName;
            this.capturedAdapter = printAdapter;
            this.capturedAttributes = attributes;
            return resultToReturn;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme);

        webApp = new WebApp("https://example.org", 0);
        webApp.setTitle("Example Website");

        webView = new WebView(activity);
        testExecutor = new TestPrintExecutor();

        callback = new TestCallback(webApp, webView);
        controller = new BrowserPrintController(activity, callback);
        controller.setPrintExecutor(testExecutor);
        controller.setPrintSupportedOverride(true);
    }

    @After
    public void tearDown() {
        webView.destroy();
    }

    @Test
    public void sanitizeJobName_replacesIllegalCharactersAndTrims() {
        assertEquals("Fallback", BrowserPrintController.sanitizeJobName(null, "Fallback"));
        assertEquals("Fallback", BrowserPrintController.sanitizeJobName("   ", "Fallback"));
        assertEquals("Report_2026_Final", BrowserPrintController.sanitizeJobName("Report/2026:Final", "Fallback"));
        assertEquals("Dangerous_File_Name_", BrowserPrintController.sanitizeJobName("Dangerous*File?Name|", "Fallback"));

        String longTitle = "A".repeat(80);
        String sanitized = BrowserPrintController.sanitizeJobName(longTitle, "Fallback");
        assertEquals(60, sanitized.length());
    }

    @Test
    public void isPrintSupported_respectsOverride() {
        controller.setPrintSupportedOverride(false);
        assertFalse(controller.isPrintSupported());

        controller.setPrintSupportedOverride(true);
        assertTrue(controller.isPrintSupported());
    }

    @Test
    public void print_whenWebViewNull_returnsFalse() {
        callback.wv = null;
        assertFalse(controller.print());
        assertFalse(testExecutor.executed);
    }

    @Test
    public void print_whenNotSupported_returnsFalse() {
        controller.setPrintSupportedOverride(false);
        assertFalse(controller.print(webView, "Test Title"));
        assertFalse(testExecutor.executed);
    }

    @Test
    public void print_whenSupported_invokesPrintExecutorWithSanitizedName() {
        boolean result = controller.print();
        assertTrue(result);

        assertTrue(testExecutor.executed);
        assertEquals("Example Website", testExecutor.capturedJobName);
        assertNotNull(testExecutor.capturedAdapter);
        assertNotNull(testExecutor.capturedAttributes);
    }

    @Test
    public void print_whenExecutorFails_returnsFalse() {
        testExecutor.resultToReturn = false;
        boolean result = controller.print(webView, "Failing Page");
        assertFalse(result);
        assertTrue(testExecutor.executed);
    }

    @Test
    public void createPrintDocumentAdapter_createsNonNullAdapter() {
        PrintDocumentAdapter adapter = controller.createPrintDocumentAdapter(webView, "DocumentName");
        assertNotNull(adapter);
    }
}
