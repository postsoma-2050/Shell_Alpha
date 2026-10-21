package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Intent;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.SandboxActivity;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.WebViewLauncher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserSandboxDialogHelperTest {

    private Activity activity;
    private WebApp webApp;
    private TestCallback callback;
    private BrowserSandboxDialogHelper helper;

    private static class TestCallback implements BrowserSandboxDialogHelper.Callback {
        WebApp app;
        final List<String> loadedUrls = new ArrayList<>();
        WebApp switchedApp = null;
        boolean drawerClosed = false;

        TestCallback(WebApp app) {
            this.app = app;
        }

        @Nullable
        @Override
        public WebApp getWebApp() {
            return app;
        }

        @Override
        public void loadUrl(@NonNull String url) {
            loadedUrls.add(url);
        }

        @Override
        public void onContainerSwitched(@NonNull WebApp updatedApp) {
            this.switchedApp = updatedApp;
        }

        @Override
        public void closeDrawer() {
            this.drawerClosed = true;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
        activity.setTheme(R.style.AppTheme);

        DataManager dataManager = DataManager.getInstance();
        dataManager.loadAppData();
        dataManager.getWebsites().clear();

        webApp = new WebApp("https://example.com/start", 100);
        webApp.setContainerId(Const.NO_CONTAINER);
        webApp.setUseContainer(false);
        dataManager.addWebsite(webApp);

        callback = new TestCallback(webApp);
        helper = new BrowserSandboxDialogHelper(activity, callback);
    }

    @Test
    public void showSandboxPickerDialogDisplaysDialogWithAllContainers() {
        helper.showSandboxPickerDialog();

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("Sandbox dialog should be shown", dialog);

        ListView listView = dialog.getListView();
        assertNotNull("Dialog list view must exist", listView);

        int expectedCount = 1 + SandboxManager.getSandboxCount();
        assertEquals("Dialog should contain default plus all sandbox containers", expectedCount, listView.getAdapter().getCount());
    }

    @Test
    public void selectContainerSwitchesAppAndNotifiesCallback() {
        helper.showSandboxPickerDialog();

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);

        ListView listView = dialog.getListView();
        // Item index 1 corresponds to container 0
        listView.performItemClick(null, 1, listView.getAdapter().getItemId(1));

        assertTrue("Drawer should be closed on selection", callback.drawerClosed);
        assertNotNull("Container switch callback should be triggered", callback.switchedApp);
        assertEquals("WebApp container ID should be updated to container 0", 0, callback.switchedApp.getContainerId());
        assertTrue("WebApp useContainer flag should be true", callback.switchedApp.isUseContainer());
    }

    @Test
    public void openLinkInSandboxSameContainerLoadsUrlDirectly() {
        webApp.setContainerId(1);
        helper.openLinkInSandbox("https://example.com/target", 1);

        assertEquals(1, callback.loadedUrls.size());
        assertEquals("https://example.com/target", callback.loadedUrls.get(0));
    }

    @Test
    public void openLinkInSandboxDifferentContainerStartsNewIntent() {
        webApp.setContainerId(Const.NO_CONTAINER);
        helper.openLinkInSandbox("https://example.com/sandbox-link", 2);

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        Intent intent = shadowActivity.getNextStartedActivity();
        assertNotNull("Should start new Lite App intent for target sandbox", intent);
        assertEquals("https://example.com/sandbox-link", intent.getStringExtra(WebViewLauncher.EXTRA_TARGET_URL));
    }

    @Test
    public void manageSandboxNamesOpensSandboxActivity() {
        helper.showSandboxPickerDialog();

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        Intent intent = shadowActivity.getNextStartedActivity();
        assertNotNull("Manage sandboxes button should launch SandboxActivity", intent);
        assertEquals(SandboxActivity.class.getName(), intent.getComponent().getClassName());
    }
}
