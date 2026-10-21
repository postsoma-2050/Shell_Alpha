package com.cylonid.nativealpha;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.ViewGroup;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.cylonid.nativealpha.databinding.DialogHttpAuthBinding;
import com.cylonid.nativealpha.helper.AdblockLifecycleHelper;
import com.cylonid.nativealpha.helper.AdblockProviderApiHelper;
import com.cylonid.nativealpha.helper.BiometricPromptHelper;
import com.cylonid.nativealpha.helper.IconPopupMenuHelper;
import com.cylonid.nativealpha.media.BackgroundPlaybackController;
import com.cylonid.nativealpha.model.AdblockConfig;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.SandboxPreferences;
import com.cylonid.nativealpha.browser.BrowserOriginPolicy;
import com.cylonid.nativealpha.browser.PageFindController;
import com.cylonid.nativealpha.browser.BrowserPageToolsController;
import com.cylonid.nativealpha.browser.BrowserThemeController;
import com.cylonid.nativealpha.browser.BrowserPrintController;
import com.cylonid.nativealpha.browser.BrowserReaderController;
import com.cylonid.nativealpha.browser.BrowserBarsController;
import com.cylonid.nativealpha.browser.BrowserDrawerLayout;
import com.cylonid.nativealpha.browser.BrowserDownloadController;
import com.cylonid.nativealpha.browser.BrowserHeadersPolicy;
import com.cylonid.nativealpha.browser.BrowserJsDialogHelper;
import com.cylonid.nativealpha.browser.BrowserPermissionsController;
import com.cylonid.nativealpha.browser.BrowserQuickSettingsController;
import com.cylonid.nativealpha.browser.BrowserContextMenuController;
import com.cylonid.nativealpha.browser.BrowserSandboxDialogHelper;
import com.cylonid.nativealpha.browser.BrowserSecurityPolicy;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.ColorPalette;
import com.cylonid.nativealpha.util.DateUtils;
import com.cylonid.nativealpha.util.EntryPointUtils;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.cylonid.nativealpha.util.ShortcutIconUtils;
import com.cylonid.nativealpha.util.UrlSanitizer;
import com.cylonid.nativealpha.util.Utility;
import com.cylonid.nativealpha.util.WebViewLauncher;
import com.cylonid.nativealpha.adapter.SandboxChoiceAdapter;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import io.github.edsuns.adfilter.AdFilter;
import io.github.edsuns.adfilter.Filter;
import io.github.edsuns.adfilter.FilterResult;
import pub.devrel.easypermissions.EasyPermissions;


