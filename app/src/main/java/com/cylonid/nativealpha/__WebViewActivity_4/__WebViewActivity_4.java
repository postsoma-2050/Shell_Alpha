package com.cylonid.nativealpha;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
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
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
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
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.ColorPalette;
import com.cylonid.nativealpha.util.DateUtils;
import com.cylonid.nativealpha.util.EntryPointUtils;
import com.cylonid.nativealpha.util.LocaleUtils;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.cylonid.nativealpha.util.ShortcutIconUtils;
import com.cylonid.nativealpha.util.UrlSanitizer;
import com.cylonid.nativealpha.util.Utility;
import com.cylonid.nativealpha.util.WebViewLauncher;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.edsuns.adfilter.AdFilter;
import io.github.edsuns.adfilter.Filter;
import pub.devrel.easypermissions.EasyPermissions;

import static com.cylonid.nativealpha.util.Const.CODE_OPEN_FILE;

public class __WebViewActivity_4 extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

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
    private TextView quickSettingsTitle;
    private TextView quickSettingsHost;
    private TextView quickSettingsSandbox;
    private ImageView quickSettingsFavicon;
    private LinearLayout quickColorSwatches;
    private MaterialSwitch quickOverrideSwitch;
    private boolean updatingQuickSettings;
    private boolean pageLoading;
    private String defaultUserAgent;
    private boolean currently_reloading = true;
    private GeolocationPermissions.Callback mGeoPermissionRequestCallback = null;
    private String mGeoPermissionRequestOrigin = null;
    private DownloadManager.Request dl_request = null;
    private Map<String, String> CUSTOM_HEADERS;
    protected ValueCallback<Uri[]> filePathCallback;

    private boolean quitOnNextBackpress = false;
    private final Handler backExitHandler = new Handler();
    private final Runnable resetBackExitHint = () -> quitOnNextBackpress = false;
    private Handler reload_handler = null;
    private volatile WebApp webapp = null;
    private BackgroundPlaybackController backgroundPlaybackController;
    private WebApp settingsFromEditor;
    private String lastLoadedSettingsJson;
    private String urlOnFirstPageload = "";
    private boolean fallbackToDefaultLongClickBehaviour = false;
    private PopupMenu mPopupMenu = null;
    private float pullToRefreshStartY = 0f;
    private boolean pullToRefreshArmed = false;

    private AdFilter adFilter;

    private AdblockProviderApiHelper adblockProviderApiHelper;
    private AdblockLifecycleHelper adblockLifecycleHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adblockLifecycleHelper = new AdblockLifecycleHelper(this);
        adblockLifecycleHelper.trySyncOperation(() -> adFilter = AdFilter.Companion.get(getApplicationContext()));

        adblockProviderApiHelper = new AdblockProviderApiHelper(adFilter);
        webappID = getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1);
        EntryPointUtils.entryPointReached(this);
        webapp = DataManager.getInstance().getWebApp(webappID);
        if (webapp == null) {
            // Toast is shown in getWebApp method
            finish();
        } else {
            if (webapp.isBiometricProtection()) {
                new BiometricPromptHelper(__WebViewActivity_4.this).showPrompt(this::setupWebView, this::finish, getString(R.string.bioprompt_restricted_webapp));
            } else {
                setupWebView();
            }
        }
    }

    private boolean isWebViewSetup = false;

    @SuppressLint("ClickableViewAccessibility")
    private void setupWebView() {
        if (isWebViewSetup) return;
        isWebViewSetup = true;

        String processName = Application.getProcessName();
        String packageName = this.getPackageName();

        SandboxManager.getInstance().normalizeSandbox(webapp);
        boolean hasSandboxing = SandboxManager.isValidContainerId(webapp.getContainerId());
        String expectedProcess = hasSandboxing
                ? packageName + ":web_sandbox_" + webapp.getContainerId()
                : packageName;
        // Legacy shortcuts may point at the main process or the previous sandbox process.
        if (!expectedProcess.equals(processName)) {
            WebViewLauncher.startWebViewInNewProcess(webapp, this);
            return;
        }

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

        String url = webapp.getBaseUrl();

        wv = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);
        setupHermitShell();
        applyThemeIdentity();

        List<AdblockConfig> adblockConfigs = DataManager.getInstance().getSettings().getGlobalWebApp().getAdBlockSettings();
        if (webapp.isUseAdblock() && !adblockConfigs.isEmpty()) {
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

        String fieldName = Stream.of(__WebViewActivity_4.class.getDeclaredFields()).filter(f -> f.getType() == WebView.class).findFirst().orElseThrow(null).getName();
        String uaString = wv.getSettings().getUserAgentString().replace("; " + fieldName, "");
        wv.getSettings().setUserAgentString(uaString);
        defaultUserAgent = uaString;
        if (webapp.isUseCustomUserAgent()) {
            if(webapp.getUserAgent() != null && !webapp.getUserAgent().equals("")) {
                wv.getSettings().setUserAgentString(webapp.getUserAgent().replace("\0", "").replace("\n", "").replace("\r", ""));
            }
        }

        if (webapp.isShowFullscreen()) {
            this.hideSystemBars();
        } else if(DataManager.getInstance().getSettings().getAlwaysShowSoftwareButtons()) {
            this.showSystemBars();
        }
        if (webapp.isFrameless()) {
            this.hideSystemBars();
        }
        wv.setWebViewClient(new CustomBrowser());
        wv.getSettings().setSafeBrowsingEnabled(webapp.isSafeBrowsing());
        wv.getSettings().setDomStorageEnabled(true);
        wv.getSettings().setDatabaseEnabled(true);
        wv.getSettings().setAllowFileAccess(true);
        wv.getSettings().setBlockNetworkLoads(false);
//        wv.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        this.setDarkModeIfNeeded();

        wv.getSettings().setJavaScriptEnabled(webapp.isAllowJs());

        CookieManager.getInstance().setAcceptCookie(webapp.isAllowCookies());
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, webapp.isAllowThirdPartyCookies());

        if (webapp.isBlockImages())
            wv.getSettings().setBlockNetworkImage(true);

        if (webapp.isRequestDesktop()) {
            wv.getSettings().setUserAgentString(Const.DESKTOP_USER_AGENT);
            wv.getSettings().setUseWideViewPort(true);
            wv.getSettings().setLoadWithOverviewMode(true);

            wv.getSettings().setSupportZoom(true);
            wv.getSettings().setBuiltInZoomControls(true);
            wv.getSettings().setDisplayZoomControls(false);

            wv.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
            wv.setScrollbarFadingEnabled(false);

        }
        if(webapp.isEnableZooming()) {
            wv.getSettings().setSupportZoom(true);
            wv.getSettings().setBuiltInZoomControls(true);
        }

        CUSTOM_HEADERS = initCustomHeaders(webapp.isSendSavedataRequest());
        backgroundPlaybackController = new BackgroundPlaybackController(this, wv, webapp);
        loadURL(wv, url);
        wv.setWebChromeClient(new CustomWebChromeClient());
        wv.setOnLongClickListener(view -> {
            if(webapp.getAlwaysUseFallbackContextMenu()) return false;
            if(fallbackToDefaultLongClickBehaviour) {
                fallbackToDefaultLongClickBehaviour = false;
                return false;
            }
            showWebViewPopupMenu();
            return true;
        });
        updateToolbarContent(wv.getUrl());
        updateToolbarVisibility();
        refreshQuickSettingsState();


        wv.setDownloadListener((dl_url, userAgent, contentDisposition, mimeType, contentLength) -> {

            if (mimeType.equals("application/pdf")) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(dl_url));
                startActivity(i);
            } else {
                if(dl_url != null && !dl_url.equals("")) {
                    if(dl_url.startsWith("blob:")) {
                        dl_url = dl_url.replace("blob:", "");
                        try {
                            dl_url = URLDecoder.decode(dl_url, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                    DownloadManager.Request request = null;
                    try {
                        request = new DownloadManager.Request(
                                Uri.parse(dl_url));
                    }
                    catch(Exception e) {
                        NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                    }
                  String file_name = Utility.getFileNameFromDownload(dl_url, contentDisposition, mimeType);

                  request.setMimeType(mimeType);
                  request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(dl_url));
                  request.addRequestHeader("User-Agent", userAgent);
                  request.setTitle(file_name);
                  request.allowScanningByMediaScanner();
                  request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                  request.setDestinationInExternalPublicDir(
                          Environment.DIRECTORY_DOWNLOADS, file_name);

                  DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                      String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
                      if (!EasyPermissions.hasPermissions(__WebViewActivity_4.this, perms)) {
                          dl_request = request;
                          EasyPermissions.requestPermissions(__WebViewActivity_4.this, getString(R.string.permission_storage_rationale), Const.PERMISSION_RC_STORAGE, perms);
                      } else {
                          if (dm != null) {
                              dm.enqueue(request);
                              NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                          }
                      }
                  }
                  //No storage permission needed for Android 10+
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                      if (dm != null) {
                          dm.enqueue(request);
                          NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                      }
                  }
                }

            }
        });
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
                                    WebViewLauncher.startWebView(DataManager.getInstance().getPredecessor(webappID), __WebViewActivity_4.this);
                                    finish();
                                } else if (DataManager.getInstance().getSettings().isTwoFingerMultitouch()) {
                                    if (wv.canGoForward())
                                        wv.goForward();
                                }
                            } else {
                                if (event.getPointerCount() == 3 && DataManager.getInstance().getSettings().isThreeFingerMultitouch()) {
                                    WebViewLauncher.startWebView(DataManager.getInstance().getSuccessor(webappID), __WebViewActivity_4.this);
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
        quickSettingsDrawer = findViewById(R.id.quick_settings_drawer);
        drawerLayout.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateBrowserDrawerMetrics());
        findViewById(R.id.webviewActivity).addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateImmersiveGestureAccess());
        browserToolbarContainer = findViewById(R.id.browser_toolbar_container);
        toolbarTitle = findViewById(R.id.txtToolbarTitle);
        toolbarSubtitle = findViewById(R.id.txtToolbarSubtitle);
        quickSettingsTitle = findViewById(R.id.txtQuickSettingsTitle);
        quickSettingsHost = findViewById(R.id.txtQuickSettingsHost);
        quickSettingsSandbox = findViewById(R.id.txtQuickSettingsSandbox);
        quickSettingsFavicon = findViewById(R.id.imgQuickSettingsFavicon);
        quickColorSwatches = findViewById(R.id.quickColorSwatches);
        quickOverrideSwitch = findViewById(R.id.switchQuickOverrideGlobal);

        View homeButton = findViewById(R.id.btnToolbarHome);
        homeButton.setOnClickListener(view -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.btnToolbarRefresh).setOnClickListener(view -> reloadCurrentPage());
        findViewById(R.id.btnToolbarQuickSettings).setOnClickListener(view ->
                openQuickSettings());
        findViewById(R.id.btnQuickClose).setOnClickListener(view ->
                drawerLayout.closeDrawer(GravityCompat.END));

        setupQuickActionCards();
        setupQuickSettingsSwitches();
        setupQuickColorSwatches();
        setupQuickNavigation();
        positionBrowserToolbar();
        updateBrowserDrawerMetrics();
        populateQuickSettingsHeader();
        updateLoadingControls();
    }

    private void openQuickSettings() {
        refreshQuickSettingsState();
        drawerLayout.openDrawer(GravityCompat.END);
    }

    private void setupQuickActionCards() {
        findViewById(R.id.cardQuickDesktop).setOnClickListener(view -> {
            webapp.setRequestDesktop(!webapp.isRequestDesktop());
            applyDesktopMode();
            persistQuickSettings(true);
        });
        findViewById(R.id.cardQuickFrameless).setOnClickListener(view -> {
            webapp.setFrameless(!webapp.isFrameless());
            persistQuickSettings(false);
            updateToolbarVisibility();
            if (webapp.isFrameless() || webapp.isShowFullscreen()) {
                hideSystemBars();
            } else {
                showSystemBars();
            }
            refreshQuickSettingsState();
        });
        findViewById(R.id.cardQuickDarkMode).setOnClickListener(view -> {
            webapp.setForceDarkMode(!webapp.isForceDarkMode());
            webapp.setUseTimespanDarkMode(false);
            persistQuickSettings(false);
            setDarkModeIfNeeded();
            refreshQuickSettingsState();
        });
        findViewById(R.id.cardQuickShare).setOnClickListener(view -> shareCurrentUrl());
        findViewById(R.id.cardQuickExternal).setOnClickListener(view -> openCurrentUrlExternally());
        findViewById(R.id.cardQuickShortcut).setOnClickListener(view -> {
            ShortcutDialogFragment fragment = ShortcutDialogFragment.newInstance(webapp);
            fragment.show(getSupportFragmentManager(), "QuickShortcut-" + webapp.getID());
        });
    }

    private void setupQuickSettingsSwitches() {
        quickOverrideSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingQuickSettings) return;
            webapp.setOverrideGlobalSettings(checked);
            if (!checked) {
                int order = webapp.getOrder();
                boolean active = webapp.isActiveEntry();
                webapp.copySettings(DataManager.getInstance().getSettings().getGlobalWebApp());
                webapp.setOrder(order);
                webapp.setActiveEntry(active);
            }
            DataManager.getInstance().replaceWebApp(webapp);
            applyDynamicSettings();
            reloadCurrentPage();
        });

        MaterialSwitch adblock = findViewById(R.id.switchQuickAdblock);
        adblock.setOnCheckedChangeListener((button, checked) -> {
            if (updatingQuickSettings) return;
            webapp.setUseAdblock(checked);
            webapp.setSafeBrowsing(checked);
            wv.getSettings().setSafeBrowsingEnabled(checked);
            if (checked) {
                adFilter.setupWebView(wv);
                List<AdblockConfig> filters = DataManager.getInstance().getSettings()
                        .getGlobalWebApp().getAdBlockSettings();
                adblockLifecycleHelper.trySyncOperation(() ->
                        adblockProviderApiHelper.synchronizeAdblockProviderWithSettings(filters));
            }
            persistQuickSettings(true);
        });

        MaterialSwitch tracking = findViewById(R.id.switchQuickTracking);
        tracking.setOnCheckedChangeListener((button, checked) -> {
            if (updatingQuickSettings) return;
            webapp.setStripTrackingParams(checked);
            persistQuickSettings(false);
            if (checked) loadURL(wv, currentPageUrl());
        });

        MaterialSwitch dataSaver = findViewById(R.id.switchQuickDataSaver);
        dataSaver.setOnCheckedChangeListener((button, checked) -> {
            if (updatingQuickSettings) return;
            webapp.setSendSavedataRequest(checked);
            webapp.setBlockImages(checked);
            CUSTOM_HEADERS = initCustomHeaders(checked);
            wv.getSettings().setBlockNetworkImage(checked);
            persistQuickSettings(true);
        });

        MaterialSwitch javaScript = findViewById(R.id.switchQuickJavaScript);
        javaScript.setOnCheckedChangeListener((button, checked) -> {
            if (updatingQuickSettings) return;
            webapp.setAllowJs(checked);
            wv.getSettings().setJavaScriptEnabled(checked);
            persistQuickSettings(true);
        });

        MaterialSwitch pullToRefresh = findViewById(R.id.switchQuickPullToRefresh);
        pullToRefresh.setOnCheckedChangeListener((button, checked) -> {
            if (updatingQuickSettings) return;
            webapp.setPullToRefresh(checked);
            persistQuickSettings(false);
        });

        MaterialSwitch monitor = findViewById(R.id.switchQuickMonitor);
        monitor.setOnCheckedChangeListener((button, checked) -> {
            if (updatingQuickSettings) return;
            webapp.setMonitorEnabled(checked);
            persistQuickSettings(false);
            com.cylonid.nativealpha.monitors.WebMonitorWorker.syncPeriodicWork(this);
        });
    }

    private void setupQuickNavigation() {
        findViewById(R.id.btnQuickHome).setOnClickListener(view -> {
            quitOnNextBackpress = false;
            loadURL(wv, webapp.getBaseUrl());
        });
        findViewById(R.id.btnQuickBack).setOnClickListener(view -> {
            quitOnNextBackpress = false;
            if (wv.canGoBack()) {
                wv.goBack();
            } else {
                drawerLayout.closeDrawer(GravityCompat.END);
            }
        });
        findViewById(R.id.btnQuickRefresh).setOnClickListener(view -> reloadCurrentPage());
        findViewById(R.id.btnQuickForward).setOnClickListener(view -> {
            quitOnNextBackpress = false;
            if (wv.canGoForward()) wv.goForward();
        });
        findViewById(R.id.btnQuickMoreSettings).setOnClickListener(view -> {
            drawerLayout.closeDrawer(GravityCompat.END);
            Intent intent = new Intent(this, WebAppSettingsActivity.class);
            intent.putExtra(Const.INTENT_WEBAPPID, webapp.getID());
            startActivityForResult(intent, REQUEST_WEBAPP_SETTINGS);
        });
    }

    private void updateQuickNavigationState() {
        if (wv == null || drawerLayout == null) return;
        setQuickNavigationEnabled(R.id.btnQuickHome, !webapp.getBaseUrl().isBlank());
        setQuickNavigationEnabled(R.id.btnQuickBack, wv.canGoBack());
        setQuickNavigationEnabled(R.id.btnQuickRefresh, wv.getUrl() != null);
        setQuickNavigationEnabled(R.id.btnQuickForward, wv.canGoForward());
    }

    private void setQuickNavigationEnabled(int id, boolean enabled) {
        View button = findViewById(id);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.38f);
    }

    private void updateLoadingControls() {
        if (drawerLayout == null) return;
        MaterialButton toolbarButton = findViewById(R.id.btnToolbarRefresh);
        MaterialButton quickButton = findViewById(R.id.btnQuickRefresh);
        int icon = pageLoading ? R.drawable.ic_browser_stop_24 : R.drawable.ic_baseline_refresh_24;
        int description = pageLoading ? R.string.browser_stop_loading : R.string.browser_refresh_page;
        toolbarButton.setIconResource(icon);
        toolbarButton.setContentDescription(getString(description));
        toolbarButton.setEnabled(wv != null);
        quickButton.setIconResource(icon);
        quickButton.setContentDescription(getString(description));
        quickButton.setEnabled(pageLoading || (wv != null && wv.getUrl() != null));
        quickButton.setAlpha(quickButton.isEnabled() ? 1f : 0.38f);
    }

    private void setupQuickColorSwatches() {
        quickColorSwatches.removeAllViews();
        int swatchSize = dp(48);
        int margin = dp(4);
        for (Map.Entry<String, String> color : ColorPalette.INSTANCE.getHERMIT_COLORS().entrySet()) {
            View swatch = new View(this);
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
                persistQuickSettings(false);
                applyThemeIdentity();
                refreshQuickSettingsState();
            });
            quickColorSwatches.addView(swatch);
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
        if (!getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, value, true)
                || value.resourceId == 0) {
            return null;
        }
        return ContextCompat.getDrawable(this, value.resourceId);
    }

    private void populateQuickSettingsHeader() {
        String title = webapp.getTitle();
        quickSettingsTitle.setText(title == null || title.isBlank()
                ? displayHost(webapp.getBaseUrl()) : title);
        quickSettingsHost.setText(displayHost(webapp.getBaseUrl()));
        quickSettingsFavicon.setContentDescription(
                getString(R.string.browser_controls_title));
        Bitmap favicon = ShortcutIconUtils.loadFavicon(this, webapp.getID());
        if (favicon != null) {
            quickSettingsFavicon.setImageBitmap(favicon);
        } else {
            quickSettingsFavicon.setImageResource(R.mipmap.native_alpha);
        }
    }

    private void positionBrowserToolbar() {
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                        browserToolbarContainer.getLayoutParams();
        params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        if (DataManager.getInstance().getSettings().getToolbarPosition() == 1) {
            params.bottomToBottom =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        } else {
            params.topToTop =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        }
        browserToolbarContainer.setLayoutParams(params);
    }

    private void updateToolbarVisibility() {
        if (browserToolbarContainer == null) return;
        boolean frameless = webapp.isFrameless() || webapp.isShowFullscreen();
        browserToolbarContainer.setVisibility(frameless ? View.GONE : View.VISIBLE);
        updateImmersiveGestureAccess();
    }

    // Reserve a limited right-edge region for DrawerLayout, like Hermit's lower-edge access.
    // This changes gesture routing only: no overlay covers or intercepts website buttons.
    private void updateImmersiveGestureAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || webapp == null) return;
        View content = findViewById(R.id.webviewActivity);
        if (content == null) return;
        if ((!webapp.isFrameless() && !webapp.isShowFullscreen())
                || content.getWidth() == 0 || content.getHeight() == 0) {
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
        toolbarTitle.setText(title == null || title.isBlank() ? displayHost(url) : title);
        String host = displayHost(url);
        toolbarSubtitle.setText(host);
        toolbarSubtitle.setVisibility(host.isBlank() ? View.GONE : View.VISIBLE);
        View titleCapsule = findViewById(R.id.browserTitleCapsule);
        if (titleCapsule != null) {
            titleCapsule.setContentDescription(
                    (toolbarTitle.getText().toString() + ", " + host).trim());
        }
    }

    private String displayHost(@Nullable String url) {
        if (url == null || url.isBlank()) return "";
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || host.isBlank()) return "";
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private void refreshQuickSettingsState() {
        if (quickOverrideSwitch == null) return;
        updatingQuickSettings = true;
        quickOverrideSwitch.setChecked(webapp.isOverrideGlobalSettings());
        setSwitchState(R.id.switchQuickAdblock, webapp.isUseAdblock());
        setSwitchState(R.id.switchQuickTracking, webapp.isStripTrackingParams());
        setSwitchState(R.id.switchQuickDataSaver, webapp.isBlockImages());
        setSwitchState(R.id.switchQuickJavaScript, webapp.isAllowJs());
        setSwitchState(R.id.switchQuickPullToRefresh, webapp.isPullToRefresh());
        setSwitchState(R.id.switchQuickMonitor, webapp.isMonitorEnabled());
        updateQuickActionState(R.id.cardQuickDesktop, R.id.txtQuickDesktopState,
                getString(webapp.isRequestDesktop()
                        ? R.string.browser_action_desktop_on : R.string.browser_action_desktop));
        updateQuickActionState(R.id.cardQuickFrameless, R.id.txtQuickFramelessState,
                getString(webapp.isFrameless()
                        ? R.string.browser_action_frameless_on : R.string.browser_action_frameless));
        updateQuickActionState(R.id.cardQuickDarkMode, R.id.txtQuickDarkModeState,
                getString(webapp.isForceDarkMode()
                        ? R.string.browser_action_dark_mode_on : R.string.browser_action_dark_mode));
        int accent = ColorPalette.webAppColor(webapp.getThemeColor(), webapp.getContainerId());
        int tint = ColorPalette.contrastingTextColor(accent);
        styleActiveCard(R.id.cardQuickDesktop, webapp.isRequestDesktop(), accent, tint);
        styleActiveCard(R.id.cardQuickFrameless, webapp.isFrameless(), accent, tint);
        styleActiveCard(R.id.cardQuickDarkMode, webapp.isForceDarkMode(), accent, tint);
        updateSandboxBadge();
        updateColorSwatchSelection();
        updateQuickNavigationState();
        updatingQuickSettings = false;
    }

    private void setSwitchState(int id, boolean checked) {
        MaterialSwitch button = findViewById(id);
        button.setChecked(checked);
    }

    private void updateQuickActionState(int cardId, int textId, String label) {
        TextView text = findViewById(textId);
        text.setText(label);
        View card = findViewById(cardId);
        card.setContentDescription(label);
    }

    private void styleActiveCard(int cardId, boolean active, int accent, int contentColor) {
        com.google.android.material.card.MaterialCardView card = findViewById(cardId);
        card.setCardBackgroundColor(active ? accent : MaterialColors.getColor(
                card, R.attr.colorSurfaceContainer, Color.TRANSPARENT));
        int childCount = card.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = card.getChildAt(i);
            if (child instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) child;
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

    private void updateSandboxBadge() {
        int containerId = webapp.getContainerId();
        boolean sandboxed = SandboxManager.isValidContainerId(containerId);
        quickSettingsSandbox.setText(sandboxed
                ? getString(R.string.sandbox_badge, containerId + 1)
                : getString(R.string.no_sandbox_badge));
        int color = ColorPalette.colorForContainer(containerId);
        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(dp(12));
        badge.setColor(color);
        quickSettingsSandbox.setBackground(badge);
        quickSettingsSandbox.setTextColor(ColorPalette.contrastingTextColor(color));
    }

    private void updateColorSwatchSelection() {
        for (int i = 0; i < quickColorSwatches.getChildCount(); i++) {
            View swatch = quickColorSwatches.getChildAt(i);
            String colorName = swatch.getTag() == null ? "" : swatch.getTag().toString();
            boolean selected = colorName.equalsIgnoreCase(webapp.getThemeColor());
            swatch.setAlpha(selected ? 1f : 0.72f);
            swatch.setScaleX(selected ? 1.12f : 1f);
            swatch.setScaleY(selected ? 1.12f : 1f);
            swatch.setContentDescription(getString(selected
                    ? R.string.browser_theme_color_selected
                    : R.string.browser_theme_color_named, colorName));
        }
    }

    private void persistQuickSettings(boolean requirePageReload) {
        webapp.setOverrideGlobalSettings(true);
        DataManager.getInstance().replaceWebApp(webapp);
        refreshQuickSettingsState();
        if (requirePageReload && wv != null) {
            reloadCurrentPage();
        }
    }

    private void applyDesktopMode() {
        if (webapp.isRequestDesktop()) {
            wv.getSettings().setUserAgentString(Const.DESKTOP_USER_AGENT);
            wv.getSettings().setUseWideViewPort(true);
            wv.getSettings().setLoadWithOverviewMode(true);
        } else {
            String userAgent = webapp.isUseCustomUserAgent() && webapp.getUserAgent() != null
                    && !webapp.getUserAgent().isBlank()
                    ? webapp.getUserAgent() : defaultUserAgent;
            wv.getSettings().setUserAgentString(
                    userAgent.replace("\0", "").replace("\n", "").replace("\r", ""));
            wv.getSettings().setUseWideViewPort(false);
            wv.getSettings().setLoadWithOverviewMode(false);
        }
        boolean zoomEnabled = webapp.isRequestDesktop() || webapp.isEnableZooming();
        wv.getSettings().setSupportZoom(zoomEnabled);
        wv.getSettings().setBuiltInZoomControls(zoomEnabled);
        wv.getSettings().setDisplayZoomControls(false);
    }

    private void applyDynamicSettings() {
        applyDesktopMode();
        wv.getSettings().setJavaScriptEnabled(webapp.isAllowJs());
        wv.getSettings().setBlockNetworkImage(webapp.isBlockImages());
        wv.getSettings().setSafeBrowsingEnabled(webapp.isSafeBrowsing());
        CookieManager.getInstance().setAcceptCookie(webapp.isAllowCookies());
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, webapp.isAllowThirdPartyCookies());
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
        if (webapp.isFrameless() || webapp.isShowFullscreen()) {
            hideSystemBars();
        } else {
            showSystemBars();
        }
        setDarkModeIfNeeded();
        positionBrowserToolbar();
        updateToolbarVisibility();
        applyThemeIdentity();
        updateToolbarContent(wv.getUrl());
        populateQuickSettingsHeader();
        refreshQuickSettingsState();
    }

    private void reloadCurrentPage() {
        if (pageLoading) {
            wv.stopLoading();
            setPageLoading(false);
            Toast.makeText(this, R.string.browser_loading_stopped, Toast.LENGTH_SHORT).show();
            return;
        }
        currently_reloading = true;
        setPageLoading(true);
        wv.reload();
    }

    private void setPageLoading(boolean loading) {
        pageLoading = loading;
        if (!loading) {
            currently_reloading = false;
            progressBar.setVisibility(View.GONE);
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

    private void shareCurrentUrl() {
        String url = currentPageUrl();
        new ShareCompat.IntentBuilder(this)
                .setType("text/plain")
                .setChooserTitle(getString(R.string.share))
                .setText(url)
                .startChooser();
    }

    private void openCurrentUrlExternally() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentPageUrl())));
        } catch (Exception e) {
            Toast.makeText(this, R.string.external_app_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private String currentPageUrl() {
        String url = wv.getUrl();
        return url == null || url.isBlank() ? webapp.getBaseUrl() : url;
    }

    private int dp(int value) {
        return Math.round(value * Resources.getSystem().getDisplayMetrics().density);
    }

    private void applyThemeIdentity() {
        int accentColor = ColorPalette.colorForTheme(webapp.getThemeColor());
        getWindow().setStatusBarColor(accentColor);
        progressBar.setProgressTintList(ColorStateList.valueOf(accentColor));
        View titleCapsule = findViewById(R.id.browserTitleCapsule);
        if (titleCapsule instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) titleCapsule)
                    .setStrokeColor(accentColor);
            ((com.google.android.material.card.MaterialCardView) titleCapsule)
                    .setStrokeWidth(dp(1));
        }
        View containerDot = findViewById(R.id.toolbarContainerDot);
        if (containerDot != null) {
            containerDot.setBackgroundTintList(ColorStateList.valueOf(
                    ColorPalette.colorForContainer(webapp.getContainerId())));
        }

        Bitmap favicon = ShortcutIconUtils.loadFavicon(this, webapp.getID());
        ActivityManager.TaskDescription description = favicon == null
                ? new ActivityManager.TaskDescription(webapp.getTitle(), null, accentColor)
                : new ActivityManager.TaskDescription(webapp.getTitle(), favicon, accentColor);
        setTaskDescription(description);
    }

    @SuppressLint("RequiresFeature")
    private void setDarkModeIfNeeded() {
        if (!BuildConfig.FLAVOR.contains("extended")) {
            return;
        }

        boolean needsForcedDarkMode = webapp.isUseTimespanDarkMode() &&
                DateUtils.isInInterval(DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeBegin()), Calendar.getInstance(), DateUtils.convertStringToCalendar(webapp.getTimespanDarkModeEnd()))
                || (!webapp.isUseTimespanDarkMode() && webapp.isForceDarkMode());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean isForceDarkSupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK);
            boolean isForceDarkStrategySupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY);
            boolean isAlgorithmicDarkeningSupported = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING);

            if (needsForcedDarkMode) {
                wv.setBackgroundColor(Color.BLACK);
                wv.setForceDarkAllowed(true);
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                if (isForceDarkSupported) {
                    WebSettingsCompat.setForceDark(wv.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
                }
                if (isForceDarkStrategySupported) {
                    WebSettingsCompat.setForceDarkStrategy(wv.getSettings(), WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
                }
                if (isAlgorithmicDarkeningSupported) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), true);
                }
            } else {
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                wv.setBackgroundColor(Color.WHITE);

                if (isForceDarkSupported) {
                    WebSettingsCompat.setForceDark(wv.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
                }
                if (isForceDarkStrategySupported) {
                    WebSettingsCompat.setForceDarkStrategy(wv.getSettings(), WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY);
                }
                if (isAlgorithmicDarkeningSupported) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.getSettings(), false);
                }
            }
        }

    }

    @SuppressLint("NonConstantResourceId")
    private void showWebViewPopupMenu() {
        View center = findViewById(R.id.anchorCenterScreen);
        mPopupMenu = IconPopupMenuHelper.getMenu(center, R.menu.wv_context_menu, __WebViewActivity_4.this);

        String currentUrl = wv.getUrl();
        String title = "";
        if (currentUrl != null) {
            title = currentUrl.length() < 32 ? currentUrl : currentUrl.substring(0, 32) + "…";
        }
        SpannableString spanStringWebAppTitle = new SpannableString(title);

        // The item is disabled because it has no click action, but we want to override the disabled style (text color)
        int colorOnSurface = MaterialColors.getColor(center, R.attr.colorOnSurface, Color.BLACK);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(colorOnSurface);
        spanStringWebAppTitle.setSpan(foregroundColorSpan, 0,     spanStringWebAppTitle.length(), 0);

        spanStringWebAppTitle.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, spanStringWebAppTitle.length(), 0);
        mPopupMenu.getMenu().getItem(0).setTitle(spanStringWebAppTitle);

        for (int i = 0; i < mPopupMenu.getMenu().size(); i++) {
            MenuItem item = mPopupMenu.getMenu().getItem(i);
            SpannableString spanString = new SpannableString(item.getTitle());
            spanString.setSpan(foregroundColorSpan, 0, spanString.length(),0);
            item.setTitle(spanString);
        }
        if(wv.canGoForward()) mPopupMenu.getMenu().getItem(2).setVisible(true);
        if(BuildConfig.DEBUG) {
            mPopupMenu.getMenu().getItem(6).setVisible(true);
        }
        mPopupMenu.setOnMenuItemClickListener(menuItem -> {
            switch(menuItem.getItemId()) {
                case R.id.cmItemForward:
                    wv.goForward();
                    return true;
                case R.id.cmItemBack:
                    onBackPressed();
                    return true;
                case R.id.cmItemReload:
                    wv.reload();
                    return true;
                case R.id.cmItemCopyUrl:
                    ClipboardManager clipboard =  getSystemService(ClipboardManager.class);
                    ClipData clip = ClipData.newPlainText("URL", wv.getUrl());
                    clipboard.setPrimaryClip(clip);
                    return true;
                case R.id.cmItemShareUrl:
                    new ShareCompat.IntentBuilder(__WebViewActivity_4.this)
                            .setType("text/plain")
                            .setChooserTitle("Share URL")
                            .setText(wv.getUrl())
                            .startChooser();
                    return true;
                case R.id.cmItemCloseWebApp:
                    finishAndRemoveTask();
                    return true;
                case R.id.cmFallbackContextmenuTemp:
                    fallbackToDefaultLongClickBehaviour = true;
                    return true;
                case R.id.cmMainMenu:
                    Intent intent = new Intent(this, MainActivity.class);
                    startActivity(intent);
                    return true;
                case R.id.cmItemQuickSettings:
                    drawerLayout.openDrawer(GravityCompat.END);
                    return true;
                case R.id.cmShowAdblockProviders:
                    StringBuilder message = new StringBuilder();
                    for(Map.Entry<String, Filter> entry :  Objects.requireNonNull(AdFilter.Companion.get().getViewModel().getFilters().getValue()).entrySet()) {
                        Filter filter = entry.getValue();
                        message.append(filter.getUrl()).append(" has downloaded: ").append(filter.hasDownloaded()).append("\n\n");
                }
                    NotificationUtils.showToast(this, message.toString());
                    return true;

            }
            return false;
        });

        mPopupMenu.show();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateBrowserDrawerMetrics();
        if (wv != null) updateQuickNavigationState();
        this.setDarkModeIfNeeded();
    }

    @Override
    @SuppressLint("MissingSuperCall") // Custom back navigation hierarchy: drawer -> webview history -> base url -> exit
    public void onBackPressed() {
        if (closeOpenDrawer()) return;
        if (webapp.isFrameless()) {
            hideSystemBars();
        }
        if(wv.canGoBack()) {
            quitOnNextBackpress = false;
            backExitHandler.removeCallbacks(resetBackExitHint);
            wv.goBack();
            return;
        }

        if(quitOnNextBackpress) {
            quitOnNextBackpress = false;
            backExitHandler.removeCallbacks(resetBackExitHint);
            moveTaskToBack(true);
            return;
        }

        loadURL(wv, webapp.getBaseUrl());
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
            if (new_webapp != null) WebViewLauncher.startWebViewInNewProcess(new_webapp, this);
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
        String loadedSettingsJson = gson.toJson(latestSettings);
        // Sandboxed activities may still have the old SharedPreferences snapshot after editing.
        if (settingsFromEditor != null) {
            latestSettings = settingsFromEditor;
            settingsFromEditor = null;
            if (!latestSettings.isOverrideGlobalSettings()) {
                int order = latestSettings.getOrder();
                boolean active = latestSettings.isActiveEntry();
                latestSettings.copySettings(DataManager.getInstance().getSettings().getGlobalWebApp());
                latestSettings.setOrder(order);
                latestSettings.setActiveEntry(active);
            }
        } else if (loadedSettingsJson.equals(lastLoadedSettingsJson)) {
            latestSettings = webapp;
        }
        SandboxManager.getInstance().normalizeSandbox(latestSettings);
        if (webapp.getContainerId() != latestSettings.getContainerId()) {
            WebViewLauncher.startWebViewInNewProcess(latestSettings, this);
            finish();
            return;
        }
        lastLoadedSettingsJson = loadedSettingsJson;
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
        wv.onResume();
        wv.resumeTimers();
        if (backgroundPlaybackController != null) {
            backgroundPlaybackController.onResume(webapp);
        }
        if (settingsChanged) {
            applyDynamicSettings();
            if (requiresReload) reloadCurrentPage();
        } else {
            setDarkModeIfNeeded();
            updateToolbarVisibility();
            updateToolbarContent(wv.getUrl());
            refreshQuickSettingsState();
        }

        
        if(webapp.isBiometricProtection()) {
            View fullActivityView = findViewById(R.id.webviewActivity);
            fullActivityView.setVisibility(View.GONE);
            new BiometricPromptHelper(__WebViewActivity_4.this).showPrompt(() -> fullActivityView.setVisibility(View.VISIBLE), () -> finish(), getString(R.string.bioprompt_restricted_webapp));
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
        if(mPopupMenu != null) mPopupMenu.dismiss();

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
        backExitHandler.removeCallbacksAndMessages(null);
        if (reload_handler != null) reload_handler.removeCallbacksAndMessages(null);
        if (mPopupMenu != null) mPopupMenu.dismiss();
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
        if (DataManager.getInstance().getSettings().getClear_cookies()) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        }
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

    private Map<String, String> initCustomHeaders(boolean save_data) {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("DNT", "1");
        extraHeaders.put("X-REQUESTED-WITH", "");
        if (save_data)
            extraHeaders.put("Save-Data", "on");
        return Collections.unmodifiableMap(extraHeaders);
    }

    private void loadURL(final WebView view, final String url) {
        final WebApp webApp = webapp;
        final String sanitizedUrl = webApp.isStripTrackingParams()
                ? UrlSanitizer.sanitizeTrackingParameters(url)
                : url;
        final String targetUrl = sanitizedUrl == null ? url : sanitizedUrl;
        if (targetUrl.regionMatches(true, 0, "http://", 0, 7) && !webApp.isAllowHttp()) {
            final MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(__WebViewActivity_4.this);

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
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if(controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());

                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void showSystemBars() {

        if(webapp.isShowFullscreen()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @FunctionalInterface
    interface PermissionGrantedCallback {
        void execute();
    }

    private void enablePermissionBoolOnWebApp(PermissionGrantedCallback successCallback) {
        webapp.setOverrideGlobalSettings(true);
        successCallback.execute();
        DataManager.getInstance().replaceWebApp(webapp);
        wv.reload();
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        if (requestCode == Const.PERMISSION_RC_LOCATION) {
            enablePermissionBoolOnWebApp(() -> webapp.setAllowLocationAccess(true));
            this.handleGeoPermissionCallback(true);
        }
        if (requestCode == Const.PERMISSION_CAMERA) {
            enablePermissionBoolOnWebApp(() -> webapp.setCameraPermission(true));
        }
        if (requestCode == Const.PERMISSION_RC_STORAGE) {
            if (dl_request != null) {
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(dl_request);
                    NotificationUtils.showInfoSnackbar(this, getString(R.string.file_download), Snackbar.LENGTH_SHORT);
                }
                dl_request = null;

            }
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        if (requestCode == Const.PERMISSION_RC_LOCATION) {
            this.handleGeoPermissionCallback(false);
        }
    }

    private void handleGeoPermissionCallback(boolean allow) {
        if (mGeoPermissionRequestCallback != null) {
            mGeoPermissionRequestCallback.invoke(mGeoPermissionRequestOrigin, allow, false);
            mGeoPermissionRequestCallback = null;
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
                        if (updated != null && updated.getID() == webappID) settingsFromEditor = updated;
                    } catch (com.google.gson.JsonParseException e) {
                        Log.w("SETTINGS", "Unable to read updated WebApp settings", e);
                    }
                }
            }
            return;
        }
        if (resultCode == RESULT_CANCELED && requestCode == CODE_OPEN_FILE) {
            this.filePathCallback.onReceiveValue(null);
        } else if (resultCode == RESULT_OK && requestCode == CODE_OPEN_FILE) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            filePathCallback = null;
        }
    }


    private class CustomWebChromeClient extends android.webkit.WebChromeClient {

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            super.onReceivedIcon(view, icon);
            if (icon != null && webapp != null) {
                Bitmap existing = ShortcutIconUtils.loadFavicon(__WebViewActivity_4.this, webapp.getID());
                if (existing == null || (existing.getWidth() < 64 && icon.getWidth() >= existing.getWidth())) {
                    ShortcutIconUtils.saveFavicon(__WebViewActivity_4.this, webapp.getID(), icon);
                    populateQuickSettingsHeader();
                }
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (title != null && !title.trim().isEmpty() && webapp != null) {
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
                    DataManager.getInstance().saveWebAppData();
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

        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;

        private void handlePermissionRequest(String resId,
                                             boolean currentState,
                                             String[] androidPermissions,
                                             int requestCode,
                                             List<String> permissionsToGrant,
                                             String[] webkitPermission,
                                             PermissionGrantedCallback successCallback) {
            boolean androidPermissionsMissing = !EasyPermissions.hasPermissions(__WebViewActivity_4.this, androidPermissions);
            if (currentState && androidPermissionsMissing) {
                ActivityCompat.requestPermissions(__WebViewActivity_4.this, androidPermissions, requestCode);
                return;
            }
            if (currentState && !androidPermissionsMissing) {
                permissionsToGrant.addAll(Arrays.asList(webkitPermission));
                handleGeoPermissionCallback(true);
                return;
            }

            new MaterialAlertDialogBuilder(__WebViewActivity_4.this).setTitle(getPermissionRequestStringResource("dialog_permission_", resId, "_title"))
                    .setMessage(getPermissionRequestStringResource("dialog_permission_", resId, "_txt"))
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        enablePermissionBoolOnWebApp(successCallback);
                        handleGeoPermissionCallback(true);
                        permissionsToGrant.addAll(Arrays.asList(webkitPermission));
                        if (androidPermissionsMissing) {
                            ActivityCompat.requestPermissions(__WebViewActivity_4.this, androidPermissions, requestCode);
                        }
                    }).setNegativeButton(android.R.string.no, (dialog, id) -> handleGeoPermissionCallback(false)).create().show();
        }

        private String getPermissionRequestStringResource(String prefix, String variable, String suffix) {
            return getString(__WebViewActivity_4.this.getResources().getIdentifier(prefix + variable + suffix, "string", __WebViewActivity_4.this.getPackageName()));
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView, ValueCallback<Uri[]> pFilePathCallback,
                WebChromeClient.FileChooserParams fileChooserParams) {
            filePathCallback = pFilePathCallback;
            try {
                Intent intent = fileChooserParams.createIntent();
                startActivityForResult(intent, CODE_OPEN_FILE);
            } catch (Exception e) {
                NotificationUtils.showInfoSnackbar(__WebViewActivity_4.this, getString(R.string.no_filemanager), Snackbar.LENGTH_LONG);
                e.printStackTrace();
            }
            return true;
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            final Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawARGB(0, 0, 0, 0);
            return bitmap;
        }

        public void onHideCustomView() {
            ((FrameLayout) getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
            showSystemBars();
        }

        public void onShowCustomView(View pView, WebChromeClient.CustomViewCallback pViewCallback) {
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = pView;
            this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            this.mOriginalOrientation = getRequestedOrientation();
            this.mCustomViewCallback = pViewCallback;
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
            hideSystemBars();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            List<String> permissionsToGrant = new ArrayList<>();

            boolean containsDrmRequest = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
            boolean containsCameraRequest = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            boolean containsMicrophoneRequest = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);

            if (containsDrmRequest) {
                this.handlePermissionRequest("drm", webapp.isDrmAllowed(), new String[]{}, -1, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}, () -> webapp.setDrmAllowed(true));
            }
            if (containsCameraRequest) {
                this.handlePermissionRequest("camera", webapp.isCameraPermission(), new String[]{Manifest.permission.CAMERA}, Const.PERMISSION_CAMERA, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}, () -> webapp.setCameraPermission(true));
            }

            if (containsMicrophoneRequest) {
                this.handlePermissionRequest("microphone", webapp.isMicrophonePermission(), new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS}, Const.PERMISSION_AUDIO, permissionsToGrant, new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}, () -> webapp.setMicrophonePermission(true));
            }

            request.grant(permissionsToGrant.toArray(new String[0]));
        }


        public void onProgressChanged(WebView view, int progress) {
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
            mGeoPermissionRequestCallback = callback;
            mGeoPermissionRequestOrigin = origin;
            this.handlePermissionRequest("location", webapp.isAllowLocationAccess(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, Const.PERMISSION_RC_LOCATION, Arrays.asList(new String[]{}), new String[]{}, () -> webapp.setAllowLocationAccess(true));

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

        private AdFilter adFilter = AdFilter.Companion.get();

        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            showHttpAuthDialog(handler, host, realm);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            setPageLoading(false);
            if(url.equals("about:blank")) {
                String langExtension = LocaleUtils.getFileEnding();
                wv.loadUrl("file:///android_asset/errorSite/error_" + langExtension + ".html");
            }
            injectPageSettings(view);
            injectUserscriptIfAllowed(view);
            if (backgroundPlaybackController != null) {
                backgroundPlaybackController.onPageFinished(url);
            }
            updateToolbarContent(url);
            updateQuickNavigationState();
            super.onPageFinished(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            setPageLoading(true);
            if (backgroundPlaybackController != null) {
                backgroundPlaybackController.onPageStarted(url);
            }
            updateToolbarContent(url);
            updateQuickNavigationState();
            if (webapp.isUseAdblock()) adFilter.performScript(view, url);
            super.onPageStarted(view, url, favicon);
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if(urlOnFirstPageload.equals("")) urlOnFirstPageload = request.getUrl().toString();

            if(webapp.isUseAdblock()) {
                return (adFilter.shouldIntercept(view, request)).getResourceResponse();
            }
            if (webapp.isBlockThirdPartyRequests()) {
                Uri uri = request.getUrl();
                Uri webapp_uri = Uri.parse(webapp.getBaseUrl());

                if(uri.getHost() != null) {
                    if (!uri.getHost().endsWith(webapp_uri.getHost())) {
                        return new WebResourceResponse("text/plain", "utf-8", null);
                    }
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {

            //This option is hidden in "expert settings"
            if (webapp.isIgnoreSslErrors()) {
                handler.proceed();
                return;
            }

            final MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(__WebViewActivity_4.this);

            String message = getString(R.string.ssl_error_msg_line1) + " ";
            switch (error.getPrimaryError()) {
                case SslError.SSL_UNTRUSTED:
                    message += getString(R.string.ssl_error_unknown_authority) + "\n";
                    break;
                case SslError.SSL_EXPIRED:
                    message += getString(R.string.ssl_error_expired) + "\n";
                    break;
                case SslError.SSL_IDMISMATCH:
                    message += getString(R.string.ssl_error_id_mismatch) + "\n";
                    break;
                case SslError.SSL_NOTYETVALID:
                    message += getString(R.string.ssl_error_notyetvalid) + "\n";
                    break;
            }
            message += getString(R.string.ssl_error_msg_line2) + "\n";

            builder.setTitle(getString(R.string.ssl_error_title));
            builder.setMessage(message);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(getString(android.R.string.cancel), (dialog, id) -> handler.cancel());
            builder.setNegativeButton(getString(R.string.load_anyway), (dialog, id) -> handler.proceed());
            final AlertDialog dialog = builder.create();
            dialog.show();
//            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setPadding(5, 5, 5, 5);
//            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(ContextCompat.getColor(__WebViewActivity_4.this, android.R.color.holo_orange_light));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(__WebViewActivity_4.this, android.R.color.holo_red_dark));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(__WebViewActivity_4.this, android.R.color.holo_green_dark));
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
            boolean isWebUrl = "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme);
            if (!isWebUrl) {
                if (!webapp.isAllowAppLaunch()) {
                    Toast.makeText(
                            __WebViewActivity_4.this,
                            R.string.external_app_launch_blocked,
                            Toast.LENGTH_SHORT
                    ).show();
                    return true;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, targetUri));
                } catch (Exception e) {
                    Toast.makeText(
                            __WebViewActivity_4.this,
                            R.string.external_app_not_available,
                            Toast.LENGTH_SHORT
                    ).show();
                }
                return true;
            }

            if (webapp.isOpenUrlExternal()) {
                String requestHost = normalizeHost(Uri.parse(url).getHost());
                String baseHost = normalizeHost(Uri.parse(webapp.getBaseUrl()).getHost());
                boolean belongsToWebApp = baseHost == null
                        || requestHost == null
                        || requestHost.equals(baseHost)
                        || requestHost.endsWith("." + baseHost);
                if (!belongsToWebApp) {
                    if (webapp.isAllowAppLaunch()) {
                        view.getContext().startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    }
                    return true;
                }
            }
            loadURL(view, url);
            return true;
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
            view.evaluateJavascript("(function(){"
                    + "if(window.__nativeAlphaPageSettingsApplied)return;"
                    + "window.__nativeAlphaPageSettingsApplied=true;"
                    + "document.addEventListener('visibilitychange',function(event){"
                    + "event.stopImmediatePropagation();},true);"
                    + desktopScript + "})();", null);
        }

        private void injectUserscriptIfAllowed(WebView view) {
            if (!webapp.isUserscriptEnabled() || webapp.getUserscriptCode().isBlank()) {
                return;
            }
            String script = webapp.getUserscriptCode();
            String lower = script.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("@grant")
                    || lower.contains("@require")
                    || lower.contains("@resource")) {
                return;
            }
            String wrappedScript = "(function(){ 'use strict'; "
                    + "if(window.__nativeAlphaUserscriptApplied)return;"
                    + "window.__nativeAlphaUserscriptApplied=true;" + script + "\n})();";
            view.evaluateJavascript(wrappedScript, null);
        }

        @Nullable
        private String normalizeHost(@Nullable String host) {
            if (host == null) {
                return null;
            }
            String normalized = host.toLowerCase(java.util.Locale.ROOT);
            while (normalized.startsWith("www.")) {
                normalized = normalized.substring(4);
            }
            return normalized.isEmpty() ? null : normalized;
        }
    }
}


