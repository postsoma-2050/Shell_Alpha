package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class BrowserDownloadControllerTest {

    private Activity activity;
    private BrowserDownloadController controller;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        controller = new BrowserDownloadController(activity);
    }

    @Test
    public void unsupportedUrlOriginShowsUnsupportedToast() {
        controller.handleDownload("javascript:alert(1)", "Mozilla/5.0", null, "text/html", 100, null);
        assertEquals(activity.getString(R.string.browser_download_unsupported), ShadowToast.getTextOfLatestToast());
        assertNull(controller.getPendingRequest());
    }

    @Test
    public void pdfDownloadWithAppLaunchDispatchesViewIntent() {
        WebApp webApp = new WebApp("https://example.com", 1);
        webApp.setAllowAppLaunch(true);

        controller.handleDownload("https://example.com/document.pdf", "Mozilla/5.0",
                null, "application/pdf", 1024, webApp);

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        Intent intent = shadowActivity.getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(Uri.parse("https://example.com/document.pdf"), intent.getData());
        assertEquals("application/pdf", intent.getType());
    }

    @Test
    public void permissionsCallbacksManagePendingRequest() {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse("https://example.com/file.zip"));
        controller.setPendingRequest(request);

        // Denying permission clears pending request and shows toast
        controller.onPermissionsDenied(Const.PERMISSION_RC_STORAGE, Collections.singletonList("android.permission.WRITE_EXTERNAL_STORAGE"));
        assertNull(controller.getPendingRequest());
        assertEquals(activity.getString(R.string.browser_download_failed), ShadowToast.getTextOfLatestToast());
    }
}
