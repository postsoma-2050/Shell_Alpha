package com.cylonid.nativealpha.browser;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Looper;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserPermissionsControllerTest {

    private Activity activity;
    private WebApp webApp;
    private WebView webView;
    private TestCallback callback;
    private BrowserPermissionsController controller;

    private static class TestCallback implements BrowserPermissionsController.Callback {
        WebApp app;
        WebView wv;
        boolean finishingOrDestroyed = false;

        BrowserPermissionsController.PendingMediaPermission pendingMedia;
        ValueCallback<Uri[]> fileCallback;
        int activeFileCode = -1;

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

        @Override
        public boolean isFinishingOrDestroyed() {
            return finishingOrDestroyed;
        }

        @Override
        public void onStateChanged(
                @Nullable BrowserPermissionsController.PendingMediaPermission pendingMedia,
                @Nullable ValueCallback<Uri[]> fileCallback,
                int activeFileCode) {
            this.pendingMedia = pendingMedia;
            this.fileCallback = fileCallback;
            this.activeFileCode = activeFileCode;
        }
    }

    private static class RecordingPermissionRequest extends PermissionRequest {
        final Uri origin;
        final String[] resources;
        int grants = 0;
        int denials = 0;
        String[] grantedResources;

        RecordingPermissionRequest(String origin, String... resources) {
            this.origin = Uri.parse(origin);
            this.resources = resources;
        }

        @Override public Uri getOrigin() { return origin; }
        @Override public String[] getResources() { return resources; }
        @Override public void grant(String[] resources) { grants++; this.grantedResources = resources; }
        @Override public void deny() { denials++; }
    }

    private static class RecordingGeoCallback implements GeolocationPermissions.Callback {
        int calls = 0;
        String origin;
        boolean allow;
        boolean retain;

        @Override
        public void invoke(String origin, boolean allow, boolean retain) {
            calls++;
            this.origin = origin;
            this.allow = allow;
            this.retain = retain;
        }
    }

    private static class RecordingFileCallback implements ValueCallback<Uri[]> {
        int calls = 0;
        Uri[] result;

        @Override
        public void onReceiveValue(Uri[] value) {
            calls++;
            this.result = value;
        }
    }

    private static class RecordingChooserParams extends WebChromeClient.FileChooserParams {
        boolean fail = false;
        boolean multiple = false;

        @Override public int getMode() { return multiple ? MODE_OPEN_MULTIPLE : MODE_OPEN; }
        @Override public String[] getAcceptTypes() { return new String[]{"*/*"}; }
        @Override public boolean isCaptureEnabled() { return false; }
        @Override public CharSequence getTitle() { return "Select File"; }
        @Override public String getFilenameHint() { return null; }
        @Override public Intent createIntent() {
            if (fail) throw new IllegalStateException("Intent creation failed");
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*");
            if (multiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            return intent;
        }
    }

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(new android.widget.FrameLayout(activity));
        webApp = new WebApp("https://example.org", 1);
        webView = new WebView(activity);
        callback = new TestCallback(webApp, webView);
        controller = new BrowserPermissionsController(activity, callback);
    }

    @After
    public void tearDown() {
        controller.cancelMediaPermission(false);
        controller.handleGeoPermissionCallback(false);
        controller.completeFileChooser(null);
        webView.destroy();
    }

    @Test
    public void allocateBrowserRequestCodeGeneratesUniqueValidCodes() {
        int code1 = controller.allocateBrowserRequestCode();
        int code2 = controller.allocateBrowserRequestCode();
        assertTrue(code1 >= 0x4000 && code1 <= 0x7fff);
        assertTrue(code2 >= 0x4000 && code2 <= 0x7fff);
        assertTrue(code1 != code2);
    }

    @Test
    public void isResourceAllowedRespectsWebAppSettingsAndOrigin() {
        webApp.setCameraPermission(true);
        webApp.setMicrophonePermission(false);
        webApp.setDrmAllowed(true);
        webApp.setAllowLocationAccess(false);

        // Same origin matches
        assertTrue(controller.isResourceAllowed("https://example.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE));
        assertFalse(controller.isResourceAllowed("https://example.org", PermissionRequest.RESOURCE_AUDIO_CAPTURE));
        assertTrue(controller.isResourceAllowed("https://example.org", PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID));
        assertFalse(controller.isResourceAllowed("https://example.org", "location"));

        // Different origin is blocked even if webapp setting is enabled
        assertFalse(controller.isResourceAllowed("https://evil.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE));
        assertFalse(controller.isResourceAllowed("https://evil.org", PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID));
    }

    @Test
    public void onPermissionRequestDeniesWhenOwnerMismatchOrFinishing() {
        WebView anotherWebView = new WebView(activity);
        RecordingPermissionRequest request1 = new RecordingPermissionRequest("https://example.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        controller.onPermissionRequest(request1, anotherWebView);
        assertEquals(1, request1.denials);
        anotherWebView.destroy();

        callback.finishingOrDestroyed = true;
        RecordingPermissionRequest request2 = new RecordingPermissionRequest("https://example.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        controller.onPermissionRequest(request2, webView);
        assertEquals(1, request2.denials);
    }

    @Test
    public void onPermissionRequestDeniesUnsupportedResources() {
        RecordingPermissionRequest request = new RecordingPermissionRequest("https://example.org", "unsupported.resource.spec");
        controller.onPermissionRequest(request, webView);
        assertEquals(1, request.denials);
        assertEquals(0, request.grants);
    }

    @Test
    public void onPermissionRequestShowsDialogWhenNotPreApproved() {
        webApp.setCameraPermission(false);
        RecordingPermissionRequest request = new RecordingPermissionRequest("https://example.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        controller.onPermissionRequest(request, webView);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        // Cancel dialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, request.denials);
        assertEquals(0, request.grants);
        assertNull(callback.pendingMedia);
    }

    @Test
    public void onPermissionRequestApprovedDialogFlowRequestsAndroidRuntimePermission() {
        webApp.setCameraPermission(false);
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA);

        RecordingPermissionRequest request = new RecordingPermissionRequest("https://example.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        controller.onPermissionRequest(request, webView);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);

        // Click OK on permission dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(callback.pendingMedia);
        int requestCode = callback.pendingMedia.runtimeRequestCode;
        assertTrue(requestCode >= 0x4000);

        // Simulate user granting runtime permission
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA);
        boolean handled = controller.handleRequestPermissionsResult(
                requestCode,
                new String[]{Manifest.permission.CAMERA},
                new int[]{PackageManager.PERMISSION_GRANTED});

        assertTrue(handled);
        assertEquals(1, request.grants);
        assertArrayEquals(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}, request.grantedResources);
        assertNull(callback.pendingMedia);
    }

    @Test
    public void onPermissionRequestPreApprovedWithAndroidPermissionGrantsImmediately() {
        webApp.setCameraPermission(true);
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA);

        RecordingPermissionRequest request = new RecordingPermissionRequest("https://example.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        controller.onPermissionRequest(request, webView);

        assertEquals(1, request.grants);
        assertArrayEquals(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}, request.grantedResources);
        assertEquals(0, request.denials);
        assertNull(callback.pendingMedia);
    }

    @Test
    public void onPermissionRequestCanceledCancelsPendingRequest() {
        webApp.setMicrophonePermission(false);
        RecordingPermissionRequest request = new RecordingPermissionRequest("https://example.org", PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        controller.onPermissionRequest(request, webView);

        assertNotNull(callback.pendingMedia);

        // Canceled by webview
        controller.onPermissionRequestCanceled(request);

        assertNull(callback.pendingMedia);
        assertEquals(0, request.grants);
        assertEquals(0, request.denials);
    }

    @Test
    public void onGeolocationPermissionsShowPromptRejectsNonMatchingOwner() {
        WebView otherWv = new WebView(activity);
        RecordingGeoCallback geoCallback = new RecordingGeoCallback();

        controller.onGeolocationPermissionsShowPrompt("https://example.org", geoCallback, otherWv);

        assertEquals(1, geoCallback.calls);
        assertFalse(geoCallback.allow);
        otherWv.destroy();
    }

    @Test
    public void onGeolocationPermissionsShowPromptAndDialogDenied() {
        webApp.setAllowLocationAccess(false);
        RecordingGeoCallback geoCallback = new RecordingGeoCallback();

        controller.onGeolocationPermissionsShowPrompt("https://example.org", geoCallback, webView);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, geoCallback.calls);
        assertFalse(geoCallback.allow);
    }

    @Test
    public void onGeolocationPermissionsShowPromptApprovedWithAndroidPermission() {
        webApp.setAllowLocationAccess(true);
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        RecordingGeoCallback geoCallback = new RecordingGeoCallback();

        controller.onGeolocationPermissionsShowPrompt("https://example.org", geoCallback, webView);

        assertEquals(1, geoCallback.calls);
        assertTrue(geoCallback.allow);
    }

    @Test
    public void onGeolocationPermissionsHidePromptDismissesDialog() {
        webApp.setAllowLocationAccess(false);
        RecordingGeoCallback geoCallback = new RecordingGeoCallback();

        controller.onGeolocationPermissionsShowPrompt("https://example.org", geoCallback, webView);
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        controller.onGeolocationPermissionsHidePrompt(webView);
        assertFalse(dialog.isShowing());
        assertEquals(1, geoCallback.calls);
        assertFalse(geoCallback.allow);
    }

    @Test
    public void fileChooserHandlesExceptionGracefully() {
        RecordingFileCallback fileCallback = new RecordingFileCallback();
        RecordingChooserParams params = new RecordingChooserParams();
        params.fail = true;

        boolean handled = controller.onShowFileChooser(webView, fileCallback, params);
        assertTrue(handled);
        assertEquals(1, fileCallback.calls);
        assertNull(fileCallback.result);
        assertNull(callback.fileCallback);
    }

    @Test
    public void fileChooserSingleFileSuccess() {
        RecordingFileCallback fileCallback = new RecordingFileCallback();
        RecordingChooserParams params = new RecordingChooserParams();

        boolean handled = controller.onShowFileChooser(webView, fileCallback, params);
        assertTrue(handled);
        assertNotNull(callback.fileCallback);
        int requestCode = callback.activeFileCode;

        Intent resultData = new Intent();
        resultData.setData(Uri.parse("content://media/external/images/media/42"));

        boolean resultHandled = controller.handleActivityResult(requestCode, Activity.RESULT_OK, resultData);
        assertTrue(resultHandled);
        assertEquals(1, fileCallback.calls);
        assertNotNull(fileCallback.result);
        assertEquals(1, fileCallback.result.length);
        assertEquals(Uri.parse("content://media/external/images/media/42"), fileCallback.result[0]);
        assertNull(callback.fileCallback);
    }

    @Test
    public void fileChooserMultipleFilesSuccess() {
        RecordingFileCallback fileCallback = new RecordingFileCallback();
        RecordingChooserParams params = new RecordingChooserParams();
        params.multiple = true;

        controller.onShowFileChooser(webView, fileCallback, params);
        int requestCode = callback.activeFileCode;

        Intent resultData = new Intent();
        ClipData clipData = ClipData.newUri(
                activity.getContentResolver(),
                "file1",
                Uri.parse("content://media/external/images/media/1"));
        clipData.addItem(new ClipData.Item(Uri.parse("content://media/external/images/media/2")));
        resultData.setClipData(clipData);

        boolean resultHandled = controller.handleActivityResult(requestCode, Activity.RESULT_OK, resultData);
        assertTrue(resultHandled);
        assertEquals(1, fileCallback.calls);
        assertNotNull(fileCallback.result);
        assertEquals(2, fileCallback.result.length);
        assertEquals(Uri.parse("content://media/external/images/media/1"), fileCallback.result[0]);
        assertEquals(Uri.parse("content://media/external/images/media/2"), fileCallback.result[1]);
    }

    @Test
    public void fileChooserCancellationDeliversNull() {
        RecordingFileCallback fileCallback = new RecordingFileCallback();
        RecordingChooserParams params = new RecordingChooserParams();

        controller.onShowFileChooser(webView, fileCallback, params);
        int requestCode = callback.activeFileCode;

        boolean resultHandled = controller.handleActivityResult(requestCode, Activity.RESULT_CANCELED, null);
        assertTrue(resultHandled);
        assertEquals(1, fileCallback.calls);
        assertNull(fileCallback.result);
        assertNull(callback.fileCallback);
    }
}
