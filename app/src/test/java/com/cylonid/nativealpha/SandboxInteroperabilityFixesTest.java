package com.cylonid.nativealpha;

import com.cylonid.nativealpha.util.App;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.GlobalSettings;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SandboxInteroperabilityFixesTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ReflectionHelpers.setStaticField(App.class, "context", context);
        context.getSharedPreferences("WEBSITEDATA", 0).edit().clear().commit();
        context.getSharedPreferences("SANDBOX_SETTINGS", 0).edit().clear().commit();
        DataManager manager = DataManager.getInstance();
        ReflectionHelpers.setField(manager, "websites", new ArrayList<WebApp>());
        ReflectionHelpers.setField(manager, "settings", new GlobalSettings());
        ReflectionHelpers.setField(manager, "appsBaseline", null);
        manager.loadAppData();
    }

    @Test
    public void newWebAppDefaultsToDefaultSandboxAndAllowsThirdPartyCookies() {
        WebApp app = new WebApp("https://example.com", 1);
        assertEquals(0, app.getContainerId());
        assertTrue(app.isUseContainer());
        assertTrue(app.isAllowThirdPartyCookies());

        WebApp newApp = new WebApp("https://another.com", 2);
        newApp.applySettingsForNewWebApp();
        assertEquals(0, newApp.getContainerId());
        assertTrue(newApp.isUseContainer());
        assertTrue(newApp.isAllowThirdPartyCookies());
        assertFalse(newApp.isOverrideGlobalSettings());
    }

    @Test
    public void defect1_reorderingOrDeletingWebAppsDoesNotDisableValidShortcuts() {
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        Shadows.shadowOf(shortcutManager).setIsRequestPinShortcutSupported(true);

        Intent intent1 = new Intent(Intent.ACTION_VIEW).putExtra(Const.INTENT_WEBAPPID, 1);
        Intent intent2 = new Intent(Intent.ACTION_VIEW).putExtra(Const.INTENT_WEBAPPID, 2);
        ShortcutInfo sc1 = new ShortcutInfo.Builder(context, "app-1").setShortLabel("App 1").setIntent(intent1).build();
        ShortcutInfo sc2 = new ShortcutInfo.Builder(context, "app-2").setShortLabel("App 2").setIntent(intent2).build();
        shortcutManager.requestPinShortcut(sc1, null);
        shortcutManager.requestPinShortcut(sc2, null);
        assertEquals(2, shortcutManager.getPinnedShortcuts().size());

        WebApp app1 = new WebApp("https://one.example.com", 1);
        WebApp app2 = new WebApp("https://two.example.com", 2);
        ArrayList<WebApp> oldList = new ArrayList<>(Arrays.asList(app1, app2));
        ArrayList<WebApp> reorderedList = new ArrayList<>(Arrays.asList(app2, app1));

        ReflectionHelpers.callInstanceMethod(DataManager.getInstance(), "checkIfWebAppIdsCollide",
                ReflectionHelpers.ClassParameter.from(ArrayList.class, oldList),
                ReflectionHelpers.ClassParameter.from(ArrayList.class, reorderedList));

        for (ShortcutInfo info : shortcutManager.getPinnedShortcuts()) {
            assertTrue("Shortcut should remain enabled", info.isEnabled());
        }

        WebApp app1Changed = new WebApp("https://one-changed.example.com", 1);
        ArrayList<WebApp> changedList = new ArrayList<>(Arrays.asList(app1Changed, app2));
        ReflectionHelpers.callInstanceMethod(DataManager.getInstance(), "checkIfWebAppIdsCollide",
                ReflectionHelpers.ClassParameter.from(ArrayList.class, oldList),
                ReflectionHelpers.ClassParameter.from(ArrayList.class, changedList));

        java.util.Map<String, ShortcutInfo> disabled = ReflectionHelpers.getStaticField(
                org.robolectric.shadows.ShadowShortcutManager.class, "disabledPinnedShortcuts");
        java.util.Map<String, ShortcutInfo> active = ReflectionHelpers.getStaticField(
                org.robolectric.shadows.ShadowShortcutManager.class, "activePinnedShortcuts");
        assertTrue("Shortcut for app 1 should be disabled", disabled.containsKey("app-1"));
        assertTrue("Shortcut for app 2 should remain active", active.containsKey("app-2"));
    }

    @Test
    public void defect3_settingsActivityOnCreateLoadsAppDataAndPreservesSnapshot() {
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        GlobalSettings initialSettings = manager.getSettings();
        initialSettings.setThemeId(2);
        manager.saveGlobalSettings();

        ReflectionHelpers.setField(manager, "settings", new GlobalSettings());
        assertNull(manager.getSettings().getPersistenceSnapshot());

        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).create().pause()) {
            assertNotNull(manager.getSettings().getPersistenceSnapshot());
            assertEquals(2, manager.getSettings().getThemeId());
        }
    }

    @Test
    public void defect4_getWebAppDoesNotPolluteMemorySingletonOrBakeGlobalSettingsIntoJson() {
        DataManager manager = DataManager.getInstance();
        manager.getWebsites().clear();

        WebApp app = new WebApp("https://site.example.com", 1);
        app.setOverrideGlobalSettings(false);
        app.setAllowCookies(false);
        manager.addWebsite(app);

        assertTrue(manager.getSettings().getGlobalWebApp().isAllowCookies());

        WebApp runtimeApp = manager.getWebApp(1);
        assertNotNull(runtimeApp);
        assertTrue(runtimeApp.isAllowCookies());

        WebApp unoverridden = manager.getWebAppIgnoringGlobalOverride(1, true);
        assertFalse("Original site should retain its own false override", unoverridden.isAllowCookies());

        runtimeApp.setTitle("Updated Title");
        manager.replaceWebApp(runtimeApp);

        WebApp reloaded = manager.getWebAppIgnoringGlobalOverride(1, true);
        assertEquals("Updated Title", reloaded.getTitle());
        assertFalse("replaceWebApp should not have baked global setting into site config", reloaded.isAllowCookies());
    }

    @Test
    public void defect5_processRedirectHappensBeforeBiometricPrompt() {
        DataManager manager = DataManager.getInstance();
        WebApp app = new WebApp("https://secure.example.com", 42);
        app.setBiometricProtection(true);
        app.setContainerId(3);
        app.setUseContainer(true);
        manager.addWebsite(app);

        Intent intent = new Intent(context, WebViewActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, 42);

        try (ActivityController<WebViewActivity> controller =
                     Robolectric.buildActivity(WebViewActivity.class, intent).create()) {
            WebViewActivity activity = controller.get();
            assertTrue(activity.isFinishing());
            Intent nextIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull("Should redirect to target process component", nextIntent);
            assertEquals("com.cylonid.nativealpha.__WebViewActivity_3",
                    nextIntent.getComponent().getClassName());
        }
    }
    @Test
    public void newWebAppDefaultsHaveOptimalExperienceFeaturesEnabled() {
        WebApp app = new WebApp("https://example.com", 1);
        assertTrue(app.isUseAdblock());
        assertTrue(app.isSafeBrowsing());
        assertTrue(app.isAllowMediaPlaybackInBackground());
        assertTrue(app.isStripTrackingParams());
        assertTrue(app.isPullToRefresh());
        assertTrue(app.isEnableZooming());
        assertFalse(app.getAdBlockSettings().isEmpty());

        WebApp newApp = new WebApp("https://another.com", 2);
        newApp.applySettingsForNewWebApp();
        assertTrue(newApp.isUseAdblock());
        assertTrue(newApp.isSafeBrowsing());
        assertTrue(newApp.isAllowMediaPlaybackInBackground());
        assertTrue(newApp.isStripTrackingParams());
        assertTrue(newApp.isPullToRefresh());
        assertTrue(newApp.isEnableZooming());
        assertFalse(newApp.getAdBlockSettings().isEmpty());
    }

    @Test
    public void webAppDeserializerAppliesDefaultOnFeaturesForLegacyJson() {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .registerTypeAdapter(WebApp.class, new com.cylonid.nativealpha.model.deserializer.WebAppDeserializer())
                .create();
        String legacyJson = "{\"baseUrl\":\"https://legacy.example.com\",\"ID\":99}";
        WebApp deserialized = gson.fromJson(legacyJson, WebApp.class);
        assertTrue(deserialized.isUseAdblock());
        assertTrue(deserialized.isSafeBrowsing());
        assertTrue(deserialized.isAllowMediaPlaybackInBackground());
        assertTrue(deserialized.isStripTrackingParams());
        assertTrue(deserialized.isPullToRefresh());
        assertTrue(deserialized.isEnableZooming());
        assertFalse(deserialized.getAdBlockSettings().isEmpty());
    }
}
