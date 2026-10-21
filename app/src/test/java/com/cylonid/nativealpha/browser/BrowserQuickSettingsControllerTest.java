package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cylonid.nativealpha.R;
import io.github.edsuns.adfilter.AdFilter;
import com.cylonid.nativealpha.helper.AdblockLifecycleHelper;
import com.cylonid.nativealpha.helper.AdblockProviderApiHelper;
import com.cylonid.nativealpha.model.WebApp;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BrowserQuickSettingsControllerTest {

    private Activity activity;
    private View rootView;
    private WebApp webapp;
    private BrowserQuickSettingsController controller;

    private boolean persisted;
    private boolean systemBarsApplied;
    private boolean dynamicSettingsApplied;
    private boolean reloaded;
    private boolean desktopModeApplied;
    private boolean darkModeApplied;
    private boolean toolbarVisibilityUpdated;
    private boolean drawerClosed;
    private String lastLoadedUrl;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
        activity.setTheme(R.style.AppTheme);

        rootView = LayoutInflater.from(activity).inflate(R.layout.full_webview, null);
        webapp = new WebApp("https://example.com", 1);
        webapp.setTitle("Example App");

        controller = new BrowserQuickSettingsController(rootView, new BrowserQuickSettingsController.Callback() {
            @NonNull @Override public WebApp getWebApp() { return webapp; }
            @Nullable @Override public WebView getWebView() { return null; }
            @Override public boolean isPageLoading() { return false; }
            @Override public void persistQuickSettings(boolean requireReload) { persisted = true; }
            @Override public void applySystemBars() { systemBarsApplied = true; }
            @Override public void applyDynamicSettings() { dynamicSettingsApplied = true; }
            @Override public void forceReloadCurrentPage() { reloaded = true; }
            @Override public void reloadCurrentPage() { reloaded = true; }
            @Override public void applyDesktopMode() { desktopModeApplied = true; }
            @Override public void setDarkModeIfNeeded() { darkModeApplied = true; }
            @Override public void updateToolbarVisibility() { toolbarVisibilityUpdated = true; }
            @Override public void closeDrawer() { drawerClosed = true; }
            @Override public void shareCurrentUrl() {}
            @Override public void openCurrentUrlExternally() {}
            @Override public void copyCurrentUrl() {}
            @Override public void openReaderMode() {}
            @Override public void openFindInPage() {}
            @Override public void showShortcutDialog() {}
            @Override public void showSandboxPickerDialog() {}
            @Override public void loadUrl(String url) { lastLoadedUrl = url; }
            @Override public String getCurrentPageUrl() { return "https://example.com/page"; }
            @Override public Map<String, String> initCustomHeaders(boolean sendSaveData) { return Collections.emptyMap(); }
            @Override public void onHeadersUpdated(Map<String, String> headers) {}
            @Override public io.github.edsuns.adfilter.AdFilter getAdFilter() { return null; }
            @Override public com.cylonid.nativealpha.helper.AdblockProviderApiHelper getAdblockProviderApiHelper() { return null; }
            @Override public com.cylonid.nativealpha.helper.AdblockLifecycleHelper getAdblockLifecycleHelper() { return null; }
            @Override public void applyThemeIdentity() {}
            @Override public void onMoreSettingsClicked() {}
        });
    }

    @Test
    public void displayHostSanitizesDomainCorrectly() {
        assertEquals("", BrowserQuickSettingsController.displayHost(null));
        assertEquals("", BrowserQuickSettingsController.displayHost(""));
        assertEquals("google.com", BrowserQuickSettingsController.displayHost("https://www.google.com/search?q=test"));
        assertEquals("sub.domain.org", BrowserQuickSettingsController.displayHost("https://sub.domain.org/path"));
        assertEquals("localhost", BrowserQuickSettingsController.displayHost("http://localhost:8080"));
    }

    @Test
    public void textZoomClampsAndResetsCorrectly() {
        controller.applyTextZoom(150);
        assertEquals(150, webapp.getTextZoom());
        assertTrue(persisted);

        TextView txtZoom = rootView.findViewById(R.id.txtQuickZoomValue);
        assertNotNull(txtZoom);
        assertEquals("150%", txtZoom.getText().toString());

        View btnDecrease = rootView.findViewById(R.id.btnQuickZoomDecrease);
        assertNotNull(btnDecrease);
        btnDecrease.performClick();
        assertEquals(140, webapp.getTextZoom());

        View btnIncrease = rootView.findViewById(R.id.btnQuickZoomIncrease);
        assertNotNull(btnIncrease);
        btnIncrease.performClick();
        assertEquals(150, webapp.getTextZoom());

        txtZoom.performClick();
        assertEquals(100, webapp.getTextZoom());
    }

    @Test
    public void fullscreenSwitchTogglesStateAndCallsHost() {
        MaterialSwitch switchFullscreen = rootView.findViewById(R.id.switchQuickFullscreen);
        assertNotNull(switchFullscreen);

        switchFullscreen.setChecked(true);
        assertTrue(webapp.isShowFullscreen());
        assertTrue(systemBarsApplied);
        assertTrue(persisted);

        systemBarsApplied = false;
        switchFullscreen.setChecked(false);
        assertFalse(webapp.isShowFullscreen());
        assertTrue(systemBarsApplied);
    }

    @Test
    public void trackingSwitchTogglesAndReloadsSanitizedUrl() {
        MaterialSwitch switchTracking = rootView.findViewById(R.id.switchQuickTracking);
        assertNotNull(switchTracking);

        switchTracking.setChecked(true);
        assertTrue(webapp.isStripTrackingParams());
        assertEquals("https://example.com/page", lastLoadedUrl);
    }

    @Test
    public void desktopCardTogglesModeAndPersists() {
        View cardDesktop = rootView.findViewById(R.id.cardQuickDesktop);
        assertNotNull(cardDesktop);

        assertFalse(webapp.isRequestDesktop());
        cardDesktop.performClick();
        assertTrue(webapp.isRequestDesktop());
        assertTrue(desktopModeApplied);
        assertTrue(persisted);
    }

    @Test
    public void framelessCardTogglesAndClosesDrawerWhenEnabled() {
        View cardFrameless = rootView.findViewById(R.id.cardQuickFrameless);
        assertNotNull(cardFrameless);

        assertFalse(webapp.isFrameless());
        cardFrameless.performClick();
        assertTrue(webapp.isFrameless());
        assertTrue(drawerClosed);
        assertTrue(toolbarVisibilityUpdated);
        assertTrue(systemBarsApplied);
    }

    @Test
    public void darkModeCardTogglesAndInvokesHost() {
        View cardDarkMode = rootView.findViewById(R.id.cardQuickDarkMode);
        assertNotNull(cardDarkMode);

        assertFalse(webapp.isForceDarkMode());
        cardDarkMode.performClick();
        assertTrue(webapp.isForceDarkMode());
        assertTrue(darkModeApplied);
    }

    @Test
    public void populateHeaderShowsTitleAndHost() {
        controller.populateHeader();

        TextView txtTitle = rootView.findViewById(R.id.txtQuickSettingsTitle);
        TextView txtHost = rootView.findViewById(R.id.txtQuickSettingsHost);

        assertNotNull(txtTitle);
        assertNotNull(txtHost);
        assertEquals("Example App", txtTitle.getText().toString());
        assertEquals("example.com", txtHost.getText().toString());
    }

    @Test
    public void refreshStateSynchronizesSwitchStates() {
        webapp.setShowFullscreen(true);
        webapp.setStripTrackingParams(true);
        webapp.setAllowJs(false);
        webapp.setRequestDesktop(true);

        controller.refreshState();

        MaterialSwitch switchFullscreen = rootView.findViewById(R.id.switchQuickFullscreen);
        MaterialSwitch switchTracking = rootView.findViewById(R.id.switchQuickTracking);
        MaterialSwitch switchJs = rootView.findViewById(R.id.switchQuickJavaScript);

        assertNotNull(switchFullscreen);
        assertNotNull(switchTracking);
        assertNotNull(switchJs);

        assertTrue(switchFullscreen.isChecked());
        assertTrue(switchTracking.isChecked());
        assertFalse(switchJs.isChecked());
    }
}
