package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BrowserPageToolsController}, verifying frameless vs framed
 * visibility toggles, safe insets application, drawer actions, find routing, and back press handling.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserPageToolsControllerTest {

    private Activity activity;
    private View root;
    private DrawerLayout drawerLayout;
    private WebApp webApp;
    private WebView activeWebView;
    private WebView readerWebView;
    private PageFindController pageFindController;
    private TestCallback callback;
    private BrowserPageToolsController controller;

    private static class TestCallback implements BrowserPageToolsController.Callback {
        WebApp app;
        WebView activeWv;
        WebView readerWv;
        DrawerLayout drawer;
        boolean readerOpen = false;
        boolean mediaCustomViewShowing = false;
        boolean openReaderModeCalled = false;
        boolean copyCurrentUrlCalled = false;
        boolean closeDrawerCalled = false;
        boolean openQuickSettingsCalled = false;
        int onFindStateChangedCount = 0;

        @Nullable
        @Override
        public WebApp getWebApp() {
            return app;
        }

        @Nullable
        @Override
        public WebView getActiveWebView() {
            return activeWv;
        }

        @Nullable
        @Override
        public WebView getReaderWebView() {
            return readerWv;
        }

        @Override
        public boolean isReaderOpen() {
            return readerOpen;
        }

        @Override
        public boolean isMediaCustomViewShowing() {
            return mediaCustomViewShowing;
        }

        @Nullable
        @Override
        public DrawerLayout getDrawerLayout() {
            return drawer;
        }

        @Override
        public void openReaderMode() {
            openReaderModeCalled = true;
        }

        @Override
        public void copyCurrentUrl() {
            copyCurrentUrlCalled = true;
        }

        @Override
        public void closeDrawer() {
            closeDrawerCalled = true;
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.END);
            }
        }

        @Override
        public void openQuickSettings() {
            openQuickSettingsCalled = true;
        }

        @Override
        public void onFindStateChanged() {
            onFindStateChangedCount++;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme);

        root = LayoutInflater.from(activity).inflate(R.layout.full_webview, null);
        activity.setContentView(root);

        drawerLayout = root.findViewById(R.id.browser_drawer_container);
        webApp = new WebApp("https://example.org", 0);
        activeWebView = new WebView(activity);
        readerWebView = new WebView(activity);

        callback = new TestCallback();
        callback.app = webApp;
        callback.activeWv = activeWebView;
        callback.readerWv = readerWebView;
        callback.drawer = drawerLayout;

        View findBarView = root.findViewById(R.id.browserFindBar);
        pageFindController = new PageFindController(findBarView, () -> {});

        controller = new BrowserPageToolsController(root, callback);
        controller.setPageFindController(pageFindController);
    }

    @After
    public void tearDown() {
        if (pageFindController != null) {
            pageFindController.close();
        }
        activeWebView.destroy();
        readerWebView.destroy();
    }

    @Test
    public void initViews_attachesClickListenersAndTriggersCallbacks() {
        View btnReader = root.findViewById(R.id.btnPageToolsReader);
        View btnFind = root.findViewById(R.id.btnPageToolsFind);
        View btnCopy = root.findViewById(R.id.btnPageToolsCopy);
        View btnClose = root.findViewById(R.id.btnPageToolsClose);

        assertNotNull(btnReader);
        assertNotNull(btnFind);
        assertNotNull(btnCopy);
        assertNotNull(btnClose);

        btnReader.performClick();
        assertTrue("Reader button must trigger openReaderMode()", callback.openReaderModeCalled);

        btnCopy.performClick();
        assertTrue("Copy button must trigger copyCurrentUrl()", callback.copyCurrentUrlCalled);

        btnClose.performClick();
        assertTrue("Close button must trigger closeDrawer()", callback.closeDrawerCalled);

        btnFind.performClick();
        assertTrue("Find button must open find bar", controller.isFindOpen());
        assertTrue("Find state changed must be notified", callback.onFindStateChangedCount > 0);
    }

    @Test
    public void updatePageToolsVisibility_framelessTrue_showsFramelessToolsAndHidesSettings() {
        webApp.setFrameless(true);
        controller.updatePageToolsVisibility();

        View framelessTools = root.findViewById(R.id.framelessPageTools);
        View quickSettingsScroll = root.findViewById(R.id.quickSettingsScroll);
        View quickSettingsNavigation = root.findViewById(R.id.quickSettingsNavigation);
        View quickSettingsDivider = root.findViewById(R.id.quickSettingsDivider);

        assertEquals(View.VISIBLE, framelessTools.getVisibility());
        assertEquals(View.GONE, quickSettingsScroll.getVisibility());
        assertEquals(View.GONE, quickSettingsNavigation.getVisibility());
        assertEquals(View.GONE, quickSettingsDivider.getVisibility());
    }

    @Test
    public void updatePageToolsVisibility_framelessFalse_hidesFramelessToolsAndShowsSettings() {
        webApp.setFrameless(false);
        controller.updatePageToolsVisibility();

        View framelessTools = root.findViewById(R.id.framelessPageTools);
        View quickSettingsScroll = root.findViewById(R.id.quickSettingsScroll);
        View quickSettingsNavigation = root.findViewById(R.id.quickSettingsNavigation);
        View quickSettingsDivider = root.findViewById(R.id.quickSettingsDivider);

        assertEquals(View.GONE, framelessTools.getVisibility());
        assertEquals(View.VISIBLE, quickSettingsScroll.getVisibility());
        assertEquals(View.VISIBLE, quickSettingsNavigation.getVisibility());
        assertEquals(View.VISIBLE, quickSettingsDivider.getVisibility());
    }

    @Test
    public void updatePageToolsVisibility_nullWebApp_treatedAsFramed() {
        callback.app = null;
        controller.updatePageToolsVisibility();

        View framelessTools = root.findViewById(R.id.framelessPageTools);
        View quickSettingsScroll = root.findViewById(R.id.quickSettingsScroll);

        assertEquals(View.GONE, framelessTools.getVisibility());
        assertEquals(View.VISIBLE, quickSettingsScroll.getVisibility());
    }

    @Test
    public void applyPageToolsInsets_appliesCutoutAndSystemBarsPadding() {
        View framelessTools = root.findViewById(R.id.framelessPageTools);
        assertNotNull(framelessTools);

        WindowInsetsCompat testInsets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 20, 0, 0))
                .build();

        controller.applyPageToolsInsets(testInsets);

        int basePadding = Math.round(16 * root.getResources().getDisplayMetrics().density);
        assertEquals(basePadding, framelessTools.getPaddingLeft());
        assertEquals(basePadding + 20, framelessTools.getPaddingTop());
        assertEquals(basePadding, framelessTools.getPaddingRight());
        assertEquals(basePadding, framelessTools.getPaddingBottom());
    }

    @Test
    public void openPageTools_whenFrameless_opensDrawerAndUpdatesVisibility() {
        webApp.setFrameless(true);
        controller.openPageTools();

        assertTrue("Drawer must be open or opening at END", drawerLayout.isDrawerOpen(GravityCompat.END) || drawerLayout.isDrawerVisible(GravityCompat.END));
        assertFalse("Quick settings must NOT be requested in frameless mode", callback.openQuickSettingsCalled);
    }

    @Test
    public void openPageTools_whenFramed_delegatesToQuickSettings() {
        webApp.setFrameless(false);
        controller.openPageTools();

        assertTrue("Quick settings must be delegated to in framed mode", callback.openQuickSettingsCalled);
        assertFalse("Drawer must NOT be opened directly for frameless tools", drawerLayout.isDrawerOpen(GravityCompat.END));
    }

    @Test
    public void openPageTools_whenMediaCustomViewShowing_doesNothing() {
        webApp.setFrameless(true);
        callback.mediaCustomViewShowing = true;

        controller.openPageTools();
        assertFalse("Drawer must not open when media custom view is showing", drawerLayout.isDrawerOpen(GravityCompat.END));
        assertFalse(callback.openQuickSettingsCalled);
    }

    @Test
    public void openFindInPage_whenReaderClosed_opensOnActiveWebView() {
        callback.readerOpen = false;
        controller.openFindInPage();

        assertTrue("Find bar must be open", controller.isFindOpen());
        assertTrue("Drawer must be closed when find bar opens", callback.closeDrawerCalled);
        assertTrue("Callback must be notified on find state changed", callback.onFindStateChangedCount > 0);
    }

    @Test
    public void openFindInPage_whenReaderOpen_targetsReaderWebView() {
        callback.readerOpen = true;
        controller.openFindInPage();

        assertTrue("Find bar must be open", controller.isFindOpen());
        assertTrue("Drawer must be closed", callback.closeDrawerCalled);
    }

    @Test
    public void openFindInPage_whenMediaCustomViewShowing_doesNothing() {
        callback.mediaCustomViewShowing = true;
        controller.openFindInPage();

        assertFalse("Find bar must NOT open when media is showing", controller.isFindOpen());
    }

    @Test
    public void closeFind_closesPageFindAndNotifiesCallback() {
        controller.openFindInPage();
        assertTrue(controller.isFindOpen());

        int countBefore = callback.onFindStateChangedCount;
        controller.closeFind();

        assertFalse("Find bar must be closed", controller.isFindOpen());
        assertEquals("Callback count must increment on close", countBefore + 1, callback.onFindStateChangedCount);
    }

    @Test
    public void handleBackPressed_whenFindOpen_closesFindAndReturnsTrue() {
        controller.openFindInPage();
        assertTrue(controller.isFindOpen());

        boolean handled = controller.handleBackPressed();
        assertTrue("handleBackPressed must return true when find was open", handled);
        assertFalse("Find bar must now be closed", controller.isFindOpen());
    }

    @Test
    public void handleBackPressed_whenFindClosed_returnsFalse() {
        assertFalse(controller.isFindOpen());
        boolean handled = controller.handleBackPressed();
        assertFalse("handleBackPressed must return false when find was closed", handled);
    }

    @Test
    public void getterSetterPageFindController() {
        assertSame(pageFindController, controller.getPageFindController());
        controller.setPageFindController(null);
        assertEquals(null, controller.getPageFindController());
    }
}