public class WebViewActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    //Constants for touchlistener
    private static final int NONE = 0;
    private static final int SWIPE = 1;
    private static final int TRESHOLD = 100;
    private static final int REQUEST_WEBAPP_SETTINGS = 4102;
    private static final long BACK_EXIT_HINT_TIMEOUT_MS = 2500L;
    int webappID = -1;
    private WebView wv;
    private ProgressBar progressBar;
    private DrawerLayout drawerLayout;
    private LinearLayout browserToolbarContainer;
    private View quickSettingsDrawer;
    private int appliedDrawerWidth;
    private TextView toolbarTitle;
    private TextView toolbarSubtitle;
    private BrowserQuickSettingsController quickSettingsController;
    private String lastLongClickedUrl = null;
    private boolean pageLoading;
    private String defaultUserAgent;
    private boolean currently_reloading = true;
    private BrowserDownloadController downloadController;
    private BrowserJsDialogHelper jsDialogHelper;
    private BrowserPermissionsController permissionsController;
    private DownloadManager.Request dl_request = null;
    private Map<String, String> CUSTOM_HEADERS;
    protected ValueCallback<Uri[]> filePathCallback;
    private int activeFileRequestCode = -1;
    private BrowserPermissionsController.PendingMediaPermission pendingMediaPermission;
    private final List<WebView> popupParents = new ArrayList<>();
    private View mediaCustomView;
    private WebChromeClient.CustomViewCallback mediaCustomViewCallback;
    private BrowserBarsController barsController;
    private WebView rootWebView;
    private PageFindController pageFind;
    private BrowserPageToolsController pageToolsController;
    private BrowserThemeController themeController;
    private BrowserReaderController readerController;
    private BrowserPrintController printController;

    private boolean quitOnNextBackpress = false;
    private final Handler backExitHandler = new Handler();
    private final Runnable resetBackExitHint = () -> quitOnNextBackpress = false;
    private Handler reload_handler = null;
    private volatile WebApp webapp = null;
    private BackgroundPlaybackController backgroundPlaybackController;
    private String urlOnFirstPageload = "";
    private boolean fallbackToDefaultLongClickBehaviour = false;
    private BrowserSandboxDialogHelper sandboxDialogHelper;
    private BrowserContextMenuController contextMenuController;
    private float pullToRefreshStartY = 0f;
    private boolean pullToRefreshArmed = false;

    private AdFilter adFilter;

    private AdblockProviderApiHelper adblockProviderApiHelper;
    private AdblockLifecycleHelper adblockLifecycleHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adblockLifecycleHelper = new AdblockLifecycleHelper(this);
        adblockLifecycleHelper.trySyncOperation(() -> {
            try {
                adFilter = AdFilter.Companion.get(getApplicationContext());
            } catch (Exception e) {
                try {
                    adFilter = AdFilter.Companion.create(getApplicationContext());
                } catch (Exception ex) {
                    Log.w("WebViewActivity", "Unable to create AdFilter: " + ex.getMessage());
                }
            }
        });
        if (adFilter == null) {
            try {
                adFilter = AdFilter.Companion.get(getApplicationContext());
            } catch (Exception e) {
                try {
                    adFilter = AdFilter.Companion.create(getApplicationContext());
                } catch (Exception ex) {
                    Log.w("WebViewActivity", "Unable to create AdFilter fallback: " + ex.getMessage());
                }
            }
        }
        if (adFilter != null) {
            adblockProviderApiHelper = new AdblockProviderApiHelper(adFilter);
        }
        webappID = getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1);
        EntryPointUtils.entryPointReached(this);
        webapp = DataManager.getInstance().getWebApp(webappID);
        if (webapp == null) {
            // Toast is shown in getWebApp method
            finish();
            return;
        }

        SandboxManager.getInstance().normalizeSandbox(webapp);
        Class<?> targetClass = WebViewLauncher.getWebViewClass(webapp);
        if (!this.getClass().equals(targetClass)) {
            WebViewLauncher.startWebViewInNewProcess(webapp, this);
            finish();
            return;
        }

        if (webapp.isBiometricProtection()) {
            new BiometricPromptHelper(WebViewActivity.this).showPrompt(this::setupWebView, this::finish, getString(R.string.bioprompt_restricted_webapp));
        } else {
            setupWebView();
        }
    }

    private boolean isWebViewSetup = false;

    @SuppressLint("ClickableViewAccessibility")
    private void setupWebView() {
        if (isWebViewSetup) return;
        isWebViewSetup = true;

        boolean hasSandboxing = SandboxManager.isValidContainerId(webapp.getContainerId());
        if (hasSandboxing) {
            try {
                SandboxManager.getInstance().registerWebAppToSandbox(webapp);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        setContentView(R.layout.full_webview);

        if(webapp.isKeepAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        String requestedUrl = getIntent().getStringExtra(WebViewLauncher.EXTRA_TARGET_URL);
        String url = BrowserOriginPolicy.origin(requestedUrl) != null
                ? requestedUrl : webapp.getBaseUrl();

        wv = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);
        setupHermitShell();
        applyThemeIdentity();

        List<AdblockConfig> adblockConfigs = DataManager.getInstance().getSettings().getGlobalWebApp().getAdBlockSettings();
        if (webapp.isUseAdblock() && !adblockConfigs.isEmpty() && adFilter != null && adblockProviderApiHelper != null) {
            wv.setVisibility(View.GONE);
            wv = findViewById(R.id.adblockwebview);
            wv.setVisibility(View.VISIBLE);

            adFilter.setupWebView(wv);
            adblockLifecycleHelper.beforeAdblockOperation(() -> adblockProviderApiHelper.synchronizeAdblockProviderWithSettings(adblockConfigs));

            adFilter.getViewModel().getOnDirty().observe(this, none -> wv.clearCache(false)
            );

            adFilter.getViewModel().getEnabledFilterCount().observe(this, count -> {
                if (count == adblockConfigs.size()) {
                    adblockLifecycleHelper.afterAdblockOperation();
                }
            });
        }

        String fieldName = Stream.of(WebViewActivity.class.getDeclaredFields()).filter(f -> f.getType() == WebView.class).findFirst().orElseThrow(null).getName();
        String uaString = wv.getSettings().getUserAgentString().replace("; " + fieldName, "");
        wv.getSettings().setUserAgentString(uaString);
        defaultUserAgent = uaString;
        if (webapp.isUseCustomUserAgent()) {
            if(webapp.getUserAgent() != null && !webapp.getUserAgent().equals("")) {
                wv.getSettings().setUserAgentString(webapp.getUserAgent().replace("\0", "").replace("\n", "").replace("\r", ""));
            }
        }

        applySystemBars();
        wv.setWebViewClient(new CustomBrowser());
        wv.getSettings().setSafeBrowsingEnabled(webapp.isSafeBrowsing());
        wv.getSettings().setDomStorageEnabled(true);
        wv.getSettings().setDatabaseEnabled(true);
        wv.getSettings().setAllowFileAccess(true);
        wv.getSettings().setAllowContentAccess(true);
        wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
        wv.getSettings().setBlockNetworkLoads(false);
//        wv.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        this.setDarkModeIfNeeded();

        wv.getSettings().setJavaScriptEnabled(webapp.isAllowJs());

        applySandboxCookiePolicy();
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, webapp.isAllowThirdPartyCookies());

        if (webapp.isBlockImages())
            wv.getSettings().setBlockNetworkImage(true);
        wv.getSettings().setTextZoom(webapp.getTextZoom() > 0 ? webapp.getTextZoom() : 100);

        applyDesktopMode(wv);

        CUSTOM_HEADERS = initCustomHeaders(webapp.isSendSavedataRequest());
        downloadController = new BrowserDownloadController(this);
        jsDialogHelper = new BrowserJsDialogHelper(this);
        backgroundPlaybackController = new BackgroundPlaybackController(this, wv, webapp);
        rootWebView = wv;
        wv.getSettings().setSupportMultipleWindows(true);
        wv.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        wv.setWebChromeClient(new CustomWebChromeClient(wv));
        loadURL(wv, url);
        wv.setOnLongClickListener(view -> {
            if (webapp.isFrameless()) return false;
            if(webapp.getAlwaysUseFallbackContextMenu()) return false;
            if(fallbackToDefaultLongClickBehaviour) {
                fallbackToDefaultLongClickBehaviour = false;
                return false;
            }
            WebView.HitTestResult hr = wv.getHitTestResult();
            if (hr != null && (hr.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE
                    || hr.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                lastLongClickedUrl = hr.getExtra();
            } else {
                lastLongClickedUrl = null;
            }
            showWebViewPopupMenu();
            return true;
        });
        updateToolbarContent(wv.getUrl());
        updateToolbarVisibility();
        refreshQuickSettingsState();


        wv.setDownloadListener(this::handleDownload);
        wv.setOnTouchListener(new View.OnTouchListener() {
            private int mode = NONE;
            private float startX;
            private float stopX;
            private float startY;
            private float stopY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (webapp.isPullToRefresh()) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            pullToRefreshStartY = event.getY();
                            pullToRefreshArmed = !wv.canScrollVertically(-1);
                            break;
                        case MotionEvent.ACTION_UP:
                            if (pullToRefreshArmed
                                    && event.getY() - pullToRefreshStartY > TRESHOLD
                                    && !wv.canScrollVertically(-1)) {
                                currently_reloading = true;
                                wv.reload();
                            }
                            pullToRefreshArmed = false;
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            pullToRefreshArmed = false;
                            break;
                    }
                }
                if (webapp.isRequestDesktop())
                    return false;

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                        // This happens when you touch the screen with two fingers
                        mode = SWIPE;
                        // You can also use event.getY(1) or the average of the two
                        startX = event.getX(0);
                        startY = event.getY(0);
                        return true;

                    case MotionEvent.ACTION_POINTER_UP:
                        // This happens when you release the second finger
                        mode = NONE;
                        if (Math.abs(startX - stopX) > TRESHOLD) {
                            if (startX > stopX) {
                                if (event.getPointerCount() == 3 && DataManager.getInstance().getSettings().isThreeFingerMultitouch()) {
                                    WebViewLauncher.startWebView(DataManager.getInstance().getPredecessor(webappID), WebViewActivity.this);
                                    finish();
                                } else if (DataManager.getInstance().getSettings().isTwoFingerMultitouch()) {
                                    if (wv.canGoForward())
                                        wv.goForward();
                                }
                            } else {
                                if (event.getPointerCount() == 3 && DataManager.getInstance().getSettings().isThreeFingerMultitouch()) {
                                    WebViewLauncher.startWebView(DataManager.getInstance().getSuccessor(webappID), WebViewActivity.this);
                                    finish();
                                } else if (DataManager.getInstance().getSettings().isTwoFingerMultitouch())
                                    onBackPressed();

                            }
                            return true;
                        }
                        if (DataManager.getInstance().getSettings().isMultitouchReload() && Math.abs(startY - stopY) > TRESHOLD) {
                            if (stopY > startY) {
                                currently_reloading = true;
                                wv.reload();
                            }
                            return true;
                        }
                    case MotionEvent.ACTION_MOVE:
                        if (mode == SWIPE) {
                            stopX = event.getX(0);
                            stopY = event.getY(0);
                        }
                        return false;
                }
                return false;
            }
        });
    }

    private void setupHermitShell() {
        drawerLayout = findViewById(R.id.browser_drawer_container);
        View windowRoot = findViewById(R.id.browserWindowRoot);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // DrawerLayout positions its children using margins, ignoring its own padding.
        // Apply safe areas to the outer FrameLayout so content and drawer share them.
        ViewCompat.setOnApplyWindowInsetsListener(windowRoot, (view, windowInsets) -> {
            // Hermit's ho0/jo0 window helpers allow content into the camera area.
            // Frameless is website-only: only the keyboard reserves space here.
            Insets safe = webapp != null && webapp.isFrameless() ? Insets.NONE
                    : windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, keyboard.bottom));
            applyPageToolsInsets(windowInsets);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(windowRoot);
        quickSettingsDrawer = findViewById(R.id.quick_settings_drawer);
        drawerLayout.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    updateBrowserDrawerMetrics();
                    updateImmersiveGestureAccess();
                });
        findViewById(R.id.webviewActivity).addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateImmersiveGestureAccess());
        findViewById(R.id.webviewActivity).post(this::updateImmersiveGestureAccess);
        browserToolbarContainer = findViewById(R.id.browser_toolbar_container);
        toolbarTitle = findViewById(R.id.txtToolbarTitle);
        toolbarSubtitle = findViewById(R.id.txtToolbarSubtitle);

        View homeButton = findViewById(R.id.btnToolbarHome);
        homeButton.setOnClickListener(view -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.btnToolbarRefresh).setOnClickListener(view -> reloadCurrentPage());
        findViewById(R.id.btnToolbarQuickSettings).setOnClickListener(view ->
                openQuickSettings());
        View titleCapsule = findViewById(R.id.browserTitleCapsule);
        if (titleCapsule != null) {
            titleCapsule.setOnClickListener(view -> openQuickSettings());
        }
        findViewById(R.id.btnBrowserRestoreControls).setOnClickListener(view ->
                openQuickSettings());
        findViewById(R.id.btnQuickClose).setOnClickListener(view ->
                drawerLayout.closeDrawer(GravityCompat.END));

        pageFind = new PageFindController(findViewById(R.id.browserFindBar), this::updateBrowserContentBounds);
        readerController = new BrowserReaderController(this, new BrowserReaderController.Callback() {
            @Nullable @Override public WebApp getWebApp() { return webapp; }
            @Nullable @Override public WebView getActiveWebView() { return wv; }
            @Override public boolean isMediaShowing() { return mediaCustomView != null; }
            @Override public void onUpdateContentBounds() { updateBrowserContentBounds(); }
            @Override public void onCloseFindBar() { closeFindBar(); }
            @Override public void onNavigateUrl(@NonNull String url) { navigateReaderLink(url); }
        });
        readerController.init(findViewById(R.id.browserWebContent), findViewById(R.id.browserReaderControls));
        pageToolsController = new BrowserPageToolsController(windowRoot, new BrowserPageToolsController.Callback() {
            @Nullable @Override public WebApp getWebApp() { return webapp; }
            @Nullable @Override public WebView getActiveWebView() { return wv; }
            @Nullable @Override public WebView getReaderWebView() { return readerController != null ? readerController.getReaderWebView() : null; }
            @Override public boolean isReaderOpen() { return readerController != null && readerController.isOpen(); }
            @Override public boolean isMediaCustomViewShowing() { return mediaCustomView != null; }
            @Nullable @Override public DrawerLayout getDrawerLayout() { return drawerLayout; }
            @Override public void openReaderMode() { WebViewActivity.this.openReaderMode(); }
            @Override public void copyCurrentUrl() { WebViewActivity.this.copyCurrentUrl(); }
            @Override public void closeDrawer() { closeOpenDrawer(); }
            @Override public void openQuickSettings() { WebViewActivity.this.openQuickSettings(); }
            @Override public void onFindStateChanged() { updateBrowserContentBounds(); }
        });
        pageToolsController.setPageFindController(pageFind);

        initSandboxAndContextMenuControllers();
        initThemeController();
        initPrintController();

        quickSettingsController = new BrowserQuickSettingsController(
                quickSettingsDrawer != null ? quickSettingsDrawer : findViewById(R.id.browserWindowRoot),
                new BrowserQuickSettingsController.Callback() {
                    @NonNull @Override public WebApp getWebApp() { return webapp; }
                    @Nullable @Override public WebView getWebView() { return wv; }
                    @Override public boolean isPageLoading() { return pageLoading; }
                    @Override public void persistQuickSettings(boolean requireReload) { WebViewActivity.this.persistQuickSettings(requireReload); }
                    @Override public void applySystemBars() { WebViewActivity.this.applySystemBars(); }
                    @Override public void applyDynamicSettings() { WebViewActivity.this.applyDynamicSettings(); }
                    @Override public void forceReloadCurrentPage() { WebViewActivity.this.forceReloadCurrentPage(); }
                    @Override public void reloadCurrentPage() { WebViewActivity.this.reloadCurrentPage(); }
                    @Override public void applyDesktopMode() { WebViewActivity.this.applyDesktopMode(); }
                    @Override public void setDarkModeIfNeeded() { WebViewActivity.this.setDarkModeIfNeeded(); }
                    @Override public void updateToolbarVisibility() { WebViewActivity.this.updateToolbarVisibility(); }
                    @Override public void closeDrawer() { closeOpenDrawer(); }
                    @Override public void shareCurrentUrl() { WebViewActivity.this.shareCurrentUrl(); }
                    @Override public void openCurrentUrlExternally() { WebViewActivity.this.openCurrentUrlExternally(); }
                    @Override public void copyCurrentUrl() { WebViewActivity.this.copyCurrentUrl(); }
                    @Override public void openReaderMode() { WebViewActivity.this.openReaderMode(); }
                    @Override public void openFindInPage() { WebViewActivity.this.openFindInPage(); }
                    @Override public void showShortcutDialog() {
                        ShortcutDialogFragment fragment = ShortcutDialogFragment.newInstance(webapp);
                        fragment.show(getSupportFragmentManager(), "QuickShortcut-" + webapp.getID());
                    }
                    @Override public void showSandboxPickerDialog() { WebViewActivity.this.showSandboxPickerDialog(); }
                    @Override public void loadUrl(String target) { loadURL(wv, target); }
                    @Override public String getCurrentPageUrl() { return currentPageUrl(); }
                    @Override public Map<String, String> initCustomHeaders(boolean sendSaveData) { return WebViewActivity.this.initCustomHeaders(sendSaveData); }
                    @Override public void onHeadersUpdated(Map<String, String> headers) { CUSTOM_HEADERS = headers; }
                    @Override public AdFilter getAdFilter() { return adFilter; }
                    @Override public AdblockProviderApiHelper getAdblockProviderApiHelper() { return adblockProviderApiHelper; }
                    @Override public AdblockLifecycleHelper getAdblockLifecycleHelper() { return adblockLifecycleHelper; }
                    @Override public void applyThemeIdentity() { WebViewActivity.this.applyThemeIdentity(); }
                    @Override public void onMoreSettingsClicked() {
                        Intent intent = new Intent(WebViewActivity.this, WebAppSettingsActivity.class);
                        intent.putExtra(Const.INTENT_WEBAPPID, webapp.getID());
                        startActivityForResult(intent, REQUEST_WEBAPP_SETTINGS);
                    }
                });

        positionBrowserToolbar();
        updateToolbarVisibility();
        updateBrowserDrawerMetrics();
        populateQuickSettingsHeader();
        updateLoadingControls();
    }

    private void openQuickSettings() {
        if (webapp == null || webapp.isFrameless() || mediaCustomView != null) return;
        refreshQuickSettingsState();
        drawerLayout.openDrawer(GravityCompat.END);
    }

    private void applyTextZoom(int zoom) {
        if (quickSettingsController != null) {
            quickSettingsController.applyTextZoom(zoom);
        }
    }

    private void updateQuickNavigationState() {
        if (quickSettingsController != null) {
            quickSettingsController.updateNavigationState();
        }
    }

    private void updateLoadingControls() {
        if (quickSettingsController != null) {
            quickSettingsController.updateLoadingControls(findViewById(R.id.btnToolbarRefresh));
        }
    }

    private void setupQuickColorSwatches() {
        if (quickSettingsController != null) {
            quickSettingsController.setupColorSwatches();
        }
    }

    private void populateQuickSettingsHeader() {
        if (quickSettingsController != null) {
            quickSettingsController.populateHeader();
        }
    }

    private void positionBrowserToolbar() {
        if (browserToolbarContainer == null) return;
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                        browserToolbarContainer.getLayoutParams();
        params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        params.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        params.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        if (DataManager.getInstance().getSettings().getToolbarPosition() == 1) {
            params.bottomToBottom =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        } else {
            params.topToTop =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        }
        browserToolbarContainer.setLayoutParams(params);
        updateBrowserContentBounds();
    }

    private void updateToolbarVisibility() {
        if (browserToolbarContainer == null) return;
        boolean frameless = webapp.isFrameless() || mediaCustomView != null;
        if (mediaCustomView != null) closeFindBar();
        if (mediaCustomView != null && readerController != null) readerController.close();
        browserToolbarContainer.setVisibility(frameless ? View.GONE : View.VISIBLE);
        if (drawerLayout != null) {
            drawerLayout.setDrawerLockMode(mediaCustomView != null
                    ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END);
            ((BrowserDrawerLayout) drawerLayout).setToolsOnly(webapp.isFrameless());
        }
        updatePageToolsVisibility();
        View restore = findViewById(R.id.btnBrowserRestoreControls);
        if (restore != null) {
            restore.setVisibility(View.GONE);
        }
        updateBrowserContentBounds();
        updateImmersiveGestureAccess();
    }

    private void updatePageToolsVisibility() {
        if (pageToolsController != null) {
            pageToolsController.updatePageToolsVisibility();
            return;
        }
        boolean toolsOnly = webapp != null && webapp.isFrameless();
        View tools = findViewById(R.id.framelessPageTools);
        if (tools != null) tools.setVisibility(toolsOnly ? View.VISIBLE : View.GONE);
        View scroll = findViewById(R.id.quickSettingsScroll);
        if (scroll != null) scroll.setVisibility(toolsOnly ? View.GONE : View.VISIBLE);
        View nav = findViewById(R.id.quickSettingsNavigation);
        if (nav != null) nav.setVisibility(toolsOnly ? View.GONE : View.VISIBLE);
        View divider = findViewById(R.id.quickSettingsDivider);
        if (divider != null) divider.setVisibility(toolsOnly ? View.GONE : View.VISIBLE);
        applyPageToolsInsets(ViewCompat.getRootWindowInsets(findViewById(R.id.browserWindowRoot)));
    }

    private void applyPageToolsInsets(@Nullable WindowInsetsCompat insets) {
        if (pageToolsController != null) {
            pageToolsController.applyPageToolsInsets(insets);
            return;
        }
        View tools = findViewById(R.id.framelessPageTools);
        if (tools == null) return;
        Insets safe = insets == null ? Insets.NONE : insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        tools.setPadding(dp(16) + safe.left, dp(16) + safe.top,
                dp(16) + safe.right, dp(16) + safe.bottom);
    }

    private void openPageTools() {
        if (pageToolsController != null) {
            pageToolsController.openPageTools();
            return;
        }
        if (webapp == null || mediaCustomView != null || drawerLayout == null) return;
        if (!webapp.isFrameless()) { openQuickSettings(); return; }
        updatePageToolsVisibility();
        drawerLayout.openDrawer(GravityCompat.END);
    }

    private void updateBrowserContentBounds() {
        View content = findViewById(R.id.browserWebContent);
        if (content == null || browserToolbarContainer == null) return;
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) content.getLayoutParams();
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.topToBottom = ConstraintLayout.LayoutParams.UNSET;
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
        if (browserToolbarContainer.getVisibility() == View.VISIBLE) {
            ConstraintLayout.LayoutParams toolbarParams =
                    (ConstraintLayout.LayoutParams) browserToolbarContainer.getLayoutParams();
            boolean isBottom = (toolbarParams.bottomToBottom == ConstraintLayout.LayoutParams.PARENT_ID)
                    || (toolbarParams.bottomToBottom == 0)
                    || (toolbarParams.topToTop == ConstraintLayout.LayoutParams.UNSET
                            && DataManager.getInstance().getSettings().getToolbarPosition() == 1);
            if (isBottom) {
                params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
                params.bottomToTop = R.id.browser_toolbar_container;
            } else {
                params.topToTop = ConstraintLayout.LayoutParams.UNSET;
                params.topToBottom = R.id.browser_toolbar_container;
            }
        }
        if (readerController != null) {
            readerController.adjustReaderControlsBounds(params, findViewById(R.id.browserReaderControls));
        }
        View findBar = findViewById(R.id.browserFindBar);
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            ConstraintLayout.LayoutParams findParams = (ConstraintLayout.LayoutParams) findBar.getLayoutParams();
            findParams.topToTop = params.topToTop;
            findParams.topToBottom = params.topToBottom;
            findBar.setLayoutParams(findParams);
            params.topToTop = ConstraintLayout.LayoutParams.UNSET;
            params.topToBottom = R.id.browserFindBar;
        }
        content.setLayoutParams(params);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && drawerLayout != null && mediaCustomView == null
                && webapp != null) {
            openPageTools();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Reserve a limited right-edge region for DrawerLayout, like Hermit's lower-edge access.
    // This changes gesture routing only: no overlay covers or intercepts website buttons.
    private void updateImmersiveGestureAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || webapp == null) return;
        View content = findViewById(R.id.webviewActivity);
        if (content == null) return;
        if (mediaCustomView != null || content.getWidth() <= 0 || content.getHeight() <= 0) {
            content.setSystemGestureExclusionRects(Collections.emptyList());
            return;
        }
        // Leave the bottom system-navigation area available to Android.
        int bottom = Math.max(0, content.getHeight() - dp(24));
        Rect edge = new Rect(Math.max(0, content.getWidth() - dp(24)),
                Math.max(0, bottom - dp(200)), content.getWidth(), bottom);
        content.setSystemGestureExclusionRects(Collections.singletonList(edge));
    }

    private void updateToolbarContent(@Nullable String url) {
        if (toolbarTitle == null) return;
        String title = webapp.getTitle();
        toolbarTitle.setText(title == null || title.trim().isEmpty() ? displayHost(url) : title);
        String host = displayHost(url);
        toolbarSubtitle.setText(host);
        toolbarSubtitle.setVisibility(host.trim().isEmpty() ? View.GONE : View.VISIBLE);
        View titleCapsule = findViewById(R.id.browserTitleCapsule);
        if (titleCapsule != null) {
            titleCapsule.setContentDescription(
                    (toolbarTitle.getText().toString() + ", " + host).trim());
        }
    }

    private String displayHost(@Nullable String url) {
        return BrowserQuickSettingsController.displayHost(url);
    }

    private void refreshQuickSettingsState() {
        if (quickSettingsController != null) {
            quickSettingsController.refreshState();
        }
    }

    private void updateSandboxBadge() {
        if (quickSettingsController != null) {
            quickSettingsController.updateSandboxBadge();
        }
    }

    private void initSandboxAndContextMenuControllers() {
        if (sandboxDialogHelper == null) {
            sandboxDialogHelper = new BrowserSandboxDialogHelper(this, new BrowserSandboxDialogHelper.Callback() {
                @Nullable @Override public WebApp getWebApp() { return webapp; }
                @Override public void loadUrl(@NonNull String url) { loadURL(wv, url); }
                @Override public void onContainerSwitched(@NonNull WebApp updatedApp) {
                    rememberCurrentPageForRelaunch();
                    if (drawerLayout != null) {
                        drawerLayout.closeDrawer(GravityCompat.END);
                    }
                    WebViewLauncher.startWebViewInNewProcess(updatedApp, WebViewActivity.this);
                    finish();
                }
                @Override public void closeDrawer() { closeOpenDrawer(); }
            });
        }
        if (contextMenuController == null) {
            contextMenuController = new BrowserContextMenuController(this, new BrowserContextMenuController.Callback() {
                @Nullable @Override public WebApp getWebApp() { return webapp; }
                @Nullable @Override public WebView getWebView() { return wv; }
                @Nullable @Override public AdFilter getAdFilter() { return adFilter; }
                @Override public void goBack() { onBackPressed(); }
                @Override public void reload() { if (wv != null) wv.reload(); }
                @Override public void openFindInPage() { WebViewActivity.this.openFindInPage(); }
                @Override public void openQuickSettings() { WebViewActivity.this.openQuickSettings(); }
                @Override public void finishAndRemoveTask() { WebViewActivity.this.finishAndRemoveTask(); }
                @Override public void setFallbackLongClick(boolean fallback) { fallbackToDefaultLongClickBehaviour = fallback; }
                @Override public void showSandboxDialog(@Nullable String linkUrl) { showOpenInSandboxDialog(linkUrl); }
                @Override public void closeDrawer() { closeOpenDrawer(); }
                @Override public void printPage() { WebViewActivity.this.printPage(); }
            });
        }
    }

    private void initPrintController() {
        if (printController == null) {
            printController = new BrowserPrintController(this, new BrowserPrintController.Callback() {
                @Nullable @Override public WebApp getWebApp() { return webapp; }
                @Nullable @Override public WebView getActiveWebView() { return wv; }
            });
        }
    }

    private void printPage() {
        initPrintController();
        if (printController != null) {
            printController.print();
        }
    }

    void showSandboxPickerDialog() {
        initSandboxAndContextMenuControllers();
        sandboxDialogHelper.showSandboxPickerDialog();
    }

    private void showOpenInSandboxDialog(String linkUrl) {
        initSandboxAndContextMenuControllers();
        sandboxDialogHelper.showOpenInSandboxDialog(linkUrl);
    }

    private void openLinkInSandbox(String targetUrl, int targetContainerId) {
        initSandboxAndContextMenuControllers();
        sandboxDialogHelper.openLinkInSandbox(targetUrl, targetContainerId);
    }


    private void persistQuickSettings(boolean requirePageReload) {
        webapp.setOverrideGlobalSettings(true);
        DataManager.getInstance().replaceWebApp(webapp);
        refreshQuickSettingsState();
        if (requirePageReload && wv != null) {
            forceReloadCurrentPage();
        }
    }

    private void applyDesktopMode() {
        applyDesktopMode(wv);
    }

    private void applyDesktopMode(WebView target) {
        if (webapp.isRequestDesktop()) {
            target.getSettings().setUserAgentString(Const.DESKTOP_USER_AGENT);
        } else {
            String userAgent = webapp.isUseCustomUserAgent() && webapp.getUserAgent() != null
                    && !webapp.getUserAgent().trim().isEmpty()
                    ? webapp.getUserAgent() : (defaultUserAgent != null ? defaultUserAgent : "");
            target.getSettings().setUserAgentString(
                    userAgent.replace("\0", "").replace("\n", "").replace("\r", ""));
        }
        target.getSettings().setUseWideViewPort(true);
        target.getSettings().setLoadWithOverviewMode(true);
        boolean zoomEnabled = webapp.isRequestDesktop() || webapp.isEnableZooming();
        target.getSettings().setSupportZoom(zoomEnabled);
        target.getSettings().setBuiltInZoomControls(zoomEnabled);
        target.getSettings().setDisplayZoomControls(false);
    }

    private void applyDynamicSettings() {
        applyDesktopMode();
        wv.getSettings().setJavaScriptEnabled(webapp.isAllowJs());
        wv.getSettings().setBlockNetworkImage(webapp.isBlockImages());
        wv.getSettings().setSafeBrowsingEnabled(webapp.isSafeBrowsing());
        applySandboxCookiePolicy();
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, webapp.isAllowThirdPartyCookies());
        for (WebView parent : popupParents) {
            applyDesktopMode(parent);
            parent.getSettings().setJavaScriptEnabled(webapp.isAllowJs());
            parent.getSettings().setBlockNetworkImage(webapp.isBlockImages());
            parent.getSettings().setSafeBrowsingEnabled(webapp.isSafeBrowsing());
            CookieManager.getInstance().setAcceptThirdPartyCookies(parent, webapp.isAllowThirdPartyCookies());
        }
        CUSTOM_HEADERS = initCustomHeaders(webapp.isSendSavedataRequest());
        if (webapp.isKeepAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (webapp.isUseAdblock() && adFilter != null) {
            adFilter.setupWebView(wv);
            List<AdblockConfig> filters = DataManager.getInstance().getSettings()
                    .getGlobalWebApp().getAdBlockSettings();
            adblockLifecycleHelper.trySyncOperation(() ->
                    adblockProviderApiHelper.synchronizeAdblockProviderWithSettings(filters));
        }
        applySystemBars();
        setDarkModeIfNeeded();
        positionBrowserToolbar();
        updateToolbarVisibility();
        applyThemeIdentity();
        updateToolbarContent(wv.getUrl());
        populateQuickSettingsHeader();
        refreshQuickSettingsState();
    }

    private void applySandboxCookiePolicy() {
        if (webapp != null) {
            CookieManager.getInstance().setAcceptCookie(
                    SandboxPreferences.isCookiesAllowed(webapp.getContainerId()));
        }
    }

    private void reloadCurrentPage() {
        if (readerController != null) readerController.close();
        if (pageLoading) {
            wv.stopLoading();
            setPageLoading(false);
            Toast.makeText(this, R.string.browser_loading_stopped, Toast.LENGTH_SHORT).show();
            return;
        }
        forceReloadCurrentPage();
    }

    private void forceReloadCurrentPage() {
        if (wv == null) return;
        wv.stopLoading();
        currently_reloading = true;
        setPageLoading(true);
        wv.reload();
    }

    private void setPageLoading(boolean loading) {
        pageLoading = loading;
        if (!loading) {
            currently_reloading = false;
            if (progressBar != null) progressBar.setVisibility(View.GONE);
        }
        updateLoadingControls();
    }

    private void updateBrowserDrawerMetrics() {
        if (drawerLayout == null || quickSettingsDrawer == null) return;
        int availableWidth = drawerLayout.getWidth();
        if (availableWidth <= 0) {
            availableWidth = Math.round(getResources().getConfiguration().screenWidthDp
                    * getResources().getDisplayMetrics().density);
        }
        if (availableWidth <= 0) return;
        int inset = Math.min(dp(16), Math.max(0, availableWidth - dp(272)));
        int width = Math.min(dp(360), availableWidth - inset);
        if (width <= 0 || width == appliedDrawerWidth) return;
        DrawerLayout.LayoutParams params =
                (DrawerLayout.LayoutParams) quickSettingsDrawer.getLayoutParams();
        params.width = width;
        quickSettingsDrawer.setLayoutParams(params);
        appliedDrawerWidth = width;
    }

    private boolean closeOpenDrawer() {
        if (drawerLayout == null) return false;
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        }
        return false;
    }

    private void openReaderMode() {
        if (webapp == null || mediaCustomView != null || wv == null || readerController == null) return;
        closeOpenDrawer();
        closeFindBar();
        readerController.open(wv);
    }

    private void navigateReaderLink(String url) {
        if (wv == null || webapp == null || BrowserOriginPolicy.origin(url) == null) return;
        if (webapp.isOpenUrlExternal() && webapp.isAllowAppLaunch() && popupParents.isEmpty()
                && !BrowserOriginPolicy.belongsToSite(url, webapp.getBaseUrl())) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                Toast.makeText(this, R.string.browser_external_internal_fallback, Toast.LENGTH_SHORT).show();
            }
        }
        loadURL(wv, url);
    }

    private void openFindInPage() {
        if (pageToolsController != null) {
            pageToolsController.openFindInPage();
            return;
        }
        if (webapp == null || mediaCustomView != null || wv == null || pageFind == null) return;
        closeOpenDrawer();
        pageFind.open(readerController != null && readerController.isOpen() ? readerController.getReaderWebView() : wv);
    }

    private void closeFindBar() {
        if (pageToolsController != null) {
            pageToolsController.closeFind();
        } else if (pageFind != null) {
            pageFind.close();
        }
    }

    private void copyCurrentUrl() {
        initSandboxAndContextMenuControllers();
        contextMenuController.copyCurrentUrl();
    }

    private void copyUrlToClipboard(String url) {
        initSandboxAndContextMenuControllers();
        contextMenuController.copyUrlToClipboard(url);
    }

    private void shareCurrentUrl() {
        initSandboxAndContextMenuControllers();
        contextMenuController.shareCurrentUrl();
    }

    private void openCurrentUrlExternally() {
        initSandboxAndContextMenuControllers();
        contextMenuController.openCurrentUrlExternally();
    }

    private String currentPageUrl() {
        initSandboxAndContextMenuControllers();
        return contextMenuController.currentPageUrl();
    }

    private int dp(int value) {
        return Math.round(value * Resources.getSystem().getDisplayMetrics().density);
    }

    private void initThemeController() {
        if (themeController == null) {
            themeController = new BrowserThemeController(this, new BrowserThemeController.Callback() {
                @Nullable @Override public WebApp getWebApp() { return webapp; }
                @Nullable @Override public WebView getActiveWebView() { return wv; }
                @Nullable @Override public ProgressBar getProgressBar() { return progressBar; }
                @Nullable @Override public View getTitleCapsule() { return findViewById(R.id.browserTitleCapsule); }
                @Nullable @Override public View getContainerDot() { return findViewById(R.id.toolbarContainerDot); }
                @Override public void setLocalNightMode(int mode) { getDelegate().setLocalNightMode(mode); }
            });
        }
    }

    private void applyThemeIdentity() {
        initThemeController();
        themeController.applyThemeIdentity();
    }

    @SuppressLint("RequiresFeature")
    private void setDarkModeIfNeeded() {
        initThemeController();
        themeController.setDarkModeIfNeeded();
    }

    private void showWebViewPopupMenu() {
        if (webapp == null || webapp.isFrameless()) return;
        initSandboxAndContextMenuControllers();
        View center = findViewById(R.id.anchorCenterScreen);
        if (center != null) {
            contextMenuController.showContextMenu(center, lastLongClickedUrl);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateBrowserDrawerMetrics();
        if (wv != null) updateQuickNavigationState();
        this.setDarkModeIfNeeded();
    }

    @Override
    @SuppressLint("MissingSuperCall") // Media, drawer, page history, popup parent, then exit.
    public void onBackPressed() {
        initBarsController();
        if (barsController.handleBackPressed()) {
            return;
        }
        if (closeOpenDrawer()) return;
        if (pageToolsController != null && pageToolsController.handleBackPressed()) {
            return;
        }
        if (pageFind != null && pageFind.isOpen()) {
            pageFind.close();
            return;
        }
        if (wv == null || webapp == null) {
            finish();
            return;
        }
        if (readerController != null && readerController.handleBackPressed()) return;
        if(wv.canGoBack()) {
            quitOnNextBackpress = false;
            backExitHandler.removeCallbacks(resetBackExitHint);
            wv.goBack();
            return;
        }
        if (!popupParents.isEmpty()) {
            closePopup(wv);
            return;
        }

        if (webapp.isFrameless()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        if(quitOnNextBackpress) {
            quitOnNextBackpress = false;
            backExitHandler.removeCallbacks(resetBackExitHint);
            moveTaskToBack(true);
            return;
        }

        quitOnNextBackpress = true;
        backExitHandler.removeCallbacks(resetBackExitHint);
        backExitHandler.postDelayed(resetBackExitHint, BACK_EXIT_HINT_TIMEOUT_MS);
        Toast.makeText(this, R.string.browser_press_back_again, Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onResume() {
        super.onResume();
        int new_id = getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1);

        if (new_id != webappID) {
            WebApp new_webapp = DataManager.getInstance().getWebApp(new_id);
            if (new_webapp != null) {
                WebViewLauncher.startWebViewInNewProcess(new_webapp, this);
            }
            finish();
            return;
        }
        if (wv == null || webapp == null) return;

        WebApp latestSettings = DataManager.getInstance().getWebApp(webappID);
        if (latestSettings == null) {
            finish();
            return;
        }
        Gson gson = new Gson();
        SandboxManager.getInstance().normalizeSandbox(latestSettings);
        if (webapp.getContainerId() != latestSettings.getContainerId()) {
            rememberCurrentPageForRelaunch();
            WebViewLauncher.startWebViewInNewProcess(latestSettings, this);
            finish();
            return;
        }
        boolean settingsChanged = !gson.toJsonTree(webapp).equals(gson.toJsonTree(latestSettings));
        boolean requiresReload = webapp.isRequestDesktop() != latestSettings.isRequestDesktop()
                || webapp.isAllowJs() != latestSettings.isAllowJs()
                || webapp.isBlockImages() != latestSettings.isBlockImages()
                || webapp.isUseCustomUserAgent() != latestSettings.isUseCustomUserAgent()
                || !Objects.equals(webapp.getUserAgent(), latestSettings.getUserAgent())
                || webapp.isUseAdblock() != latestSettings.isUseAdblock()
                || webapp.isBlockThirdPartyRequests() != latestSettings.isBlockThirdPartyRequests()
                || webapp.isSendSavedataRequest() != latestSettings.isSendSavedataRequest()
                || webapp.isUserscriptEnabled() != latestSettings.isUserscriptEnabled()
                || !Objects.equals(webapp.getUserscriptCode(), latestSettings.getUserscriptCode());
        webapp = latestSettings;
        applySandboxCookiePolicy();
        wv.onResume();
        wv.resumeTimers();
        if (backgroundPlaybackController != null) {
            backgroundPlaybackController.onResume(webapp);
        }
        if (settingsChanged) {
            applyDynamicSettings();
            if (requiresReload) forceReloadCurrentPage();
        } else {
            setDarkModeIfNeeded();
            applySystemBars();
            positionBrowserToolbar();
            updateToolbarVisibility();
            updateToolbarContent(wv.getUrl());
            refreshQuickSettingsState();
        }

        
        if(webapp.isBiometricProtection()) {
            View fullActivityView = findViewById(R.id.webviewActivity);
            fullActivityView.setVisibility(View.GONE);
            new BiometricPromptHelper(WebViewActivity.this).showPrompt(() -> fullActivityView.setVisibility(View.VISIBLE), () -> finish(), getString(R.string.bioprompt_restricted_webapp));
        }
        if (webapp.isAutoreload()) {
            if (reload_handler != null) reload_handler.removeCallbacksAndMessages(null);
            reload_handler = new Handler();
            reload();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        backExitHandler.removeCallbacks(resetBackExitHint);
        quitOnNextBackpress = false;
        if (wv == null || webapp == null) return;
        if (contextMenuController != null) contextMenuController.dismissMenu();

        boolean keepPlaying = backgroundPlaybackController != null
                && backgroundPlaybackController.onPause();
        if (!keepPlaying) {
            wv.evaluateJavascript("document.querySelectorAll('audio').forEach(x => x.pause());document.querySelectorAll('video').forEach(x => x.pause());", null);
            wv.onPause();
        }
        if (!keepPlaying
                && (backgroundPlaybackController == null
                || backgroundPlaybackController.shouldPauseWebViewTimers())) {
            wv.pauseTimers();
        }
        if (!keepPlaying) {
            if (webapp.isClearCache() || DataManager.getInstance().getSettings().isClearCache())
                wv.clearCache(true);
        }

        if (reload_handler != null) {
            reload_handler.removeCallbacksAndMessages(null);
            Log.d("CLEANUP", "Stopped reload handler");
        }
    }

    @Override
    public void finish() {
        if (backgroundPlaybackController != null) {
            backgroundPlaybackController.releasePlayback();
        }
        super.finish();
    }

    @Override
    protected void onDestroy() {
        closeFindBar();
        if (readerController != null) readerController.destroy();
        backExitHandler.removeCallbacksAndMessages(null);
        if (reload_handler != null) reload_handler.removeCallbacksAndMessages(null);
        if (contextMenuController != null) contextMenuController.dismissMenu();
        hideMediaCustomView();
        cancelMediaPermission(false);
        handleGeoPermissionCallback(false);
        completeFileChooser(null);
        if (jsDialogHelper != null) jsDialogHelper.dismissActiveDialog();
        dl_request = null;
        if (backgroundPlaybackController != null) {
            backgroundPlaybackController.destroy();
            backgroundPlaybackController = null;
        }
        if (isWebViewSetup && wv != null) {
            wv.stopLoading();
            wv.setWebChromeClient(null);
            wv.setWebViewClient(null);
            wv.destroy();
            wv = null;
        }
        WebView normalWv = findViewById(R.id.webview);
        if (normalWv != null && normalWv != wv) {
            normalWv.stopLoading();
            normalWv.setWebChromeClient(null);
            normalWv.setWebViewClient(null);
            normalWv.destroy();
        }
        WebView adblockWv = findViewById(R.id.adblockwebview);
        if (adblockWv != null && adblockWv != wv) {
            adblockWv.stopLoading();
            adblockWv.setWebChromeClient(null);
            adblockWv.setWebViewClient(null);
            adblockWv.destroy();
        }
        for (WebView parent : popupParents) {
            parent.stopLoading();
            parent.setWebChromeClient(null);
            parent.setWebViewClient(null);
            parent.destroy();
        }
        popupParents.clear();
        super.onDestroy();
    }

    private void reload() {
        long intervalSeconds = Math.max(5L, (long) webapp.getTimeAutoreload());
        reload_handler.postDelayed(() -> {
            currently_reloading = true;
            if (wv != null) {
                wv.reload();
            }
            reload();
        }, intervalSeconds * 1000L);
    }

    public WebView getWebView() {
        return wv;
    }

    private void rememberCurrentPageForRelaunch() {
        if (wv != null && BrowserOriginPolicy.origin(wv.getUrl()) != null) {
            getIntent().putExtra(WebViewLauncher.EXTRA_TARGET_URL, wv.getUrl());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int id = intent.getIntExtra(Const.INTENT_WEBAPPID, -1);
        WebApp latest = DataManager.getInstance().getWebApp(id);
        if (latest == null) {
            finish();
            return;
        }
        if (webapp == null || id != webappID
                || latest.getContainerId() != webapp.getContainerId()) {
            if (id == webappID
                    && BrowserOriginPolicy.origin(intent.getStringExtra(WebViewLauncher.EXTRA_TARGET_URL)) == null) {
                rememberCurrentPageForRelaunch();
            }
            WebViewLauncher.startWebViewInNewProcess(latest, this);
            finish();
            return;
        }
        String target = intent.getStringExtra(WebViewLauncher.EXTRA_TARGET_URL);
        if (wv != null && BrowserOriginPolicy.origin(target) != null
                && !target.equals(wv.getUrl())) {
            hideMediaCustomView();
            loadURL(wv, target);
        }
    }

    private boolean createPopup(WebView parent, boolean userGesture, Message resultMsg) {
        if (!userGesture || parent != wv || popupParents.size() >= 4
                || resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)
                || isFinishing() || isDestroyed()) {
            Toast.makeText(this, R.string.browser_popup_blocked, Toast.LENGTH_LONG).show();
            return false;
        }
        hideMediaCustomView();
        cancelMediaPermission(false);
        handleGeoPermissionCallback(false);
        completeFileChooser(null);
        WebView child = new WebView(this);
        WebSettings source = parent.getSettings();
        WebSettings settings = child.getSettings();
        settings.setJavaScriptEnabled(source.getJavaScriptEnabled());
        settings.setDomStorageEnabled(source.getDomStorageEnabled());
        settings.setDatabaseEnabled(source.getDatabaseEnabled());
        settings.setUserAgentString(source.getUserAgentString());
        settings.setSafeBrowsingEnabled(source.getSafeBrowsingEnabled());
        settings.setBlockNetworkImage(source.getBlockNetworkImage());
        settings.setUseWideViewPort(source.getUseWideViewPort());
        settings.setLoadWithOverviewMode(source.getLoadWithOverviewMode());
        settings.setSupportZoom(source.supportZoom());
        settings.setBuiltInZoomControls(source.getBuiltInZoomControls());
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(child, webapp.isAllowThirdPartyCookies());
        child.setWebViewClient(new CustomBrowser());
        child.setWebChromeClient(new CustomWebChromeClient(child));
        child.setDownloadListener(this::handleDownload);
        if (webapp.isUseAdblock() && adFilter != null) adFilter.setupWebView(child);
        closeFindBar();
        if (readerController != null) readerController.close();
        popupParents.add(parent);
        parent.setVisibility(View.GONE);
        FrameLayout content = findViewById(R.id.browserWebContent);
        // Keep the page below the progress indicator and preserve the native opener relation.
        content.addView(child, 0, new FrameLayout.LayoutParams(-1, -1));
        wv = child;
        setDarkModeIfNeeded();
        child.requestFocus();
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(child);
        resultMsg.sendToTarget();
        Toast.makeText(this, R.string.browser_popup_opened, Toast.LENGTH_SHORT).show();
        updateQuickNavigationState();
        return true;
    }

    private void closePopup(WebView window) {
        if (popupParents.isEmpty()) return;
        closeFindBar();
        if (readerController != null) readerController.close();
        // A parent may call close while its own child is still open.
        int index = popupParents.indexOf(window);
        if (window != wv && index < 1) return;
        do {
            hideMediaCustomView();
            cancelMediaPermission(false);
            handleGeoPermissionCallback(false);
            completeFileChooser(null);
            WebView child = wv;
            wv = popupParents.remove(popupParents.size() - 1);
            ((FrameLayout) findViewById(R.id.browserWebContent)).removeView(child);
            child.stopLoading();
            child.setWebChromeClient(null);
            child.setWebViewClient(null);
            child.destroy();
            if (child == window) break;
        } while (!popupParents.isEmpty());
        wv.setVisibility(View.VISIBLE);
        wv.requestFocus();
        setPageLoading(wv.getProgress() < 100);
        updateToolbarContent(wv.getUrl());
        updateQuickNavigationState();
    }

    private void handleDownload(String url, String userAgent, String contentDisposition,
                                String mimeType, long contentLength) {
        if (downloadController == null) {
            downloadController = new BrowserDownloadController(this);
        }
        downloadController.handleDownload(url, userAgent, contentDisposition, mimeType, contentLength, webapp);
        dl_request = downloadController.getPendingRequest();
    }

    private void enqueueDownload(DownloadManager.Request request) {
        if (downloadController == null) {
            downloadController = new BrowserDownloadController(this);
        }
        downloadController.enqueueDownload(request);
    }

    private Map<String, String> initCustomHeaders(boolean save_data) {
        return BrowserHeadersPolicy.getCustomHeaders(save_data);
    }

    private void loadURL(final WebView view, final String url) {
        final WebApp webApp = webapp;
        final String sanitizedUrl = webApp.isStripTrackingParams()
                ? UrlSanitizer.sanitizeTrackingParameters(url)
                : url;
        final String targetUrl = sanitizedUrl == null ? url : sanitizedUrl;
        if (targetUrl.regionMatches(true, 0, "http://", 0, 7) && !webApp.isAllowHttp()) {
            final MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(WebViewActivity.this);

            builder.setTitle(getString(R.string.no_https_dialog_title));
            builder.setMessage(getString(R.string.no_https_dialog_msg));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(getString(R.string.no_https_dialog_accept), (dialog, id) -> {
                webApp.setAllowHttp(true);
                webApp.setOverrideGlobalSettings(true);
                DataManager.getInstance().replaceWebApp(webApp);
                view.loadUrl(targetUrl, CUSTOM_HEADERS);
            });
            builder.setNegativeButton(getString(android.R.string.cancel), (dialog, id) -> finish());
            final androidx.appcompat.app.AlertDialog dialog = builder.create();
            dialog.show();
        } else
            view.loadUrl(targetUrl, CUSTOM_HEADERS);

    }
    private void initBarsController() {
        if (barsController == null) {
            barsController = new BrowserBarsController(this, new BrowserBarsController.Callback() {
                @Nullable @Override public WebApp getWebApp() { return webapp; }
                @Nullable @Override public WebView getActiveWebView() { return wv; }
                @Override
                public void onCustomViewStateChanged(@Nullable View view, @Nullable WebChromeClient.CustomViewCallback callback) {
                    mediaCustomView = view;
                    mediaCustomViewCallback = callback;
                }
                @Override public void closeOpenDrawer() { WebViewActivity.this.closeOpenDrawer(); }
                @Override public void updateToolbarVisibility() { WebViewActivity.this.updateToolbarVisibility(); }
                @Override
                public void requestApplyInsetsOnDrawer() {
                    if (drawerLayout != null) ViewCompat.requestApplyInsets(drawerLayout);
                }
                @Nullable @Override public View getMediaCustomView() { return mediaCustomView; }
            });
        }
    }

    private void applySystemBars() {
        initBarsController();
        barsController.applySystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        initBarsController();
        barsController.onWindowFocusChanged(hasFocus);
    }

    private void hideSystemBars() {
        initBarsController();
        barsController.hideSystemBars();
    }

    private void showSystemBars() {
        initBarsController();
        barsController.showSystemBars();
    }

    private void hideMediaCustomView() {
        initBarsController();
        barsController.hideMediaCustomView();
    }

    private void initPermissionsController() {
        if (permissionsController == null) {
            permissionsController = new BrowserPermissionsController(this, new BrowserPermissionsController.Callback() {
                @Nullable @Override public WebApp getWebApp() { return webapp; }
                @Nullable @Override public WebView getActiveWebView() { return wv; }
                @Override public boolean isFinishingOrDestroyed() { return isFinishing() || isDestroyed(); }
                @Override public void onStateChanged(
                        @Nullable BrowserPermissionsController.PendingMediaPermission pendingMedia,
                        @Nullable ValueCallback<Uri[]> fileCallback,
                        int activeFileCode) {
                    pendingMediaPermission = pendingMedia;
                    filePathCallback = fileCallback;
                    activeFileRequestCode = activeFileCode;
                }
            });
        }
    }

    private void cancelMediaPermission(boolean canceledByWebView) {
        initPermissionsController();
        permissionsController.cancelMediaPermission(canceledByWebView);
    }

    private void handleGeoPermissionCallback(boolean allow) {
        initPermissionsController();
        permissionsController.handleGeoPermissionCallback(allow);
    }

    private void completeFileChooser(@Nullable Uri[] result) {
        initPermissionsController();
        permissionsController.completeFileChooser(result);
    }

    private int allocateBrowserRequestCode() {
        initPermissionsController();
        return permissionsController.allocateBrowserRequestCode();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        initPermissionsController();
        if (permissionsController.handleRequestPermissionsResult(requestCode, permissions, grantResults)) {
            return;
        }
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        if (requestCode == Const.PERMISSION_RC_STORAGE) {
            if (downloadController == null) {
                downloadController = new BrowserDownloadController(this);
            }
            downloadController.setPendingRequest(dl_request);
            downloadController.onPermissionsGranted(requestCode, list);
            dl_request = null;
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        if (requestCode == Const.PERMISSION_RC_STORAGE) {
            if (downloadController == null) {
                downloadController = new BrowserDownloadController(this);
            }
            downloadController.onPermissionsDenied(requestCode, list);
            dl_request = null;
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {

        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_WEBAPP_SETTINGS) {
            if (resultCode == RESULT_OK && intent != null) {
                String json = intent.getStringExtra(WebAppSettingsActivity.EXTRA_UPDATED_WEBAPP);
                if (json != null) {
                    try {
                        WebApp updated = new Gson().fromJson(json, WebApp.class);
                        if (updated != null && updated.getID() == webappID) {
                            // The provider snapshot (including its baseline) remains authoritative.
                            Log.d("SETTINGS", "Settings editor saved this Lite App");
                        }
                    } catch (com.google.gson.JsonParseException e) {
                        Log.w("SETTINGS", "Unable to read updated WebApp settings", e);
                    }
                }
            }
            return;
        }
        initPermissionsController();
        if (permissionsController.handleActivityResult(requestCode, resultCode, intent)) {
            return;
        }
    }


    public BrowserJsDialogHelper getJsDialogHelper() {
        if (jsDialogHelper == null) {
            jsDialogHelper = new BrowserJsDialogHelper(this);
        }
        return jsDialogHelper;
    }

    private class CustomWebChromeClient extends android.webkit.WebChromeClient {
        private final WebView owner;

        CustomWebChromeClient(WebView owner) {
            this.owner = owner;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            return getJsDialogHelper().handleJsAlert(view, url, message, result);
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            return getJsDialogHelper().handleJsConfirm(view, url, message, result);
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            return getJsDialogHelper().handleJsPrompt(view, url, message, defaultValue, result);
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            return getJsDialogHelper().handleJsBeforeUnload(view, url, message, result);
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            return createPopup(view, isUserGesture, resultMsg);
        }

        @Override
        public void onCloseWindow(WebView window) {
            closePopup(window);
        }

        @Override
        public void onGeolocationPermissionsHidePrompt() {
            initPermissionsController();
            permissionsController.onGeolocationPermissionsHidePrompt(owner);
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            super.onReceivedIcon(view, icon);
            if (view == rootWebView && icon != null && webapp != null) {
                Bitmap existing = ShortcutIconUtils.loadFavicon(WebViewActivity.this, webapp.getID());
                if (existing == null || (existing.getWidth() < 64 && icon.getWidth() >= existing.getWidth())) {
                    ShortcutIconUtils.saveFavicon(WebViewActivity.this, webapp.getID(), icon);
                    populateQuickSettingsHeader();
                }
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (view == rootWebView && title != null && !title.trim().isEmpty() && webapp != null) {
                String storedTitle = trimTrailingSlashes(webapp.getTitle());
                String cleanBase = trimTrailingSlashes(
                        webapp.getBaseUrl()
                                .replace("http://", "")
                                .replace("https://", "")
                                .replace("www.", "")
                                .trim());
                String cleanUrl = trimTrailingSlashes(webapp.getBaseUrl().trim());
                if (storedTitle.isEmpty() ||
                    storedTitle.equalsIgnoreCase(cleanBase) ||
                    storedTitle.equalsIgnoreCase(cleanUrl)) {
                    webapp.setTitle(title.trim());
                    DataManager.getInstance().replaceWebApp(webapp);
                    updateToolbarContent(view.getUrl());
                    populateQuickSettingsHeader();
                }
            }
        }

        private String trimTrailingSlashes(@Nullable String value) {
            if (value == null) return "";
            int end = value.length();
            while (end > 0 && value.charAt(end - 1) == '/') {
                end--;
            }
            return value.substring(0, end).trim();
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView, ValueCallback<Uri[]> pFilePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            initPermissionsController();
            return permissionsController.onShowFileChooser(webView, pFilePathCallback, fileChooserParams);
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            final Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawARGB(0, 0, 0, 0);
            return bitmap;
        }

        @Override
        public void onHideCustomView() {
            hideMediaCustomView();
        }

        @Override
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            initBarsController();
            barsController.onShowCustomView(view, callback, owner);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            initPermissionsController();
            permissionsController.onPermissionRequest(request, owner);
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            initPermissionsController();
            permissionsController.onPermissionRequestCanceled(request);
        }

        public void onProgressChanged(WebView view, int progress) {
            if (view != wv) return;
            if (progress == 100) updateQuickNavigationState();

            if (DataManager.getInstance().getSettings().isShowProgressbar() || currently_reloading) {
                if (progressBar.getVisibility() == ProgressBar.GONE && progress < 100) {
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                }

                progressBar.setProgress(progress);
                progressBar.setContentDescription(
                        getString(R.string.browser_loading_percent, progress));

                if (progress == 100) {
                    setPageLoading(false);
                }
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(final String origin,
                                                       final GeolocationPermissions.Callback callback) {
            initPermissionsController();
            permissionsController.onGeolocationPermissionsShowPrompt(origin, callback, owner);
        }
    }

    private void showHttpAuthDialog(final HttpAuthHandler handler, String host, String realm) {
        DialogHttpAuthBinding localBinding = DialogHttpAuthBinding.inflate(LayoutInflater.from(this));
        new AlertDialog.Builder(this)
                .setView(localBinding.getRoot())
                .setTitle(getString(R.string.http_auth_title))
                .setMessage(getString(R.string.enter_http_auth_credentials, realm, host))
                .setPositiveButton(getString(R.string.ok), (dialog, whichButton) -> {
                    String username = localBinding.username.getText().toString();
                    String password = localBinding.password.getText().toString();

                    handler.proceed(username, password);

                })
                .setNegativeButton(getString(R.string.cancel), (dialog, whichButton) -> handler.cancel())
                .show();
    }

    private class CustomBrowser extends WebViewClient {

        private final AdFilter adFilter = WebViewActivity.this.adFilter;

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            showHttpAuthDialog(handler, host, realm);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (webapp.isUseAdblock() && webapp.isAllowJs()
                    && com.cylonid.nativealpha.browser.YouTubeAdBlock.supports(url)) {
                view.evaluateJavascript(com.cylonid.nativealpha.browser.YouTubeAdBlock.SCRIPT, null);
            }
            injectPageSettings(view);
            injectUserscriptIfAllowed(view);
            if (backgroundPlaybackController != null && view == rootWebView) {
                backgroundPlaybackController.onPageFinished(url);
            }
            if (view == wv) {
                setPageLoading(false);
                updateToolbarContent(url);
                updateQuickNavigationState();
            }
            super.onPageFinished(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (view == wv) {
                closeFindBar();
                if (readerController != null) readerController.close();
                cancelMediaPermission(false);
                handleGeoPermissionCallback(false);
                completeFileChooser(null);
                if (jsDialogHelper != null) jsDialogHelper.dismissActiveDialog();
                setPageLoading(true);
                updateToolbarContent(url);
                updateQuickNavigationState();
            }
            if (backgroundPlaybackController != null && view == rootWebView) {
                backgroundPlaybackController.onPageStarted(url);
            }
            if (webapp.isUseAdblock() && webapp.isAllowJs()
                    && com.cylonid.nativealpha.browser.YouTubeAdBlock.supports(url)) {
                view.evaluateJavascript(com.cylonid.nativealpha.browser.YouTubeAdBlock.SCRIPT, null);
            }
            if (webapp.isUseAdblock() && adFilter != null) adFilter.performScript(view, url);
            super.onPageStarted(view, url, favicon);
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if(urlOnFirstPageload.equals("")) urlOnFirstPageload = request.getUrl().toString();

            if (webapp.isBlockThirdPartyRequests() && !request.isForMainFrame()
                    && !BrowserOriginPolicy.belongsToSite(request.getUrl().toString(), webapp.getBaseUrl())) {
                return new WebResourceResponse("text/plain", "utf-8", null);
            }

            if (webapp.isUseAdblock()) {
                String reqUrl = request.getUrl().toString();
                if (com.cylonid.nativealpha.browser.YouTubeAdBlock.shouldIntercept(reqUrl)) {
                    return com.cylonid.nativealpha.browser.YouTubeAdBlock.createEmptyResponse();
                }
                if (adFilter != null) {
                    FilterResult filterResult = adFilter.shouldIntercept(view, request);
                    if (filterResult != null && filterResult.getResourceResponse() != null) {
                        return filterResult.getResourceResponse();
                    }
                }
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
            // This option is hidden in "expert settings"
            if (webapp.isIgnoreSslErrors()) {
                handler.proceed();
                return;
            }
            BrowserSecurityPolicy.showSslErrorDialog(WebViewActivity.this, handler, error);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            runOnUiThread(() -> setDarkModeIfNeeded());
            String url = request.getUrl().toString();
            if (webapp.isStripTrackingParams()) {
                String sanitizedUrl = UrlSanitizer.sanitizeTrackingParameters(url);
                if (sanitizedUrl != null) {
                    url = sanitizedUrl;
                }
            }

            Uri targetUri = Uri.parse(url);
            String scheme = targetUri.getScheme();
            if (!BrowserSecurityPolicy.isWebScheme(scheme)) {
                if (!request.isForMainFrame()) return true;
                if (!webapp.isAllowAppLaunch() || !BrowserSecurityPolicy.isExternalSchemeDispatchable(scheme)) {
                    Toast.makeText(
                            WebViewActivity.this,
                            R.string.external_app_launch_blocked,
                            Toast.LENGTH_SHORT
                    ).show();
                    return true;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, targetUri));
                } catch (Exception e) {
                    Toast.makeText(
                            WebViewActivity.this,
                            R.string.external_app_not_available,
                            Toast.LENGTH_SHORT
                    ).show();
                }
                return true;
            }

            if (!request.isForMainFrame()) return false;
            if (BrowserSecurityPolicy.shouldOpenExternally(webapp, url, !popupParents.isEmpty())) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                } catch (ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(WebViewActivity.this, R.string.browser_external_internal_fallback,
                            Toast.LENGTH_SHORT).show();
                }
            }
            // Let WebView retain POST, redirects and history unless the URL really changes.
            if ("GET".equalsIgnoreCase(request.getMethod())
                    && (!url.equals(request.getUrl().toString())
                    || ("http".equalsIgnoreCase(scheme) && !webapp.isAllowHttp()))) {
                loadURL(view, url);
                return true;
            }
            return false;
        }

        private void injectPageSettings(WebView view) {
            if (!webapp.isAllowJs()) return;
            String desktopScript = webapp.isRequestDesktop() ? """
                    if (document.documentElement.clientWidth < 1200) {
                        var viewport = document.querySelector('meta[name="viewport"]');
                        if (!viewport && document.head) {
                            viewport = document.createElement('meta');
                            viewport.name = 'viewport';
                            document.head.appendChild(viewport);
                        }
                        if (viewport) {
                            viewport.content = 'width=1200, initial-scale=' +
                                (document.documentElement.clientWidth / 1200);
                        }
                    }
                    """ : "";
            String backgroundPlaybackScript = webapp.isAllowMediaPlaybackInBackground() ?
                    "document.addEventListener('visibilitychange',function(event){event.stopImmediatePropagation();},true);" : "";
            view.evaluateJavascript("(function(){"
                    + "if(window.__nativeAlphaPageSettingsApplied)return;"
                    + "window.__nativeAlphaPageSettingsApplied=true;"
                    + backgroundPlaybackScript
                    + desktopScript + "})();", null);
        }

        private void injectUserscriptIfAllowed(WebView view) {
            String script = webapp.getUserscriptCode();
            if (!webapp.isUserscriptEnabled() || script == null || script.trim().isEmpty()) {
                return;
            }
            String lower = script.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("@require") || lower.contains("@resource")) {
                return;
            }
            java.util.regex.Matcher grantMatcher =
                    java.util.regex.Pattern.compile("@grant\\s+(\\S+)").matcher(lower);
            while (grantMatcher.find()) {
                String grantVal = grantMatcher.group(1);
                if (!"none".equals(grantVal)) {
                    return;
                }
            }
            String wrappedScript = "(function(){ 'use strict'; "
                    + "if(window.__nativeAlphaUserscriptApplied)return;"
                    + "window.__nativeAlphaUserscriptApplied=true;" + script + "\n})();";
            view.evaluateJavascript(wrappedScript, null);
        }


    }
}


