package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.PopupMenu;

import androidx.annotation.Nullable;

import com.cylonid.nativealpha.MainActivity;
import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import io.github.edsuns.adfilter.AdFilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserContextMenuControllerTest {

    private Activity activity;
    private WebApp webApp;
    private WebView webView;
    private TestCallback callback;
    private BrowserContextMenuController controller;
    private View anchorView;

    private static class TestCallback implements BrowserContextMenuController.Callback {
        WebApp app;
        WebView wv;
        boolean backCalled = false;
        boolean reloadCalled = false;
        boolean findInPageCalled = false;
        boolean quickSettingsCalled = false;
        boolean finishAndRemoveTaskCalled = false;
        boolean fallbackLongClick = false;
        String requestedSandboxUrl = null;
        boolean sandboxDialogCalled = false;
        boolean drawerClosed = false;
        boolean printPageCalled = false;

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
        public WebView getWebView() {
            return wv;
        }

        @Nullable
        @Override
        public AdFilter getAdFilter() {
            return null;
        }

        @Override
        public void goBack() {
            backCalled = true;
        }

        @Override
        public void reload() {
            reloadCalled = true;
        }

        @Override
        public void openFindInPage() {
            findInPageCalled = true;
        }

        @Override
        public void openQuickSettings() {
            quickSettingsCalled = true;
        }

        @Override
        public void finishAndRemoveTask() {
            finishAndRemoveTaskCalled = true;
        }

        @Override
        public void setFallbackLongClick(boolean fallback) {
            fallbackLongClick = fallback;
        }

        @Override
        public void showSandboxDialog(@Nullable String linkUrl) {
            sandboxDialogCalled = true;
            requestedSandboxUrl = linkUrl;
        }

        @Override
        public void closeDrawer() {
            drawerClosed = true;
        }

        @Override
        public void printPage() {
            printPageCalled = true;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
        activity.setTheme(R.style.AppTheme);

        anchorView = new View(activity);

        webApp = new WebApp("https://example.com/base", 1);
        webApp.setFrameless(false);

        webView = new WebView(activity);
        webView.loadUrl("https://example.com/page");

        callback = new TestCallback(webApp, webView);
        controller = new BrowserContextMenuController(activity, callback);
    }

    @Test
    public void showContextMenuIgnoredWhenFrameless() {
        webApp.setFrameless(true);
        controller.showContextMenu(anchorView, null);

        assertNull("Menu should not be created in frameless mode", controller.getPopupMenu());
    }

    @Test
    public void showContextMenuInflatesAndShowsMenu() {
        controller.showContextMenu(anchorView, "https://example.com/link");

        PopupMenu menu = controller.getPopupMenu();
        assertNotNull("Menu must be created", menu);

        MenuItem sandboxItem = menu.getMenu().findItem(R.id.cmOpenInSandbox);
        assertNotNull(sandboxItem);
        assertEquals(activity.getString(R.string.open_link_in_sandbox), sandboxItem.getTitle().toString());
    }

    @Test
    public void copyUrlToClipboardCopiesToSystemClipboard() {
        controller.copyUrlToClipboard("https://example.com/copied-url");

        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertNotNull(clipboard);
        ClipData primaryClip = clipboard.getPrimaryClip();
        assertNotNull("Primary clip should not be null", primaryClip);
        assertEquals(1, primaryClip.getItemCount());
        assertEquals("https://example.com/copied-url", primaryClip.getItemAt(0).getText().toString());
        assertTrue("Drawer should close on copy", callback.drawerClosed);
    }

    @Test
    public void shareUrlLaunchesShareChooser() {
        controller.shareUrl("https://example.com/share-target");

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        Intent chooserIntent = shadowActivity.getNextStartedActivity();
        assertNotNull("Share chooser intent must be launched", chooserIntent);
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());

        Intent sendIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(sendIntent);
        assertEquals(Intent.ACTION_SEND, sendIntent.getAction());
        assertEquals("https://example.com/share-target", sendIntent.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    public void openUrlExternallyLaunchesViewIntent() {
        controller.openUrlExternally("https://example.com/external");

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        Intent viewIntent = shadowActivity.getNextStartedActivity();
        assertNotNull("ACTION_VIEW intent should be launched", viewIntent);
        assertEquals(Intent.ACTION_VIEW, viewIntent.getAction());
        assertEquals("https://example.com/external", viewIntent.getDataString());
    }

    @Test
    public void dismissMenuClosesActivePopup() {
        controller.showContextMenu(anchorView, null);
        assertNotNull(controller.getPopupMenu());

        controller.dismissMenu();
        assertNull("Menu should be dismissed and cleared", controller.getPopupMenu());
    }

    @Test
    public void currentPageUrlFallsBackToBaseUrlWhenWebViewEmpty() {
        webView = new WebView(activity);
        callback.wv = webView;

        String url = controller.currentPageUrl();
        assertEquals("https://example.com/base", url);
    }

    @Test
    public void contextMenuHasPrintItem() {
        controller.showContextMenu(anchorView, null);
        PopupMenu menu = controller.getPopupMenu();
        assertNotNull(menu);
        MenuItem printItem = menu.getMenu().findItem(R.id.cmItemPrint);
        assertNotNull("Print item should be in context menu", printItem);
    }
}
