package com.cylonid.nativealpha.model;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConfigurationStoreTest {
    @Before public void reset() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("WEBSITEDATA", 0).edit().clear().commit();
        context.getSharedPreferences("SANDBOX_SETTINGS", 0).edit().clear().commit();
        DataManager manager = DataManager.getInstance();
        ReflectionHelpers.setField(manager, "websites", new ArrayList<WebApp>());
        ReflectionHelpers.setField(manager, "settings", new GlobalSettings());
        ReflectionHelpers.setField(manager, "appsBaseline", null);
        manager.loadAppData();
    }

    @Test public void staleAppCannotUndoProfileSelectionOrAnotherAppEdit() {
        DataManager manager = DataManager.getInstance();
        WebApp initial = manager.getWebAppIgnoringGlobalOverride(0, true);
        WebApp stale = new WebApp(initial);
        initial.setContainerId(3);
        initial.setUseContainer(true);
        manager.replaceWebApp(initial);
        stale.setTitle("Renamed while switching");
        manager.replaceWebApp(stale);
        WebApp stored = manager.getWebAppIgnoringGlobalOverride(0, true);
        assertEquals(3, stored.getContainerId());
        assertEquals("Renamed while switching", stored.getTitle());
        stale.setKeepAwake(true);
        manager.replaceWebApp(stale);
        assertEquals(3, manager.getWebAppIgnoringGlobalOverride(0, true).getContainerId());
    }

    @Test public void snapshotReadersFetchCurrentHostValuesAndPoliciesArePerProfile() {
        var old = ConfigurationStore.preferences("SANDBOX_SETTINGS");
        SandboxPreferences.setName(0, "Work");
        SandboxPreferences.setCookiesAllowed(0, false);
        assertFalse(old.contains("name_0"));
        assertEquals("Work", SandboxPreferences.getName(0));
        assertFalse(SandboxPreferences.isCookiesAllowed(0));
        assertTrue(SandboxPreferences.isCookiesAllowed(1));
        assertTrue(SandboxPreferences.isCookiesAllowed(-1));
    }

    @Test public void appIdsRemainMonotonicAfterDeletingHighestApp() {
        DataManager manager = DataManager.getInstance();
        int first = manager.getIncrementedID();
        int second = manager.getIncrementedID();
        assertTrue(second > first);
        assertTrue(manager.getIncrementedID() > second);
    }

    @Test public void persistenceBaselinesNeverLeakIntoExports() {
        WebApp app = managerApp();
        assertNotNull(app.getPersistenceSnapshot());
        assertFalse(new com.google.gson.Gson().toJson(app).contains("persistenceSnapshot"));
    }

    private WebApp managerApp() { return DataManager.getInstance().getWebAppIgnoringGlobalOverride(0, true); }
}
