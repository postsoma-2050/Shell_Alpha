package com.cylonid.nativealpha.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.cylonid.nativealpha.R;
import io.github.edsuns.adfilter.AdFilter;
import com.cylonid.nativealpha.model.AdblockConfig;
import com.cylonid.nativealpha.helper.AdblockLifecycleHelper;
import com.cylonid.nativealpha.helper.AdblockProviderApiHelper;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.SandboxPreferences;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.ColorPalette;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.ShortcutIconUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.Map;

/**
 * Controller managing the Quick Settings Drawer in WebViewActivity.
 * Follows Hermit's architectural separation of UI controls from core browser engine,
 * handling switches, theme swatches, zoom controls, quick action cards, and drawer state sync.
 */
public class BrowserQuickSettingsController {

    public interface Callback {
        @NonNull WebApp getWebApp();
        @Nullable WebView getWebView();
        boolean isPageLoading();
        void persistQuickSettings(boolean requireReload);
        void applySystemBars();
        void applyDynamicSettings();
        void forceReloadCurrentPage();
        void reloadCurrentPage();
        void applyDesktopMode();
        void setDarkModeIfNeeded();
        void updateToolbarVisibility();
        void closeDrawer();
        void shareCurrentUrl();
        void openCurrentUrlExternally();
        void copyCurrentUrl();
        void openReaderMode();
        void openFindInPage();
        void showShortcutDialog();
        void showSandboxPickerDialog();
        void loadUrl(String url);
        String getCurrentPageUrl();
        Map<String, String> initCustomHeaders(boolean sendSaveData);
        void onHeadersUpdated(Map<String, String> headers);
        AdFilter getAdFilter();
        AdblockProviderApiHelper getAdblockProviderApiHelper();
        AdblockLifecycleHelper getAdblockLifecycleHelper();
        void applyThemeIdentity();
        void onMoreSettingsClicked();
    }

    private final Context context;
    private final View rootView;
    private final Callback callback;

    private TextView quickSettingsTitle;
    private TextView quickSettingsHost;
    private TextView quickSettingsSandbox;
    private ImageView quickSettingsFavicon;
    private LinearLayout quickColorSwatches;
    private MaterialSwitch quickOverrideSwitch;
    private boolean updatingQuickSettings;

    public BrowserQuickSettingsController(@NonNull View rootView, @NonNull Callback callback) {
        this.rootView = rootView;
        this.context = rootView.getContext();
        this.callback = callback;
        initViews();
        setupListeners();
    }

    public boolean isUpdating() {
        return updatingQuickSettings;
    }

    private void initViews() {
        quickSettingsTitle = rootView.findViewById(R.id.txtQuickSettingsTitle);
        quickSettingsHost = rootView.findViewById(R.id.txtQuickSettingsHost);
        quickSettingsSandbox = rootView.findViewById(R.id.txtQuickSettingsSandbox);
        quickSettingsFavicon = rootView.findViewById(R.id.imgQuickSettingsFavicon);
        quickColorSwatches = rootView.findViewById(R.id.quickColorSwatches);
        quickOverrideSwitch = rootView.findViewById(R.id.switchQuickOverrideGlobal);

        if (quickSettingsSandbox != null) {
            quickSettingsSandbox.setOnClickListener(v -> callback.showSandboxPickerDialog());
        }
        View quickSwitchSandbox = rootView.findViewById(R.id.btnQuickSwitchSandbox);
        if (quickSwitchSandbox != null) {
            quickSwitchSandbox.setOnClickListener(v -> callback.showSandboxPickerDialog());
        }
    }

    private void setupListeners() {
        setupActionCards();
        setupSwitches();
        setupTextZoom();
        setupColorSwatches();
        setupNavigation();
    }

