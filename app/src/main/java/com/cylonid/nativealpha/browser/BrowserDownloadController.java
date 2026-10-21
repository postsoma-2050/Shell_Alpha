package com.cylonid.nativealpha.browser;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.CookieManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.SandboxPreferences;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.cylonid.nativealpha.util.Utility;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

/**
 * Handles browser download requests, MIME dispatching, storage permission workflows,
 * and DownloadManager queuing. Decoupled from WebViewActivity for modularity and testability.
 */
public class BrowserDownloadController {
    private static final String TAG = "DOWNLOAD";
    private final Activity activity;
    private DownloadManager.Request pendingRequest = null;

    public BrowserDownloadController(@NonNull Activity activity) {
        this.activity = activity;
    }

    @Nullable
    public DownloadManager.Request getPendingRequest() {
        return pendingRequest;
    }

    public void setPendingRequest(@Nullable DownloadManager.Request request) {
        this.pendingRequest = request;
    }

    public void handleDownload(String url, String userAgent, String contentDisposition,
                               String mimeType, long contentLength, @Nullable WebApp webapp) {
        if (BrowserOriginPolicy.origin(url) == null) {
            Toast.makeText(activity, R.string.browser_download_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        if ("application/pdf".equalsIgnoreCase(mimeType) && webapp != null && webapp.isAllowAppLaunch()) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(url), "application/pdf"));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(activity, R.string.browser_pdf_download_fallback, Toast.LENGTH_LONG).show();
            }
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String filename = Utility.getFileNameFromDownload(url, contentDisposition, mimeType);
            if (filename == null || filename.trim().isEmpty()) filename = "download";
            filename = filename.replace('/', '_').replace('\\', '_');
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
            int containerId = webapp != null ? webapp.getContainerId() : 0;
            String cookies = SandboxPreferences.isCookiesAllowed(containerId)
                    ? CookieManager.getInstance().getCookie(url) : null;
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null && !userAgent.isEmpty()) request.addRequestHeader("User-Agent", userAgent);
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && !EasyPermissions.hasPermissions(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (pendingRequest != null) {
                    Toast.makeText(activity, R.string.browser_download_permission_pending, Toast.LENGTH_LONG).show();
                    return;
                }
                pendingRequest = request;
                EasyPermissions.requestPermissions(activity, activity.getString(R.string.permission_storage_rationale),
                        Const.PERMISSION_RC_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                enqueueDownload(request);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to prepare download", e);
            Toast.makeText(activity, R.string.browser_download_failed, Toast.LENGTH_LONG).show();
        }
    }

    public void enqueueDownload(DownloadManager.Request request) {
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null || manager.enqueue(request) <= 0) {
                Toast.makeText(activity, R.string.browser_download_failed, Toast.LENGTH_LONG).show();
                return;
            }
            NotificationUtils.showInfoSnackbar(activity, activity.getString(R.string.file_download), Snackbar.LENGTH_SHORT);
        } catch (RuntimeException e) {
            Log.w(TAG, "DownloadManager rejected download", e);
            Toast.makeText(activity, R.string.browser_download_failed, Toast.LENGTH_LONG).show();
        }
    }

    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        if (requestCode == Const.PERMISSION_RC_STORAGE) {
            DownloadManager.Request request = pendingRequest;
            pendingRequest = null;
            if (request != null) enqueueDownload(request);
        }
    }

    public void onPermissionsDenied(int requestCode, @NonNull List<String> list) {
        if (requestCode == Const.PERMISSION_RC_STORAGE) {
            pendingRequest = null;
            Toast.makeText(activity, R.string.browser_download_failed, Toast.LENGTH_LONG).show();
        }
    }
}
