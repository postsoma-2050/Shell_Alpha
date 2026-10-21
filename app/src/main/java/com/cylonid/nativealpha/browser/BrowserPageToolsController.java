package com.cylonid.nativealpha.browser;

import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

/**
 * Controller responsible for managing page tools in frameless / fullscreen mode,
 * including drawer tool buttons, safe insets adjustment, accessibility triggers,
 * and page search integration.
 * Aligned with Hermit Lite Apps Page Tools architecture.
 */
public class BrowserPageToolsController {

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        @Nullable
        WebView getActiveWebView();

        @Nullable
        WebView getReaderWebView();

        boolean isReaderOpen();

        boolean isMediaCustomViewShowing();

        @Nullable
        DrawerLayout getDrawerLayout();

        void openReaderMode();

        void copyCurrentUrl();

        void closeDrawer();

        void openQuickSettings();

        void onFindStateChanged();
    }

    private final View root;
    private final Callback callback;
    private PageFindController pageFindController;

    private View framelessPageTools;
    private View quickSettingsScroll;
    private View quickSettingsNavigation;
    private View quickSettingsDivider;

    public BrowserPageToolsController(@NonNull View root, @NonNull Callback callback) {
        this.root = root;
        this.callback = callback;
        initViews();
    }

    private void initViews() {
        framelessPageTools = root.findViewById(R.id.framelessPageTools);
        quickSettingsScroll = root.findViewById(R.id.quickSettingsScroll);
        quickSettingsNavigation = root.findViewById(R.id.quickSettingsNavigation);
        quickSettingsDivider = root.findViewById(R.id.quickSettingsDivider);

        View btnReader = root.findViewById(R.id.btnPageToolsReader);
        if (btnReader != null) {
            btnReader.setOnClickListener(v -> callback.openReaderMode());
        }
        View btnFind = root.findViewById(R.id.btnPageToolsFind);
        if (btnFind != null) {
            btnFind.setOnClickListener(v -> openFindInPage());
        }
        View btnCopy = root.findViewById(R.id.btnPageToolsCopy);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> callback.copyCurrentUrl());
        }
        View btnClose = root.findViewById(R.id.btnPageToolsClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> callback.closeDrawer());
        }

        ViewCompat.addAccessibilityAction(root, root.getContext().getString(R.string.browser_page_tools),
                (view, args) -> {
                    if (callback.isMediaCustomViewShowing()) return false;
                    openPageTools();
                    return true;
                });
    }

    public void setPageFindController(@Nullable PageFindController pageFindController) {
        this.pageFindController = pageFindController;
    }

    @Nullable
    public PageFindController getPageFindController() {
        return pageFindController;
    }

    public void updatePageToolsVisibility() {
        WebApp webApp = callback.getWebApp();
        boolean toolsOnly = webApp != null && webApp.isFrameless();

        if (framelessPageTools != null) {
            framelessPageTools.setVisibility(toolsOnly ? View.VISIBLE : View.GONE);
        }
        if (quickSettingsScroll != null) {
            quickSettingsScroll.setVisibility(toolsOnly ? View.GONE : View.VISIBLE);
        }
        if (quickSettingsNavigation != null) {
            quickSettingsNavigation.setVisibility(toolsOnly ? View.GONE : View.VISIBLE);
        }
        if (quickSettingsDivider != null) {
            quickSettingsDivider.setVisibility(toolsOnly ? View.GONE : View.VISIBLE);
        }

        applyPageToolsInsets(ViewCompat.getRootWindowInsets(root));
    }

    public void applyPageToolsInsets(@Nullable WindowInsetsCompat insets) {
        if (framelessPageTools == null) return;
        Insets safe = Insets.NONE;
        if (insets != null) {
            safe = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            if (Insets.NONE.equals(safe)) {
                safe = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            }
            if (Insets.NONE.equals(safe)) {
                safe = insets.getSystemWindowInsets();
            }
        }
        int padding16 = dpToPx(16);
        framelessPageTools.setPadding(
                padding16 + safe.left,
                padding16 + safe.top,
                padding16 + safe.right,
                padding16 + safe.bottom);
    }

    public void openPageTools() {
        WebApp webApp = callback.getWebApp();
        DrawerLayout drawer = callback.getDrawerLayout();
        if (webApp == null || callback.isMediaCustomViewShowing() || drawer == null) return;
        if (!webApp.isFrameless()) {
            callback.openQuickSettings();
            return;
        }
        updatePageToolsVisibility();
        drawer.openDrawer(GravityCompat.END);
    }

    public void openFindInPage() {
        WebApp webApp = callback.getWebApp();
        WebView activeWv = callback.getActiveWebView();
        if (webApp == null || callback.isMediaCustomViewShowing() || activeWv == null || pageFindController == null) {
            return;
        }
        callback.closeDrawer();
        WebView target = (callback.isReaderOpen() && callback.getReaderWebView() != null)
                ? callback.getReaderWebView()
                : activeWv;
        pageFindController.open(target);
        callback.onFindStateChanged();
    }

    public void closeFind() {
        if (pageFindController != null) {
            pageFindController.close();
            callback.onFindStateChanged();
        }
    }

    public boolean isFindOpen() {
        return pageFindController != null && pageFindController.isOpen();
    }

    public boolean handleBackPressed() {
        if (isFindOpen()) {
            closeFind();
            return true;
        }
        return false;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * root.getResources().getDisplayMetrics().density);
    }
}
