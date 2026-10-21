package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ShareCompat;

import com.cylonid.nativealpha.BuildConfig;
import com.cylonid.nativealpha.MainActivity;
import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.helper.IconPopupMenuHelper;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.google.android.material.color.MaterialColors;

import java.util.Map;
import java.util.Objects;

import io.github.edsuns.adfilter.AdFilter;
import io.github.edsuns.adfilter.Filter;

/**
 * Controller responsible for the browser long-click context popup menu,
 * URL sharing, clipboard copying, and external intent dispatch.
 * Aligned with Hermit Lite Apps context menu architecture.
 */
public class BrowserContextMenuController {

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        @Nullable
        WebView getWebView();

        @Nullable
        AdFilter getAdFilter();

        void goBack();

        void reload();

        void openFindInPage();

        void openQuickSettings();

        void finishAndRemoveTask();

        void setFallbackLongClick(boolean fallback);

        void showSandboxDialog(@Nullable String linkUrl);

        void closeDrawer();

        default void printPage() {}
    }

    private final Activity activity;
    private final Callback callback;
    private PopupMenu popupMenu;

    public BrowserContextMenuController(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void showContextMenu(@NonNull View anchor, @Nullable String lastLongClickedUrl) {
        WebApp webapp = callback.getWebApp();
        WebView wv = callback.getWebView();
        if (webapp == null || webapp.isFrameless() || wv == null) return;

        dismissMenu();
        popupMenu = IconPopupMenuHelper.getMenu(anchor, R.menu.wv_context_menu, activity);

        String currentUrl = lastLongClickedUrl != null ? lastLongClickedUrl : wv.getUrl();
        String title = "";
        if (currentUrl != null) {
            title = currentUrl.length() < 32 ? currentUrl : currentUrl.substring(0, 32) + "…";
        }
        SpannableString spanStringWebAppTitle = new SpannableString(title);

        int colorOnSurface = MaterialColors.getColor(anchor, R.attr.colorOnSurface, Color.BLACK);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(colorOnSurface);
        spanStringWebAppTitle.setSpan(foregroundColorSpan, 0, spanStringWebAppTitle.length(), 0);
        spanStringWebAppTitle.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, spanStringWebAppTitle.length(), 0);
        popupMenu.getMenu().getItem(0).setTitle(spanStringWebAppTitle);

        MenuItem sandboxItem = popupMenu.getMenu().findItem(R.id.cmOpenInSandbox);
        if (sandboxItem != null) {
            sandboxItem.setTitle(lastLongClickedUrl != null
                    ? R.string.open_link_in_sandbox
                    : R.string.open_in_sandbox);
        }

        for (int i = 0; i < popupMenu.getMenu().size(); i++) {
            MenuItem item = popupMenu.getMenu().getItem(i);
            SpannableString spanString = new SpannableString(item.getTitle());
            spanString.setSpan(foregroundColorSpan, 0, spanString.length(), 0);
            item.setTitle(spanString);
        }
        MenuItem forwardItem = popupMenu.getMenu().findItem(R.id.cmItemForward);
        if (forwardItem != null) {
            forwardItem.setVisible(wv.canGoForward());
        }
        MenuItem adblockItem = popupMenu.getMenu().findItem(R.id.cmShowAdblockProviders);
        if (adblockItem != null && BuildConfig.DEBUG) {
            adblockItem.setVisible(true);
        }

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.cmOpenInSandbox) {
                callback.showSandboxDialog(lastLongClickedUrl);
                return true;
            } else if (id == R.id.cmItemForward) {
                wv.goForward();
                return true;
            } else if (id == R.id.cmItemBack) {
                callback.goBack();
                return true;
            } else if (id == R.id.cmItemReload) {
                callback.reload();
                return true;
            } else if (id == R.id.cmItemCopyUrl) {
                if (lastLongClickedUrl != null) {
                    copyUrlToClipboard(lastLongClickedUrl);
                } else {
                    copyCurrentUrl();
                }
                return true;
            } else if (id == R.id.cmItemFindInPage) {
                callback.openFindInPage();
                return true;
            } else if (id == R.id.cmItemPrint) {
                callback.printPage();
                return true;
            } else if (id == R.id.cmItemShareUrl) {
                shareUrl(lastLongClickedUrl != null ? lastLongClickedUrl : wv.getUrl());
                return true;
            } else if (id == R.id.cmItemCloseWebApp) {
                callback.finishAndRemoveTask();
                return true;
            } else if (id == R.id.cmFallbackContextmenuTemp) {
                callback.setFallbackLongClick(true);
                return true;
            } else if (id == R.id.cmMainMenu) {
                activity.startActivity(new Intent(activity, MainActivity.class));
                return true;
            } else if (id == R.id.cmItemQuickSettings) {
                callback.openQuickSettings();
                return true;
            } else if (id == R.id.cmShowAdblockProviders) {
                showAdblockProvidersToast();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showAdblockProvidersToast() {
        StringBuilder message = new StringBuilder();
        AdFilter currentFilter = callback.getAdFilter();
        if (currentFilter == null) {
            try {
                currentFilter = AdFilter.Companion.get(activity.getApplicationContext());
            } catch (Exception ignored) {
            }
        }
        if (currentFilter != null && currentFilter.getViewModel().getFilters().getValue() != null) {
            for (Map.Entry<String, Filter> entry : Objects.requireNonNull(currentFilter.getViewModel().getFilters().getValue()).entrySet()) {
                Filter filter = entry.getValue();
                message.append(filter.getUrl()).append(" has downloaded: ").append(filter.hasDownloaded()).append("\n\n");
            }
        }
        NotificationUtils.showToast(activity, message.toString());
    }

    public void dismissMenu() {
        if (popupMenu != null) {
            popupMenu.dismiss();
            popupMenu = null;
        }
    }

    @Nullable
    public PopupMenu getPopupMenu() {
        return popupMenu;
    }

    public void copyCurrentUrl() {
        copyUrlToClipboard(currentPageUrl());
    }

    public void copyUrlToClipboard(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return;
        ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url));
        callback.closeDrawer();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(activity, R.string.browser_url_copied, Toast.LENGTH_SHORT).show();
        }
    }

    public void shareCurrentUrl() {
        shareUrl(currentPageUrl());
    }

    public void shareUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return;
        new ShareCompat.IntentBuilder(activity)
                .setType("text/plain")
                .setChooserTitle(activity.getString(R.string.share))
                .setText(url)
                .startChooser();
    }

    public void openCurrentUrlExternally() {
        openUrlExternally(currentPageUrl());
    }

    public void openUrlExternally(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(activity, R.string.external_app_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    public String currentPageUrl() {
        WebView wv = callback.getWebView();
        WebApp webapp = callback.getWebApp();
        String url = wv == null ? null : wv.getUrl();
        return url == null || url.trim().isEmpty() ? (webapp == null ? null : webapp.getBaseUrl()) : url;
    }
}
