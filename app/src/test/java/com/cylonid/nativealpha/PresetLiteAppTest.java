package com.cylonid.nativealpha;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.GlobalSettings;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.App;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.himanshurawat.hasher.HashType;
import com.himanshurawat.hasher.Hasher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PresetLiteAppTest {
    private static final String WEB_APP_PREFERENCES = "WEBSITEDATA";
    private static final String GENERAL_INFO_PREFERENCES = "com.cylonid.nativealpha.GENERAL_INFO";
    private static final String WEB_APPS_KEY = "WEBSITEDATA";
    private static final String MAX_ID_KEY = "MAX_ID";
    private static final String GLOBAL_SETTINGS_KEY = "GLOBALSETTINGS";
    private static final String POSTSOMA_URL = "https://postsoma-2050.website/";

    private DataManager dataManager;
    private SharedPreferences webAppPreferences;
    private Context originalAppContext;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        originalAppContext = App.getAppContext();
        context.getSharedPreferences(WEB_APP_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(GENERAL_INFO_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        webAppPreferences = context.getSharedPreferences(WEB_APP_PREFERENCES, Context.MODE_PRIVATE);
        dataManager = DataManager.getInstance();
        resetDataManager();
    }

    @After
    public void tearDown() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences(WEB_APP_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        RuntimeEnvironment.getApplication()
                .getSharedPreferences(GENERAL_INFO_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        resetDataManager();
        ReflectionHelpers.setStaticField(App.class, "context", originalAppContext);
    }

    @Test
    public void freshLoadSeedsOneActivePostSomaApp() {
        dataManager.loadAppData();

        List<WebApp> websites = dataManager.getWebsites();
        assertEquals(1, websites.size());
        WebApp app = websites.get(0);
        assertEquals(POSTSOMA_URL, app.getBaseUrl());
        assertEquals("PostSoma", app.getTitle());
        assertEquals(0, app.getID());
        assertEquals(1, app.getOrder());
        assertTrue(app.isActiveEntry());
        assertFalse(app.isOverrideGlobalSettings());
        assertTrue(webAppPreferences.contains(WEB_APPS_KEY));
        assertFalse(webAppPreferences.contains(GLOBAL_SETTINGS_KEY));
    }

    @Test
    public void freshSeedUsesLoadedGlobalSettingsWithoutChangingThem() {
        GlobalSettings settings = new GlobalSettings();
        settings.setSearchEngineUrl("https://search.example/?q=%s");
        settings.setClearCache(true);
        String storedSettings = new Gson().toJson(settings);
        webAppPreferences.edit()
                .putString(GLOBAL_SETTINGS_KEY, storedSettings)
                .commit();

        dataManager.loadAppData();

        assertEquals(1, dataManager.getWebsites().size());
        assertEquals("https://search.example/?q=%s",
                dataManager.getSettings().getSearchEngineUrl());
        assertTrue(dataManager.getSettings().isClearCache());
        assertEquals(storedSettings,
                webAppPreferences.getString(GLOBAL_SETTINGS_KEY, null));
    }

    @Test
    public void repeatedLoadAndProcessRestartDoNotDuplicateSeed() {
        dataManager.loadAppData();
        dataManager.loadAppData();

        resetDataManager();
        dataManager.loadAppData();

        assertEquals(1, dataManager.getWebsites().size());
        assertEquals(1, storedWebApps().size());
        assertEquals(POSTSOMA_URL, storedWebApps().get(0).getBaseUrl());
    }

    @Test
    public void inactiveOrDeletedSeedIsNotRecreated() {
        dataManager.loadAppData();
        WebApp app = dataManager.getWebsites().get(0);
        app.setActiveEntry(false);
        dataManager.saveWebAppData();

        resetDataManager();
        dataManager.loadAppData();

        assertEquals(1, dataManager.getWebsites().size());
        assertFalse(dataManager.getWebsites().get(0).isActiveEntry());
        assertEquals(0, dataManager.getActiveWebsitesCount());
        assertEquals(1, storedWebApps().size());
    }

    @Test
    public void existingListIsPreservedWithoutSeed() {
        ArrayList<WebApp> existing = new ArrayList<>();
        WebApp first = new WebApp("https://first.example/", 4, 2);
        first.setTitle("First");
        WebApp second = new WebApp("https://second.example/", 9, 3);
        second.setTitle("Second");
        second.setActiveEntry(false);
        existing.add(first);
        existing.add(second);
        webAppPreferences.edit()
                .putString(WEB_APPS_KEY, new Gson().toJson(existing))
                .putInt(MAX_ID_KEY, 9)
                .commit();

        dataManager.loadAppData();

        List<WebApp> loaded = dataManager.getWebsites();
        assertEquals(2, loaded.size());
        assertEquals("https://first.example/", loaded.get(0).getBaseUrl());
        assertEquals("First", loaded.get(0).getTitle());
        assertEquals(4, loaded.get(0).getID());
        assertEquals(2, loaded.get(0).getOrder());
        assertEquals("https://second.example/", loaded.get(1).getBaseUrl());
        assertFalse(loaded.get(1).isActiveEntry());
    }

    @Test
    public void restoredEmptyListIsPreservedWithoutSeed() throws Exception {
        JsonObject preferences = new JsonObject();
        preferences.addProperty(WEB_APPS_KEY, "[]");
        preferences.addProperty(MAX_ID_KEY, -1);
        JsonObject backup = new JsonObject();
        backup.addProperty("schema", 1);
        backup.add("preferences", preferences);
        backup.addProperty("checksum",
                Hasher.Companion.hash(new Gson().toJson(preferences), HashType.SHA_256));

        File backupFile = File.createTempFile(
                "empty-preset-backup", ".json",
                RuntimeEnvironment.getApplication().getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(backupFile)) {
                output.write(backup.toString().getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(dataManager.loadSharedPreferencesFromFile(Uri.fromFile(backupFile)));
            dataManager.loadAppData();

            assertTrue(webAppPreferences.contains(WEB_APPS_KEY));
            assertEquals(0, dataManager.getWebsites().size());
            assertEquals(0, storedWebApps().size());
        } finally {
            backupFile.delete();
        }
    }

    private List<WebApp> storedWebApps() {
        String json = webAppPreferences.getString(WEB_APPS_KEY, "[]");
        return new Gson().fromJson(json, new TypeToken<List<WebApp>>() {}.getType());
    }

    private void resetDataManager() {
        ReflectionHelpers.setField(dataManager, "websites", new ArrayList<WebApp>());
        ReflectionHelpers.setField(dataManager, "max_assigned_ID", -1);
        ReflectionHelpers.setField(dataManager, "settings", new GlobalSettings());
        ReflectionHelpers.setField(dataManager, "appdata", null);
    }
}
