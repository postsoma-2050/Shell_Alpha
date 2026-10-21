package com.cylonid.nativealpha.browser;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller responsible for managing WebChromeClient permission requests
 * (Camera, Microphone, DRM protected media ID), Geolocation permissions,
 * file chooser operations, and runtime Android permissions.
 * Aligned with Hermit Lite Apps permissions architecture.
 */
public class BrowserPermissionsController {

    public static final class PendingMediaPermission {
        public final PermissionRequest request;
        public final String origin;
        public final List<String> resources;
        public int runtimeRequestCode = -1;

        public PendingMediaPermission(PermissionRequest request, String origin, List<String> resources) {
            this.request = request;
            this.origin = origin;
            this.resources = resources;
        }
    }

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        @Nullable
        WebView getActiveWebView();

        boolean isFinishingOrDestroyed();

        void onStateChanged(
                @Nullable PendingMediaPermission pendingMedia,
                @Nullable ValueCallback<Uri[]> fileCallback,
                int activeFileCode);
    }

    private final Activity activity;
    private final Callback callback;

    private final Map<String, Set<String>> originPermissions = new HashMap<>();
    private PendingMediaPermission pendingMediaPermission = null;
    private AlertDialog mediaPermissionDialog = null;

    private GeolocationPermissions.Callback mGeoPermissionRequestCallback = null;
    private String mGeoPermissionRequestOrigin = null;
    private AlertDialog locationPermissionDialog = null;
    private int activeLocationRequestCode = -1;

    private ValueCallback<Uri[]> filePathCallback = null;
    private int activeFileRequestCode = -1;
    private long fileChooserGeneration = 0;
    private int nextBrowserRequestCode = 0x4000;

    public BrowserPermissionsController(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    private void syncState() {
        callback.onStateChanged(pendingMediaPermission, filePathCallback, activeFileRequestCode);
    }

    public int allocateBrowserRequestCode() {
        int code;
        do {
            code = nextBrowserRequestCode;
            nextBrowserRequestCode = code == 0x7fff ? 0x4000 : code + 1;
        } while (code == activeFileRequestCode || code == activeLocationRequestCode
                || (pendingMediaPermission != null && code == pendingMediaPermission.runtimeRequestCode));
        return code;
    }

    public void cancelMediaPermission(boolean canceledByWebView) {
        PendingMediaPermission pending = pendingMediaPermission;
        pendingMediaPermission = null;
        if (mediaPermissionDialog != null) {
            mediaPermissionDialog.dismiss();
            mediaPermissionDialog = null;
        }
        syncState();
        if (pending != null && !canceledByWebView) {
            pending.request.deny();
        }
    }

    public boolean isResourceAllowed(String origin, String resource) {
        Set<String> granted = originPermissions.get(origin);
        if (granted != null && granted.contains(resource)) return true;
        WebApp webapp = callback.getWebApp();
        if (webapp == null || !BrowserOriginPolicy.sameOrigin(origin, webapp.getBaseUrl())) return false;
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) return webapp.isCameraPermission();
        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) return webapp.isMicrophonePermission();
        if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) return webapp.isDrmAllowed();
        return "location".equals(resource) && webapp.isAllowLocationAccess();
    }

    @Nullable
    private String runtimePermission(String resource) {
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) return Manifest.permission.CAMERA;
        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) return Manifest.permission.RECORD_AUDIO;
        return null;
    }

    private void requestMediaRuntimePermissions(PendingMediaPermission pending) {
        if (pendingMediaPermission != pending) return;
        List<String> missing = new ArrayList<>();
        for (String resource : pending.resources) {
            String androidPermission = runtimePermission(resource);
            if (androidPermission != null && ContextCompat.checkSelfPermission(activity, androidPermission)
                    != PackageManager.PERMISSION_GRANTED) missing.add(androidPermission);
        }
        if (missing.isEmpty()) {
            completeMediaPermission(pending);
        } else {
            pending.runtimeRequestCode = allocateBrowserRequestCode();
            syncState();
            ActivityCompat.requestPermissions(activity, missing.toArray(new String[0]), pending.runtimeRequestCode);
        }
    }

    private void completeMediaPermission(PendingMediaPermission pending) {
        if (pendingMediaPermission != pending) return;
        pendingMediaPermission = null;
        syncState();
        List<String> granted = new ArrayList<>();
        for (String resource : pending.resources) {
            String androidPermission = runtimePermission(resource);
            if (androidPermission == null || ContextCompat.checkSelfPermission(activity, androidPermission)
                    == PackageManager.PERMISSION_GRANTED) granted.add(resource);
        }
        if (granted.isEmpty()) {
            pending.request.deny();
        } else {
            originPermissions.computeIfAbsent(pending.origin, key -> new HashSet<>()).addAll(granted);
            pending.request.grant(granted.toArray(new String[0]));
        }
    }

    public void onPermissionRequest(PermissionRequest request, WebView owner) {
        cancelMediaPermission(false);
        String origin = BrowserOriginPolicy.origin(request.getOrigin().toString());
        if (origin == null || owner != callback.getActiveWebView() || callback.isFinishingOrDestroyed()) {
            request.deny();
            return;
        }
        List<String> resources = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                resources.add(resource);
                labels.add(activity.getString(R.string.browser_permission_camera));
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                resources.add(resource);
                labels.add(activity.getString(R.string.browser_permission_microphone));
            } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
                resources.add(resource);
                labels.add(activity.getString(R.string.browser_permission_drm));
            }
        }
        if (resources.isEmpty()) {
            request.deny();
            return;
        }
        PendingMediaPermission pending = new PendingMediaPermission(request, origin, resources);
        pendingMediaPermission = pending;
        syncState();
        boolean approved = true;
        for (String resource : resources) approved &= isResourceAllowed(origin, resource);
        if (approved) {
            requestMediaRuntimePermissions(pending);
            return;
        }
        mediaPermissionDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.browser_site_permission_title)
                .setMessage(activity.getString(R.string.browser_site_permission_message, origin,
                        android.text.TextUtils.join(", ", labels)))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mediaPermissionDialog = null;
                    requestMediaRuntimePermissions(pending);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> cancelMediaPermission(false))
                .setOnCancelListener(dialog -> cancelMediaPermission(false))
                .show();
    }

    public void onPermissionRequestCanceled(PermissionRequest request) {
        if (pendingMediaPermission != null && pendingMediaPermission.request == request) {
            cancelMediaPermission(true);
        }
    }

    public void onGeolocationPermissionsShowPrompt(
            final String origin,
            final GeolocationPermissions.Callback callback,
            WebView owner) {
        handleGeoPermissionCallback(false);
        String canonicalOrigin = BrowserOriginPolicy.origin(origin);
        if (canonicalOrigin == null || owner != this.callback.getActiveWebView()) {
            callback.invoke(origin, false, false);
            return;
        }
        mGeoPermissionRequestCallback = callback;
        mGeoPermissionRequestOrigin = origin;
        Runnable requestRuntime = () -> {
            if (mGeoPermissionRequestCallback != callback) return;
            boolean hasLocation = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (hasLocation) {
                handleGeoPermissionCallback(true);
            } else {
                activeLocationRequestCode = allocateBrowserRequestCode();
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        activeLocationRequestCode);
            }
        };
        if (isResourceAllowed(canonicalOrigin, "location")) {
            requestRuntime.run();
        } else {
            locationPermissionDialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.browser_site_permission_title)
                    .setMessage(activity.getString(R.string.browser_site_permission_message, canonicalOrigin,
                            activity.getString(R.string.browser_permission_location)))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> requestRuntime.run())
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> handleGeoPermissionCallback(false))
                    .setOnCancelListener(dialog -> handleGeoPermissionCallback(false))
                    .show();
        }
    }

    public void onGeolocationPermissionsHidePrompt(WebView owner) {
        if (owner == callback.getActiveWebView()) {
            handleGeoPermissionCallback(false);
        }
    }

    public void handleGeoPermissionCallback(boolean allow) {
        GeolocationPermissions.Callback callback = mGeoPermissionRequestCallback;
        String origin = mGeoPermissionRequestOrigin;
        mGeoPermissionRequestCallback = null;
        mGeoPermissionRequestOrigin = null;
        activeLocationRequestCode = -1;
        if (locationPermissionDialog != null) {
            locationPermissionDialog.dismiss();
            locationPermissionDialog = null;
        }
        if (callback != null) {
            String canonicalOrigin = BrowserOriginPolicy.origin(origin);
            if (allow && canonicalOrigin != null) {
                originPermissions.computeIfAbsent(canonicalOrigin, key -> new HashSet<>()).add("location");
            }
            callback.invoke(origin, allow, false);
        }
    }

    public boolean onShowFileChooser(
            WebView webView, ValueCallback<Uri[]> pFilePathCallback,
            WebChromeClient.FileChooserParams fileChooserParams) {
        long generation = ++fileChooserGeneration;
        completeFileChooser(null);
        if (generation != fileChooserGeneration) {
            pFilePathCallback.onReceiveValue(null);
            return true;
        }
        filePathCallback = pFilePathCallback;
        syncState();
        if (webView != callback.getActiveWebView() || callback.isFinishingOrDestroyed()) {
            completeFileChooser(null);
            return true;
        }
        activeFileRequestCode = allocateBrowserRequestCode();
        syncState();
        try {
            Intent intent = fileChooserParams.createIntent();
            activity.startActivityForResult(intent, activeFileRequestCode);
        } catch (Exception e) {
            completeFileChooser(null);
            try {
                NotificationUtils.showInfoSnackbar(activity, activity.getString(R.string.no_filemanager), Snackbar.LENGTH_LONG);
            } catch (Exception ignored) {
                NotificationUtils.showToast(activity, activity.getString(R.string.no_filemanager));
            }
            Log.w("FILE_CHOOSER", "No file chooser available", e);
        }
        return true;
    }

    public void completeFileChooser(@Nullable Uri[] result) {
        ValueCallback<Uri[]> callback = filePathCallback;
        filePathCallback = null;
        activeFileRequestCode = -1;
        syncState();
        if (callback != null) {
            callback.onReceiveValue(result);
        }
    }

    public boolean handleRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        PendingMediaPermission pending = pendingMediaPermission;
        if (pending != null && requestCode == pending.runtimeRequestCode) {
            completeMediaPermission(pending);
            return true;
        }
        if (requestCode == activeLocationRequestCode) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            handleGeoPermissionCallback(granted);
            return true;
        }
        return false;
    }

    public boolean handleActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        if (requestCode == activeFileRequestCode) {
            Uri[] result = null;
            if (resultCode == Activity.RESULT_OK && intent != null) {
                try {
                    result = WebChromeClient.FileChooserParams.parseResult(resultCode, intent);
                } catch (RuntimeException e) {
                    Log.w("FILE_CHOOSER", "Unable to read selected files", e);
                }
                if (result == null || result.length == 0) {
                    if (intent.getData() != null) {
                        result = new Uri[]{intent.getData()};
                    } else if (intent.getClipData() != null) {
                        android.content.ClipData clip = intent.getClipData();
                        int count = clip.getItemCount();
                        if (count > 0) {
                            result = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                result[i] = clip.getItemAt(i).getUri();
                            }
                        }
                    }
                }
            }
            completeFileChooser(result);
            return true;
        }
        return false;
    }

    public void resetAll() {
        cancelMediaPermission(false);
        handleGeoPermissionCallback(false);
        completeFileChooser(null);
    }

    @Nullable
    public PendingMediaPermission getPendingMediaPermission() {
        return pendingMediaPermission;
    }

    @Nullable
    public ValueCallback<Uri[]> getFilePathCallback() {
        return filePathCallback;
    }

    public int getActiveFileRequestCode() {
        return activeFileRequestCode;
    }

    public int getActiveLocationRequestCode() {
        return activeLocationRequestCode;
    }
}