    private void setupActionCards() {
        bindClick(R.id.cardReaderMode, v -> callback.openReaderMode());
        bindClick(R.id.cardCopyUrl, v -> callback.copyCurrentUrl());
        bindClick(R.id.cardFindInPage, v -> callback.openFindInPage());

        bindClick(R.id.cardQuickDesktop, v -> {
            WebApp webapp = callback.getWebApp();
            webapp.setRequestDesktop(!webapp.isRequestDesktop());
            callback.applyDesktopMode();
            callback.persistQuickSettings(true);
        });

        bindClick(R.id.cardQuickFrameless, v -> {
            WebApp webapp = callback.getWebApp();
            webapp.setFrameless(!webapp.isFrameless());
            if (webapp.isFrameless()) callback.closeDrawer();
            callback.persistQuickSettings(false);
            callback.updateToolbarVisibility();
            callback.applySystemBars();
            refreshState();
        });

        bindClick(R.id.cardQuickDarkMode, v -> {
            WebApp webapp = callback.getWebApp();
            webapp.setForceDarkMode(!webapp.isForceDarkMode());
            webapp.setUseTimespanDarkMode(false);
            callback.persistQuickSettings(false);
            callback.setDarkModeIfNeeded();
            refreshState();
        });

        bindClick(R.id.cardQuickShare, v -> callback.shareCurrentUrl());
        bindClick(R.id.cardQuickExternal, v -> callback.openCurrentUrlExternally());
        bindClick(R.id.cardQuickShortcut, v -> callback.showShortcutDialog());
    }

    private void bindClick(int viewId, View.OnClickListener listener) {
        View view = rootView.findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    private void setupSwitches() {
        MaterialSwitch fullscreen = rootView.findViewById(R.id.switchQuickFullscreen);
        if (fullscreen != null) {
            fullscreen.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                callback.getWebApp().setShowFullscreen(checked);
                callback.persistQuickSettings(false);
                callback.applySystemBars();
            });
        }

