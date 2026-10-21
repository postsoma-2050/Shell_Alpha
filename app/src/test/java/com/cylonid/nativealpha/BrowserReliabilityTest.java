package com.cylonid.nativealpha;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.model.DataManager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.KeyEvent;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AlertDialog;

import com.cylonid.nativealpha.model.WebApp;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import android.content.Context;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserReliabilityTest {
    private WebViewActivity activity;
    private WebApp app;
    private RecordingWebView webView;
    private WebChromeClient chrome;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);
        app = new WebApp("https://example.org", 0);
        webView = new RecordingWebView(activity);
        ReflectionHelpers.setField(activity, "webapp", app);
        ReflectionHelpers.setField(activity, "wv", webView);
        ReflectionHelpers.setField(activity, "rootWebView", webView);
        ReflectionHelpers.setField(activity, "progressBar", activity.findViewById(R.id.progressBar));
        ReflectionHelpers.setField(activity, "drawerLayout", activity.findViewById(R.id.browser_drawer_container));
        ReflectionHelpers.setField(activity, "browserToolbarContainer",
                activity.findViewById(R.id.browser_toolbar_container));
        chrome = (WebChromeClient) constructInner("CustomWebChromeClient", webView);
    }

    @After public void tearDown() {
        ReflectionHelpers.callInstanceMethod(activity, "cancelMediaPermission", from(boolean.class, false));
        ReflectionHelpers.callInstanceMethod(activity, "handleGeoPermissionCallback", from(boolean.class, false));
        ReflectionHelpers.callInstanceMethod(activity, "completeFileChooser", from(Uri[].class, null));
        webView.destroy();
        ((WebView) activity.findViewById(R.id.webview)).destroy();
        ((WebView) activity.findViewById(R.id.adblockwebview)).destroy();
    }

    private Object constructInner(String name, Object... arguments) throws Exception {
        Class<?> type = Class.forName(WebViewActivity.class.getName() + "$" + name);
        Constructor<?> targetConstructor = null;
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterTypes().length == arguments.length + 1
                    && c.getParameterTypes()[0].isAssignableFrom(activity.getClass())) {
                targetConstructor = c;
                break;
            }
        }
        Constructor<?> constructor = targetConstructor != null ? targetConstructor : type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] values = new Object[arguments.length + 1];
        values[0] = activity;
        System.arraycopy(arguments, 0, values, 1, arguments.length);
        return constructor.newInstance(values);
    }

    @Test public void settingsReloadWhileLoadingReallyReloads() {
        ReflectionHelpers.setField(activity, "pageLoading", true);
        ReflectionHelpers.callInstanceMethod(activity, "forceReloadCurrentPage");
        assertEquals(1, webView.stops);
        assertEquals(1, webView.reloads);
        assertTrue(ReflectionHelpers.getField(activity, "pageLoading"));
    }

    @Test public void refreshButtonWhileLoadingOnlyStops() {
        ReflectionHelpers.setField(activity, "pageLoading", true);
        ReflectionHelpers.callInstanceMethod(activity, "reloadCurrentPage");
        assertEquals(1, webView.stops);
        assertEquals(0, webView.reloads);
        assertFalse(ReflectionHelpers.getField(activity, "pageLoading"));
    }

    @Test public void drmConsentCompletesOriginalRequestOnceWithoutReload() {
        RecordingPermissionRequest request = permission("https://other.example", PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
        chrome.onPermissionRequest(request);
        assertEquals(0, request.grants);
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, request.grants);
        assertArrayEquals(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}, request.granted);
        chrome.onPermissionRequestCanceled(request);
        assertEquals(0, request.denials);
        assertEquals(0, webView.reloads);
    }

    @Test public void baseSitePermissionDoesNotAuthorizeAnotherOrigin() {
        app.setDrmAllowed(true);
        RecordingPermissionRequest request = permission("https://evil.example", PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
        chrome.onPermissionRequest(request);
        assertEquals(0, request.grants);
        ShadowDialog.getLatestDialog().cancel();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, request.denials);
    }

    @Test public void cameraWaitsForAndroidResultAndCompletesOnlyOnce() {
        app.setCameraPermission(true);
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA);
        RecordingPermissionRequest request = permission("https://example.org", PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        chrome.onPermissionRequest(request);
        assertEquals(0, request.grants);
        Object pending = ReflectionHelpers.getField(activity, "pendingMediaPermission");
        int code = ReflectionHelpers.getField(pending, "runtimeRequestCode");
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA);
        activity.onRequestPermissionsResult(code, new String[]{Manifest.permission.CAMERA},
                new int[]{PackageManager.PERMISSION_GRANTED});
        activity.onRequestPermissionsResult(code, new String[]{Manifest.permission.CAMERA},
                new int[]{PackageManager.PERMISSION_GRANTED});
        assertEquals(1, request.grants);
        assertArrayEquals(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}, request.granted);
        assertEquals(0, webView.reloads);
    }

    @Test public void canceledWebRequestNeverReceivesLateCompletion() {
        RecordingPermissionRequest request = permission("https://example.org", PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        chrome.onPermissionRequest(request);
        chrome.onPermissionRequestCanceled(request);
        assertNull(ReflectionHelpers.getField(activity, "pendingMediaPermission"));
        assertEquals(0, request.grants);
        assertEquals(0, request.denials);
    }

    @Test public void unsupportedResourcesAreDenied() {
        RecordingPermissionRequest request = permission("https://example.org", "future.webkit.permission");
        chrome.onPermissionRequest(request);
        assertEquals(1, request.denials);
        assertEquals(0, request.grants);
    }

    @Test public void chooserFailureClearsAndCompletesCallback() {
        RecordingFileCallback callback = new RecordingFileCallback();
        RecordingChooserParams params = new RecordingChooserParams();
        params.fail = true;
        assertTrue(chrome.onShowFileChooser(webView, callback, params));
        assertEquals(1, callback.calls);
        assertNull(callback.result);
        assertNull(activity.filePathCallback);
    }

    @Test public void chooserReentryIgnoresOldResultAndCancellationIsIdempotent() {
        RecordingFileCallback first = new RecordingFileCallback();
        RecordingFileCallback second = new RecordingFileCallback();
        RecordingChooserParams params = new RecordingChooserParams();
        chrome.onShowFileChooser(webView, first, params);
        int oldCode = ReflectionHelpers.getField(activity, "activeFileRequestCode");
        chrome.onShowFileChooser(webView, second, params);
        int newCode = ReflectionHelpers.getField(activity, "activeFileRequestCode");
        assertEquals(1, first.calls);
        assertNull(first.result);
        assertNotEquals(oldCode, newCode);
        activity.onActivityResult(oldCode, Activity.RESULT_CANCELED, null);
        assertEquals(0, second.calls);
        activity.onActivityResult(newCode, Activity.RESULT_CANCELED, null);
        activity.onActivityResult(newCode, Activity.RESULT_CANCELED, null);
        assertEquals(1, second.calls);
        assertNull(second.result);
        assertNull(activity.filePathCallback);
    }

    @Test public void mediaBackRestoresChromeBeforeNavigatingPage() {
        app.setFrameless(true);
        View media = new View(activity);
        AtomicInteger hidden = new AtomicInteger();
        WebChromeClient.CustomViewCallback callback = hidden::incrementAndGet;
        chrome.onShowCustomView(media, callback);
        assertEquals(View.GONE, activity.findViewById(R.id.btnBrowserRestoreControls).getVisibility());
        activity.onBackPressed();
        assertNull(ReflectionHelpers.getField(activity, "mediaCustomView"));
        assertEquals(View.GONE, activity.findViewById(R.id.btnBrowserRestoreControls).getVisibility());
        assertEquals(1, hidden.get());
        assertEquals(0, webView.backs);
        chrome.onHideCustomView();
        assertEquals(1, hidden.get());
    }

    @Test public void crossSiteLinkFallsBackInternallyWhenExternalAppsDisabled() throws Exception {
        app.setOpenUrlExternal(true);
        app.setAllowAppLaunch(false);
        WebResourceRequest request = new WebResourceRequest() {
            @Override public Uri getUrl() { return Uri.parse("https://other.example/login"); }
            @Override public boolean isForMainFrame() { return true; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return true; }
            @Override public String getMethod() { return "GET"; }
            @Override public Map<String, String> getRequestHeaders() { return Collections.emptyMap(); }
        };
        WebViewClient browser = (WebViewClient) constructInner("CustomBrowser");
        assertFalse(browser.shouldOverrideUrlLoading(webView, request));
        assertEquals(0, webView.loads);
    }

    @Test public void blobDownloadIsReportedWithoutInventingHttpDownload() {
        ReflectionHelpers.callInstanceMethod(activity, "handleDownload",
                from(String.class, "blob:https://example.org/id"), from(String.class, "agent"),
                from(String.class, ""), from(String.class, "application/pdf"), from(long.class, 10L));
        assertNull(ReflectionHelpers.getField(activity, "dl_request"));
        assertNotNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test public void popupTransportAndCloseRetainParentWindow() {
        WebView.WebViewTransport transport = webView.new WebViewTransport();
        Message message = Message.obtain(new Handler(Looper.getMainLooper()));
        message.obj = transport;
        assertTrue(chrome.onCreateWindow(webView, false, true, message));
        WebView child = activity.getWebView();
        assertNotSame(webView, child);
        assertSame(child, transport.getWebView());
        chrome.onCloseWindow(child);
        assertSame(webView, activity.getWebView());
        assertEquals(View.VISIBLE, webView.getVisibility());
    }

    @Test public void unsolicitedPopupHasExplicitFailureAndKeepsParent() {
        assertFalse(chrome.onCreateWindow(webView, false, false, null));
        assertSame(webView, activity.getWebView());
        assertNotNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    private RecordingPermissionRequest permission(String origin, String... resources) {
        return new RecordingPermissionRequest(origin, resources);
    }

    private static final class RecordingPermissionRequest extends PermissionRequest {
        final Uri origin;
        final String[] resources;
        int grants;
        int denials;
        String[] granted;
        RecordingPermissionRequest(String origin, String[] resources) {
            this.origin = Uri.parse(origin);
            this.resources = resources;
        }
        @Override public Uri getOrigin() { return origin; }
        @Override public String[] getResources() { return resources; }
        @Override public void grant(String[] resources) { grants++; granted = resources; }
        @Override public void deny() { denials++; }
    }

    private static final class RecordingFileCallback implements ValueCallback<Uri[]> {
        int calls;
        Uri[] result;
        @Override public void onReceiveValue(Uri[] value) { calls++; result = value; }
    }

    private static final class RecordingChooserParams extends WebChromeClient.FileChooserParams {
        boolean fail;
        @Override public int getMode() { return MODE_OPEN; }
        @Override public String[] getAcceptTypes() { return new String[]{"*/*"}; }
        @Override public boolean isCaptureEnabled() { return false; }
        @Override public CharSequence getTitle() { return null; }
        @Override public String getFilenameHint() { return null; }
        @Override public Intent createIntent() {
            if (fail) throw new IllegalStateException("No picker");
            return new Intent(Intent.ACTION_GET_CONTENT).setType("*/*");
        }
    }

    private static final class RecordingWebView extends WebView {
        int stops;
        int reloads;
        int backs;
        int loads;
        boolean canGoBackValue = false;
        RecordingWebView(Context context) { super(context); }
        @Override public boolean canGoBack() { return canGoBackValue; }
        @Override public void stopLoading() { stops++; }
        @Override public void reload() { reloads++; }
        @Override public void goBack() { backs++; }
        @Override public void loadUrl(String url, Map<String, String> headers) { loads++; }
    }

    @Test
    public void containerClassVerificationDoesNotFinishWhenMatching() {
        DataManager.getInstance().loadAppData();
        WebApp sandboxedApp = new WebApp("https://example.org", 101);
        sandboxedApp.setContainerId(0);
        sandboxedApp.setUseContainer(true);
        DataManager.getInstance().addWebsite(sandboxedApp);

        Intent intent = new Intent(RuntimeEnvironment.getApplication(), __WebViewActivity_0.class);
        intent.putExtra(Const.INTENT_WEBAPPID, 101);
        try (org.robolectric.android.controller.ActivityController<__WebViewActivity_0> controller =
                     Robolectric.buildActivity(__WebViewActivity_0.class, intent).create()) {
            __WebViewActivity_0 act = controller.get();
            assertFalse(act.isFinishing());
        }
    }

    @Test
    public void mainWebViewActivityDoesNotFinishForUnsandboxedApp() {
        DataManager.getInstance().loadAppData();
        WebApp unsandboxedApp = new WebApp("https://example.org", 102);
        unsandboxedApp.setContainerId(Const.NO_CONTAINER);
        unsandboxedApp.setUseContainer(false);
        DataManager.getInstance().addWebsite(unsandboxedApp);

        Intent intent = new Intent(RuntimeEnvironment.getApplication(), WebViewActivity.class);
        intent.putExtra(Const.INTENT_WEBAPPID, 102);
        try (org.robolectric.android.controller.ActivityController<WebViewActivity> controller =
                     Robolectric.buildActivity(WebViewActivity.class, intent).create()) {
            WebViewActivity act = controller.get();
            assertFalse(act.isFinishing());
        }
    }

    @Test
    public void viewportAndOverviewModeAlwaysEnabledInBothMobileAndDesktop() {
        app.setRequestDesktop(false);
        ReflectionHelpers.callInstanceMethod(activity, "applyDesktopMode", from(WebView.class, webView));
        assertTrue(webView.getSettings().getUseWideViewPort());
        assertTrue(webView.getSettings().getLoadWithOverviewMode());

        app.setRequestDesktop(true);
        ReflectionHelpers.callInstanceMethod(activity, "applyDesktopMode", from(WebView.class, webView));
        assertTrue(webView.getSettings().getUseWideViewPort());
        assertTrue(webView.getSettings().getLoadWithOverviewMode());
    }

    private Object createDummyResultReceiver() throws Exception {
        Class<?> receiverClass = Class.forName("android.webkit.JsResult$ResultReceiver");
        return java.lang.reflect.Proxy.newProxyInstance(
                receiverClass.getClassLoader(),
                new Class<?>[]{receiverClass},
                (proxy, method, args) -> null);
    }

    private android.webkit.JsResult createJsResult() throws Exception {
        Class<?> receiverClass = Class.forName("android.webkit.JsResult$ResultReceiver");
        return ReflectionHelpers.callConstructor(
                android.webkit.JsResult.class,
                ReflectionHelpers.ClassParameter.from(receiverClass, createDummyResultReceiver()));
    }

    private android.webkit.JsPromptResult createJsPromptResult() throws Exception {
        Class<?> receiverClass = Class.forName("android.webkit.JsResult$ResultReceiver");
        return ReflectionHelpers.callConstructor(
                android.webkit.JsPromptResult.class,
                ReflectionHelpers.ClassParameter.from(receiverClass, createDummyResultReceiver()));
    }

    @Test
    public void jsAlertDisplaysDialogAndConfirmInvoked() throws Exception {
        android.webkit.JsResult result = createJsResult();
        boolean handled = chrome.onJsAlert(webView, "https://example.org", "Alert Message", result);
        assertTrue(handled);
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertTrue(confirmed);
    }

    @Test
    public void jsConfirmDisplaysDialogAndCancelInvoked() throws Exception {
        android.webkit.JsResult result = createJsResult();
        boolean handled = chrome.onJsConfirm(webView, "https://example.org", "Confirm Message", result);
        assertTrue(handled);
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertFalse(confirmed);
    }

    @Test
    public void jsPromptDisplaysDialogAndSubmitsInput() throws Exception {
        android.webkit.JsPromptResult result = createJsPromptResult();
        boolean handled = chrome.onJsPrompt(webView, "https://example.org", "Prompt Message", "initial", result);
        assertTrue(handled);
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        String stringResult = ReflectionHelpers.callInstanceMethod(result, "getStringResult");
        assertEquals("initial", stringResult);
    }

    @Test
    public void jsBeforeUnloadDisplaysDialogAndConfirmInvoked() throws Exception {
        android.webkit.JsResult result = createJsResult();
        boolean handled = chrome.onJsBeforeUnload(webView, "https://example.org", "Leave page?", result);
        assertTrue(handled);
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertTrue(confirmed);
    }

    @Test public void framelessBackNavigatesHistoryOrLaunchesMainActivity() {
        app.setFrameless(true);
        DrawerLayout drawer = activity.findViewById(R.id.browser_drawer_container);

        // When webView has history, Back navigates history without finishing or launching MainActivity
        webView.canGoBackValue = true;
        activity.onBackPressed();
        assertEquals(1, webView.backs);
        assertFalse(activity.isFinishing());
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertFalse(drawer.isDrawerOpen(GravityCompat.END));

        // When webView has no history, Back in frameless mode finishes and launches MainActivity
        webView.canGoBackValue = false;
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertFalse(drawer.isDrawerOpen(GravityCompat.END));
    }
}
