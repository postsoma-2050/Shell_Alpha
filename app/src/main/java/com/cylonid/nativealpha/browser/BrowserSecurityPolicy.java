package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Context;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Encapsulates browser security decisions, SSL certificate validation policies,
 * and external application intent / URL scheme routing.
 * Decoupled from WebViewActivity following Hermit's architectural patterns.
 */
public final class BrowserSecurityPolicy {

    private static final Set<String> FORBIDDEN_SCHEMES = new HashSet<>(Arrays.asList(
            "javascript", "file", "content", "about", "data"
    ));

    private BrowserSecurityPolicy() {}

    /**
     * Checks if the given URL scheme represents a standard web document scheme (http/https).
     */
    public static boolean isWebScheme(@Nullable String scheme) {
        if (scheme == null) return false;
        String lower = scheme.toLowerCase(Locale.ROOT);
        return "http".equals(lower) || "https".equals(lower);
    }

    /**
     * Checks whether an external (non-web) scheme is safe to dispatch to Android's Intent system.
     * Rejects internal pseudo-protocols and script execution schemes.
     */
    public static boolean isExternalSchemeDispatchable(@Nullable String scheme) {
        if (scheme == null || scheme.trim().isEmpty()) return false;
        String lower = scheme.trim().toLowerCase(Locale.ROOT);
        if (isWebScheme(lower)) return false;
        return !FORBIDDEN_SCHEMES.contains(lower);
    }

    /**
     * Decides whether an external navigation should launch in an external application or stay inside the WebView.
     */
    public static boolean shouldOpenExternally(@Nullable WebApp webapp, @Nullable String url, boolean hasPopupParents) {
        if (webapp == null || url == null) return false;
        return webapp.isOpenUrlExternal()
                && webapp.isAllowAppLaunch()
                && !hasPopupParents
                && !BrowserOriginPolicy.belongsToSite(url, webapp.getBaseUrl());
    }

    /**
     * Formats a localized explanatory description for an SSL certificate error.
     */
    @NonNull
    public static String formatSslErrorMessage(@NonNull Context context, @NonNull SslError error) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.ssl_error_msg_line1)).append(" ");
        switch (error.getPrimaryError()) {
            case SslError.SSL_UNTRUSTED:
                builder.append(context.getString(R.string.ssl_error_unknown_authority)).append("\n");
                break;
            case SslError.SSL_EXPIRED:
                builder.append(context.getString(R.string.ssl_error_expired)).append("\n");
                break;
            case SslError.SSL_IDMISMATCH:
                builder.append(context.getString(R.string.ssl_error_id_mismatch)).append("\n");
                break;
            case SslError.SSL_NOTYETVALID:
                builder.append(context.getString(R.string.ssl_error_notyetvalid)).append("\n");
                break;
            default:
                break;
        }
        builder.append(context.getString(R.string.ssl_error_msg_line2)).append("\n");
        return builder.toString();
    }

    /**
     * Displays a standard Material confirmation dialog for SSL errors, prompting the user
     * to either safely abort or explicitly accept the risk.
     */
    public static void showSslErrorDialog(@NonNull Activity activity, @NonNull SslErrorHandler handler,
                                          @NonNull SslError error) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            handler.cancel();
            return;
        }

        String message = formatSslErrorMessage(activity, error);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.ssl_error_title))
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(activity.getString(android.R.string.cancel), (dialog, id) -> handler.cancel())
                .setNegativeButton(activity.getString(R.string.load_anyway), (dialog, id) -> handler.proceed());

        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    ContextCompat.getColor(activity, android.R.color.holo_red_dark));
        }
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    ContextCompat.getColor(activity, android.R.color.holo_green_dark));
        }
    }
}
