package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.SandboxActivity;
import com.cylonid.nativealpha.adapter.SandboxChoiceAdapter;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.SandboxPreferences;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.ColorPalette;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.WebViewLauncher;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoupled controller responsible for Sandbox/Container selection dialogs,
 * container switching, and launching isolated Lite App instances in new processes.
 * Aligned with Hermit Lite Apps container management architecture.
 */
public class BrowserSandboxDialogHelper {

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        void loadUrl(@NonNull String url);

        void onContainerSwitched(@NonNull WebApp updatedApp);

        void closeDrawer();
    }

    private final Activity activity;
    private final Callback callback;

    public BrowserSandboxDialogHelper(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void showSandboxPickerDialog() {
        showOpenInSandboxDialog(null);
    }

    public void showOpenInSandboxDialog(@Nullable String linkUrl) {
        WebApp webapp = callback.getWebApp();
        if (webapp == null || activity.isFinishing() || activity.isDestroyed()) return;
        int currentContainerId = webapp.getContainerId();
        int sandboxCount = SandboxManager.getSandboxCount();
        List<SandboxChoiceAdapter.Item> items = new ArrayList<>();

        String defaultName = activity.getString(
                R.string.entry_sandbox_default,
                SandboxPreferences.getName(Const.NO_CONTAINER));
        int defaultColor = ColorPalette.colorForContainer(Const.NO_CONTAINER);
        items.add(new SandboxChoiceAdapter.Item(Const.NO_CONTAINER, defaultName, defaultColor));

        for (int i = 0; i < sandboxCount; i++) {
            String name = SandboxPreferences.getName(i);
            String label = activity.getString(R.string.entry_sandbox_named, name, i + 1);
            int color = ColorPalette.colorForContainer(i);
            items.add(new SandboxChoiceAdapter.Item(i, label, color));
        }

        int checkedItem = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).containerId == currentContainerId) {
                checkedItem = i;
                break;
            }
        }

        SandboxChoiceAdapter adapter = new SandboxChoiceAdapter(activity, items, currentContainerId);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(linkUrl != null ? R.string.open_link_in_sandbox : R.string.sandbox_interaction_choose_title)
                .setSingleChoiceItems(adapter, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    SandboxChoiceAdapter.Item chosen = items.get(which);
                    int targetContainerId = chosen.containerId;
                    if (linkUrl != null && !linkUrl.isEmpty()) {
                        openLinkInSandbox(linkUrl, targetContainerId);
                    } else if (targetContainerId != webapp.getContainerId()) {
                        webapp.setContainerId(targetContainerId);
                        webapp.setUseContainer(SandboxManager.isValidContainerId(targetContainerId));
                        DataManager.getInstance().replaceWebApp(webapp);
                        callback.closeDrawer();
                        callback.onContainerSwitched(webapp);
                    }
                })
                .setNeutralButton(R.string.manage_sandbox_names, (dialog, which) -> {
                    dialog.dismiss();
                    activity.startActivity(new Intent(activity, SandboxActivity.class));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void openLinkInSandbox(@NonNull String targetUrl, int targetContainerId) {
        WebApp webapp = callback.getWebApp();
        if (webapp != null && targetContainerId == webapp.getContainerId()) {
            callback.loadUrl(targetUrl);
            return;
        }
        WebApp targetApp = null;
        for (WebApp app : DataManager.getInstance().getActiveWebsites()) {
            if (app.getContainerId() == targetContainerId && BrowserOriginPolicy.belongsToSite(targetUrl, app.getBaseUrl())) {
                targetApp = app;
                break;
            }
        }
        if (targetApp == null) {
            targetApp = new WebApp(targetUrl, DataManager.getInstance().getIncrementedID(), DataManager.getInstance().getIncrementedOrder());
            targetApp.applySettingsForNewWebApp();
            targetApp.setContainerId(targetContainerId);
            targetApp.setUseContainer(SandboxManager.isValidContainerId(targetContainerId));
            DataManager.getInstance().addWebsite(targetApp);
        }
        Intent intent = WebViewLauncher.createWebViewIntent(targetApp, activity);
        if (intent != null) {
            intent.putExtra(WebViewLauncher.EXTRA_TARGET_URL, targetUrl);
            activity.startActivity(intent);
        }
    }
}