        if (quickOverrideSwitch != null) {
            quickOverrideSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                WebApp webapp = callback.getWebApp();
                webapp.setOverrideGlobalSettings(checked);
                if (!checked) {
                    int order = webapp.getOrder();
                    boolean active = webapp.isActiveEntry();
                    webapp.copySettings(DataManager.getInstance().getSettings().getGlobalWebApp());
                    webapp.setOrder(order);
                    webapp.setActiveEntry(active);
                }
                DataManager.getInstance().replaceWebApp(webapp);
                callback.applyDynamicSettings();
                callback.forceReloadCurrentPage();
            });
        }

        MaterialSwitch adblock = rootView.findViewById(R.id.switchQuickAdblock);
        if (adblock != null) {
            adblock.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                AdFilter adFilter = callback.getAdFilter();
                AdblockProviderApiHelper adblockProvider = callback.getAdblockProviderApiHelper();
                if (checked && (adFilter == null || adblockProvider == null)) {
                    updatingQuickSettings = true;
                    button.setChecked(false);
                    updatingQuickSettings = false;
                    Toast.makeText(context, R.string.browser_adblock_unavailable, Toast.LENGTH_LONG).show();
                    return;
                }
                WebApp webapp = callback.getWebApp();
                webapp.setUseAdblock(checked);
                if (checked) {
                    WebView wv = callback.getWebView();
                    if (wv != null) adFilter.setupWebView(wv);
                    List<AdblockConfig> filters = DataManager.getInstance().getSettings()
                            .getGlobalWebApp().getAdBlockSettings();
                    callback.getAdblockLifecycleHelper().trySyncOperation(() ->
                            adblockProvider.synchronizeAdblockProviderWithSettings(filters));
                }
                callback.persistQuickSettings(true);
            });
        }

        MaterialSwitch tracking = rootView.findViewById(R.id.switchQuickTracking);
        if (tracking != null) {
            tracking.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                WebApp webapp = callback.getWebApp();
                webapp.setStripTrackingParams(checked);
                callback.persistQuickSettings(false);
                if (checked) callback.loadUrl(callback.getCurrentPageUrl());
            });
        }

        MaterialSwitch dataSaver = rootView.findViewById(R.id.switchQuickDataSaver);
        if (dataSaver != null) {
            dataSaver.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                WebApp webapp = callback.getWebApp();
                webapp.setSendSavedataRequest(checked);
                webapp.setBlockImages(checked);
                Map<String, String> headers = callback.initCustomHeaders(checked);
                callback.onHeadersUpdated(headers);
                WebView wv = callback.getWebView();
                if (wv != null) {
                    wv.getSettings().setBlockNetworkImage(checked);
                }
                callback.persistQuickSettings(true);
            });
        }

        MaterialSwitch javaScript = rootView.findViewById(R.id.switchQuickJavaScript);
        if (javaScript != null) {
            javaScript.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                WebApp webapp = callback.getWebApp();
                webapp.setAllowJs(checked);
                WebView wv = callback.getWebView();
                if (wv != null) {
                    wv.getSettings().setJavaScriptEnabled(checked);
                }
                callback.persistQuickSettings(true);
            });
        }

        MaterialSwitch pullToRefresh = rootView.findViewById(R.id.switchQuickPullToRefresh);
        if (pullToRefresh != null) {
            pullToRefresh.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                callback.getWebApp().setPullToRefresh(checked);
                callback.persistQuickSettings(false);
            });
        }

        MaterialSwitch monitor = rootView.findViewById(R.id.switchQuickMonitor);
        if (monitor != null) {
            monitor.setOnCheckedChangeListener((button, checked) -> {
                if (updatingQuickSettings) return;
                WebApp webapp = callback.getWebApp();
                webapp.setMonitorEnabled(checked);
                callback.persistQuickSettings(false);
                com.cylonid.nativealpha.monitors.WebMonitorWorker.syncPeriodicWork(context);
            });
        }
    }

    private void setupTextZoom() {
        TextView txtZoom = rootView.findViewById(R.id.txtQuickZoomValue);
        View btnDecrease = rootView.findViewById(R.id.btnQuickZoomDecrease);
        View btnIncrease = rootView.findViewById(R.id.btnQuickZoomIncrease);
        if (txtZoom == null || btnDecrease == null || btnIncrease == null) return;

        btnDecrease.setOnClickListener(v -> {
            int current = callback.getWebApp().getTextZoom() > 0 ? callback.getWebApp().getTextZoom() : 100;
            int next = Math.max(50, current - 10);
            applyTextZoom(next);
        });

        btnIncrease.setOnClickListener(v -> {
            int current = callback.getWebApp().getTextZoom() > 0 ? callback.getWebApp().getTextZoom() : 100;
            int next = Math.min(250, current + 10);
            applyTextZoom(next);
        });

        txtZoom.setOnClickListener(v -> applyTextZoom(100));
    }

    public void applyTextZoom(int zoom) {
        callback.getWebApp().setTextZoom(zoom);
        WebView wv = callback.getWebView();
        if (wv != null) {
            wv.getSettings().setTextZoom(zoom);
        }
        TextView txtZoom = rootView.findViewById(R.id.txtQuickZoomValue);
        if (txtZoom != null) {
            txtZoom.setText(zoom + "%");
        }
        callback.persistQuickSettings(false);
    }

    private void setupNavigation() {
        bindClick(R.id.btnQuickHome, view -> {
            WebApp webapp = callback.getWebApp();
            if (webapp.getBaseUrl() != null) callback.loadUrl(webapp.getBaseUrl());
        });
        bindClick(R.id.btnQuickBack, view -> {
            WebView wv = callback.getWebView();
            if (wv != null && wv.canGoBack()) {
                wv.goBack();
            } else {
                callback.closeDrawer();
            }
        });
        bindClick(R.id.btnQuickRefresh, view -> callback.reloadCurrentPage());
        bindClick(R.id.btnQuickForward, view -> {
            WebView wv = callback.getWebView();
            if (wv != null && wv.canGoForward()) wv.goForward();
        });
        bindClick(R.id.btnQuickMoreSettings, view -> {
            callback.closeDrawer();
            callback.onMoreSettingsClicked();
        });
        bindClick(R.id.btnQuickClose, view -> callback.closeDrawer());
    }

    public void setupColorSwatches() {
        if (quickColorSwatches == null) return;
        quickColorSwatches.removeAllViews();
        int swatchSize = dp(48);
        int margin = dp(4);
        WebApp webapp = callback.getWebApp();
        for (Map.Entry<String, String> color : ColorPalette.INSTANCE.getHERMIT_COLORS().entrySet()) {
            View swatch = new View(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            params.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(params);
            swatch.setContentDescription(color.getKey());
            swatch.setTag(color.getKey());
            swatch.setClickable(true);
            swatch.setFocusable(true);
            swatch.setBackground(createSwatchDrawable(color.getValue()));
            swatch.setForeground(selectableItemBackgroundBorderless());
            swatch.setAlpha(webapp.getThemeColor().equalsIgnoreCase(color.getKey()) ? 1f : 0.72f);
            swatch.setOnClickListener(view -> {
                webapp.setThemeColor(color.getKey());
                callback.persistQuickSettings(false);
                callback.applyThemeIdentity();
                refreshState();
            });
            quickColorSwatches.addView(swatch);
        }
    }

    public void populateHeader() {
        if (quickSettingsTitle == null || quickSettingsHost == null || quickSettingsFavicon == null) return;
        WebApp webapp = callback.getWebApp();
        String title = webapp.getTitle();
        quickSettingsTitle.setText(title == null || title.trim().isEmpty()
                ? displayHost(webapp.getBaseUrl()) : title);
        quickSettingsHost.setText(displayHost(webapp.getBaseUrl()));
        quickSettingsFavicon.setContentDescription(context.getString(R.string.browser_controls_title));
        Bitmap favicon = ShortcutIconUtils.loadFavicon(context, webapp.getID());
        if (favicon != null) {
            quickSettingsFavicon.setImageBitmap(favicon);
        } else {
            quickSettingsFavicon.setImageResource(R.mipmap.native_alpha);
        }
    }

    public void refreshState() {
        if (quickOverrideSwitch == null) return;
        updatingQuickSettings = true;
        WebApp webapp = callback.getWebApp();
        quickOverrideSwitch.setChecked(webapp.isOverrideGlobalSettings());
        setSwitchState(R.id.switchQuickFullscreen, webapp.isShowFullscreen());
        setSwitchState(R.id.switchQuickAdblock, webapp.isUseAdblock());
        setSwitchState(R.id.switchQuickTracking, webapp.isStripTrackingParams());
        setSwitchState(R.id.switchQuickDataSaver, webapp.isBlockImages());
        setSwitchState(R.id.switchQuickJavaScript, webapp.isAllowJs());
        setSwitchState(R.id.switchQuickPullToRefresh, webapp.isPullToRefresh());
        setSwitchState(R.id.switchQuickMonitor, webapp.isMonitorEnabled());

        updateQuickActionState(R.id.cardQuickDesktop, R.id.txtQuickDesktopState,
                context.getString(webapp.isRequestDesktop()
                        ? R.string.browser_action_desktop_on : R.string.browser_action_desktop));
        updateQuickActionState(R.id.cardQuickFrameless, R.id.txtQuickFramelessState,
                context.getString(webapp.isFrameless()
                        ? R.string.browser_action_frameless_on : R.string.browser_action_frameless));
        updateQuickActionState(R.id.cardQuickDarkMode, R.id.txtQuickDarkModeState,
                context.getString(webapp.isForceDarkMode()
                        ? R.string.browser_action_dark_mode_on : R.string.browser_action_dark_mode));

        int accent = ColorPalette.webAppColor(webapp.getThemeColor(), webapp.getContainerId());
        int tint = ColorPalette.contrastingTextColor(accent);
        styleActiveCard(R.id.cardQuickDesktop, webapp.isRequestDesktop(), accent, tint);
        styleActiveCard(R.id.cardQuickFrameless, webapp.isFrameless(), accent, tint);
        styleActiveCard(R.id.cardQuickDarkMode, webapp.isForceDarkMode(), accent, tint);

        updateSandboxBadge();
        updateColorSwatchSelection();
        TextView txtZoom = rootView.findViewById(R.id.txtQuickZoomValue);
        if (txtZoom != null) {
            int zoom = webapp.getTextZoom() > 0 ? webapp.getTextZoom() : 100;
            txtZoom.setText(zoom + "%");
        }
        updateNavigationState();
        updatingQuickSettings = false;
    }

    public void updateNavigationState() {
        WebView wv = callback.getWebView();
        WebApp webapp = callback.getWebApp();
        setQuickNavigationEnabled(R.id.btnQuickHome,
                webapp.getBaseUrl() != null && !webapp.getBaseUrl().trim().isEmpty());
        setQuickNavigationEnabled(R.id.btnQuickBack, wv != null && wv.canGoBack());
        setQuickNavigationEnabled(R.id.btnQuickRefresh,
                (wv != null && wv.getUrl() != null && !wv.getUrl().trim().isEmpty())
                        || (webapp.getBaseUrl() != null && !webapp.getBaseUrl().trim().isEmpty()));
        setQuickNavigationEnabled(R.id.btnQuickForward, wv != null && wv.canGoForward());
    }

    public void updateLoadingControls(@Nullable MaterialButton toolbarButton) {
        MaterialButton quickButton = rootView.findViewById(R.id.btnQuickRefresh);
        boolean pageLoading = callback.isPageLoading();
        WebView wv = callback.getWebView();
        int icon = pageLoading ? R.drawable.ic_browser_stop_24 : R.drawable.ic_baseline_refresh_24;
        int description = pageLoading ? R.string.browser_stop_loading : R.string.browser_refresh_page;
        if (toolbarButton != null) {
            toolbarButton.setIconResource(icon);
            toolbarButton.setContentDescription(context.getString(description));
            toolbarButton.setEnabled(wv != null);
        }
        if (quickButton != null) {
            quickButton.setIconResource(icon);
            quickButton.setContentDescription(context.getString(description));
            quickButton.setEnabled(pageLoading || (wv != null && wv.getUrl() != null));
            quickButton.setAlpha(quickButton.isEnabled() ? 1f : 0.38f);
        }
    }

    public void updateSandboxBadge() {
        if (quickSettingsSandbox == null) return;
        WebApp webapp = callback.getWebApp();
        int containerId = webapp.getContainerId();
        String name = SandboxPreferences.getName(containerId);
        quickSettingsSandbox.setText(name);
        int color = ColorPalette.colorForContainer(containerId);
        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(dp(12));
        badge.setColor(color);
        quickSettingsSandbox.setBackground(badge);
        quickSettingsSandbox.setTextColor(ColorPalette.contrastingTextColor(color));
        quickSettingsSandbox.setContentDescription(
                context.getString(R.string.change_sandbox) + ": " + name);
    }

    private void updateColorSwatchSelection() {
        if (quickColorSwatches == null) return;
        WebApp webapp = callback.getWebApp();
        for (int i = 0; i < quickColorSwatches.getChildCount(); i++) {
            View swatch = quickColorSwatches.getChildAt(i);
            String colorName = swatch.getTag() == null ? "" : swatch.getTag().toString();
            boolean selected = colorName.equalsIgnoreCase(webapp.getThemeColor());
            swatch.setAlpha(selected ? 1f : 0.72f);
            swatch.setScaleX(selected ? 1.12f : 1f);
            swatch.setScaleY(selected ? 1.12f : 1f);
            swatch.setContentDescription(context.getString(selected
                    ? R.string.browser_theme_color_selected
                    : R.string.browser_theme_color_named, colorName));
        }
    }

    private void setSwitchState(int id, boolean checked) {
        MaterialSwitch button = rootView.findViewById(id);
        if (button != null) {
            button.setChecked(checked);
        }
    }

    private void updateQuickActionState(int cardId, int textId, String label) {
        TextView text = rootView.findViewById(textId);
        if (text != null) text.setText(label);
        View card = rootView.findViewById(cardId);
        if (card != null) card.setContentDescription(label);
    }

    private void styleActiveCard(int cardId, boolean active, int accent, int contentColor) {
        MaterialCardView card = rootView.findViewById(cardId);
        if (card == null) return;
        card.setCardBackgroundColor(active ? accent : MaterialColors.getColor(
                card, R.attr.colorSurfaceContainer, Color.TRANSPARENT));
        int childCount = card.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = card.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View content = group.getChildAt(j);
                    if (content instanceof ImageView) {
                        ((ImageView) content).setImageTintList(
                                ColorStateList.valueOf(active ? contentColor :
                                        MaterialColors.getColor(content, R.attr.colorOnSurface, Color.BLACK)));
                    } else if (content instanceof TextView) {
                        ((TextView) content).setTextColor(active ? contentColor :
                                MaterialColors.getColor(content, R.attr.colorOnSurface, Color.BLACK));
                    }
                }
            }
        }
    }

    private void setQuickNavigationEnabled(int id, boolean enabled) {
        View button = rootView.findViewById(id);
        if (button != null) {
            button.setEnabled(enabled);
            button.setAlpha(enabled ? 1f : 0.38f);
        }
    }

    private GradientDrawable createSwatchDrawable(String colorValue) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor(colorValue));
        drawable.setStroke(dp(2), MaterialColors.getColor(
                quickColorSwatches, R.attr.colorOnSurfaceVariant, Color.GRAY));
        return drawable;
    }

    @Nullable
    private Drawable selectableItemBackgroundBorderless() {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, value, true)
                || value.resourceId == 0) {
            return null;
        }
        return ContextCompat.getDrawable(context, value.resourceId);
    }

    public static String displayHost(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return "";
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return "";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
