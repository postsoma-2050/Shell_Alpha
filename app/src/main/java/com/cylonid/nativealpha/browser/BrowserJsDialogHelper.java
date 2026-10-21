package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.cylonid.nativealpha.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Encapsulates JavaScript alert, confirm, prompt, and beforeunload dialog logic.
 * Follows Hermit's modular architecture to isolate WebChromeClient UI callbacks
 * from the host Activity, prevents WindowManager memory leaks via active dialog tracking,
 * and guarantees safe JS thread resumption on Activity termination or navigation.
 */
public class BrowserJsDialogHelper {

    private final Activity activity;
    private AlertDialog activeDialog;
    private JsResult activeResult;

    public BrowserJsDialogHelper(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void dismissActiveDialog() {
        if (activeResult != null) {
            try {
                activeResult.cancel();
            } catch (Exception ignored) {}
            activeResult = null;
        }
        if (activeDialog != null) {
            try {
                if (activeDialog.isShowing()) {
                    activeDialog.dismiss();
                }
            } catch (Exception ignored) {}
            activeDialog = null;
        }
    }

    @Nullable
    public AlertDialog getActiveDialog() {
        return activeDialog;
    }

    public boolean handleJsAlert(@Nullable WebView view, @Nullable String url, @Nullable String message, @NonNull JsResult result) {
        showJsAlert(view, url, message, result);
        return true;
    }

    @Nullable
    public AlertDialog showJsAlert(@Nullable WebView view, @Nullable String url, @Nullable String message, @NonNull JsResult result) {
        if (isActivityUnavailable()) {
            result.cancel();
            return null;
        }
        dismissActiveDialog();
        activeResult = result;

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.browser_js_alert_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    activeResult = null;
                    activeDialog = null;
                    result.confirm();
                })
                .setOnCancelListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    activeDialog = null;
                })
                .setOnDismissListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    if (activeDialog == d) {
                        activeDialog = null;
                    }
                })
                .show();
        activeDialog = dialog;
        return dialog;
    }

    public boolean handleJsConfirm(@Nullable WebView view, @Nullable String url, @Nullable String message, @NonNull JsResult result) {
        showJsConfirm(view, url, message, result);
        return true;
    }

    @Nullable
    public AlertDialog showJsConfirm(@Nullable WebView view, @Nullable String url, @Nullable String message, @NonNull JsResult result) {
        if (isActivityUnavailable()) {
            result.cancel();
            return null;
        }
        dismissActiveDialog();
        activeResult = result;

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.browser_js_confirm_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    activeResult = null;
                    activeDialog = null;
                    result.confirm();
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    activeResult = null;
                    activeDialog = null;
                    result.cancel();
                })
                .setOnCancelListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    activeDialog = null;
                })
                .setOnDismissListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    if (activeDialog == d) {
                        activeDialog = null;
                    }
                })
                .show();
        activeDialog = dialog;
        return dialog;
    }

    public boolean handleJsPrompt(@Nullable WebView view, @Nullable String url, @Nullable String message,
                                 @Nullable String defaultValue, @NonNull JsPromptResult result) {
        showJsPrompt(view, url, message, defaultValue, result);
        return true;
    }

    @Nullable
    public AlertDialog showJsPrompt(@Nullable WebView view, @Nullable String url, @Nullable String message,
                                   @Nullable String defaultValue, @NonNull JsPromptResult result) {
        if (isActivityUnavailable()) {
            result.cancel();
            return null;
        }
        dismissActiveDialog();
        activeResult = result;

        final EditText input = new EditText(activity);
        input.setText(defaultValue);
        FrameLayout container = new FrameLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = dp(activity, 20);
        params.setMargins(margin, 0, margin, 0);
        input.setLayoutParams(params);
        container.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.browser_js_prompt_title)
                .setMessage(message)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    activeResult = null;
                    activeDialog = null;
                    result.confirm(input.getText() != null ? input.getText().toString() : "");
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    activeResult = null;
                    activeDialog = null;
                    result.cancel();
                })
                .setOnCancelListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    activeDialog = null;
                })
                .setOnDismissListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    if (activeDialog == d) {
                        activeDialog = null;
                    }
                })
                .show();
        activeDialog = dialog;
        return dialog;
    }

    public boolean handleJsBeforeUnload(@Nullable WebView view, @Nullable String url, @Nullable String message, @NonNull JsResult result) {
        showJsBeforeUnload(view, url, message, result);
        return true;
    }

    @Nullable
    public AlertDialog showJsBeforeUnload(@Nullable WebView view, @Nullable String url, @Nullable String message, @NonNull JsResult result) {
        if (isActivityUnavailable()) {
            result.cancel();
            return null;
        }
        dismissActiveDialog();
        activeResult = result;

        String displayMsg = (message != null && !message.isEmpty())
                ? message
                : activity.getString(R.string.browser_js_leave_page_message);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.browser_js_leave_page_title)
                .setMessage(displayMsg)
                .setPositiveButton(R.string.browser_js_leave_page_positive, (d, which) -> {
                    activeResult = null;
                    activeDialog = null;
                    result.confirm();
                })
                .setNegativeButton(R.string.browser_js_leave_page_negative, (d, which) -> {
                    activeResult = null;
                    activeDialog = null;
                    result.cancel();
                })
                .setOnCancelListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    activeDialog = null;
                })
                .setOnDismissListener(d -> {
                    if (activeResult == result) {
                        activeResult = null;
                        result.cancel();
                    }
                    if (activeDialog == d) {
                        activeDialog = null;
                    }
                })
                .show();
        activeDialog = dialog;
        return dialog;
    }

    public boolean isActivityUnavailable() {
        return activity == null || activity.isFinishing() || activity.isDestroyed();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
