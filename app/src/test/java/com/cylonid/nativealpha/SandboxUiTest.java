package com.cylonid.nativealpha;

import android.content.Intent;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.GlobalSettings;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.App;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.WebViewLauncher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SandboxUiTest {
    private static final String WEB_APP_PREFERENCES = "WEBSITEDATA";
    private static final String GENERAL_INFO_PREFERENCES =
            "com.cylonid.nativealpha.GENERAL_INFO";

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(WEB_APP_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(GENERAL_INFO_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        resetDataManager();
    }

    @After
    public void tearDown() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(WEB_APP_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(GENERAL_INFO_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        resetDataManager();
    }

    @Test
    public void managementModeShowsDefaultAndEightContainersAndExpandsApps() {
        WebApp app = addApp("https://example.org", 2);
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class).setup()) {
            SandboxActivity activity = controller.get();
            LinearLayout cards = activity.findViewById(R.id.sandboxCardList);
            assertEquals(9, cards.getChildCount());
            assertCardLabel(cards.getChildAt(0), activity.getString(R.string.sandbox_ui_default_name));
            assertCardLabel(cards.getChildAt(3), activity.getString(R.string.sandbox_ui_color_orange));

            View containerCard = cards.getChildAt(3);
            View expanded = containerCard.findViewById(R.id.sandboxExpandedContent);
            assertEquals(View.GONE, expanded.getVisibility());
            containerCard.findViewById(R.id.sandboxCardHeader).performClick();
            containerCard = cards.getChildAt(3);
            expanded = containerCard.findViewById(R.id.sandboxExpandedContent);
            assertEquals(View.VISIBLE, expanded.getVisibility());
            LinearLayout rows = containerCard.findViewById(R.id.sandboxAppList);
            assertEquals(1, rows.getChildCount());
            rows.getChildAt(0).performClick();
            Intent opened = org.robolectric.Shadows.shadowOf(activity)
                    .getNextStartedActivity();
            assertEquals(WebAppSettingsActivity.class.getName(), opened.getComponent().getClassName());
            assertEquals(app.getID(), opened.getIntExtra(Const.INTENT_WEBAPPID, -1));
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    public void narrowScreenUsesTouchTargetsAndLargeHeading() {
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class).setup()) {
            SandboxActivity activity = controller.get();
            View root = activity.findViewById(android.R.id.content);
            measure(root, 320, 640);
            TextView title = activity.findViewById(R.id.txtSandboxTitle);
            assertTrue(title.getTextSize() >= 20f * activity.getResources().getDisplayMetrics().scaledDensity);
            View card = ((android.view.ViewGroup) activity.findViewById(R.id.sandboxCardList)).getChildAt(0);
            measure(card, 288, 1000);
            assertTrue(card.getMeasuredWidth() <= 320);
            assertTrue(card.findViewById(R.id.sandboxCardHeader).getMinimumHeight() >=
                    (int) (48 * activity.getResources().getDisplayMetrics().density));
            assertEquals((int) (48 * activity.getResources().getDisplayMetrics().density),
                    card.findViewById(R.id.sandboxExpandIcon).getLayoutParams().width);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void chineseResourcesArePresent() {
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class).setup()) {
            SandboxActivity activity = controller.get();
            assertEquals("沙箱", ((TextView) activity.findViewById(R.id.txtSandboxTitle)).getText().toString());
            android.view.ViewGroup cardList = activity.findViewById(R.id.sandboxCardList);
            assertEquals("默认", ((TextView) cardList.getChildAt(0).findViewById(R.id.sandboxName)).getText().toString());
            assertEquals("绿色", ((TextView) cardList.getChildAt(2).findViewById(R.id.sandboxName)).getText().toString());
            assertEquals("橙色", ((TextView) cardList.getChildAt(3).findViewById(R.id.sandboxName)).getText().toString());
        }
    }

    @Test
    public void selectionSavesContainerAndSynchronizesFlag() {
        WebApp app = addApp("https://example.org", Const.NO_CONTAINER);
        app.setOrder(7);
        app.setActiveEntry(true);
        DataManager.getInstance().replaceWebApp(app);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SandboxActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class, intent).setup()) {
            SandboxActivity activity = controller.get();
            ((android.view.ViewGroup) activity.findViewById(R.id.sandboxCardList))
                    .getChildAt(4)
                    .findViewById(R.id.sandboxCardHeader)
                    .performClick();
            WebApp saved = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertEquals(3, saved.getContainerId());
            assertTrue(saved.isUseContainer());
            assertEquals(7, saved.getOrder());
            assertTrue(saved.isActiveEntry());
            assertEquals(SandboxActivity.RESULT_OK, org.robolectric.Shadows.shadowOf(activity).getResultCode());
            assertEquals(app.getID(), org.robolectric.Shadows.shadowOf(activity)
                    .getResultIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
            assertEquals(3, org.robolectric.Shadows.shadowOf(activity)
                    .getResultIntent().getIntExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, -99));
        }
    }

    @Test
    public void selectionOfDefaultClearsContainerAndFlag() {
        WebApp app = addApp("https://example.org", 5);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SandboxActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class, intent).setup()) {
            SandboxActivity activity = controller.get();
            ((android.view.ViewGroup) activity.findViewById(R.id.sandboxCardList))
                    .getChildAt(0)
                    .findViewById(R.id.sandboxCardHeader)
                    .performClick();
            WebApp saved = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertEquals(Const.NO_CONTAINER, saved.getContainerId());
            assertFalse(saved.isUseContainer());
        }
    }

    @Test
    public void enablingSwitchKeepsValidContainer() {
        WebApp app = new WebApp("https://example.org", 44);
        app.setContainerId(6);
        app.setUseContainer(false);

        app.onSwitchSandboxChanged(null, true);

        assertEquals(6, app.getContainerId());
        assertTrue(app.isUseContainer());
    }

    @Test
    public void cancelKeepsOriginalContainer() {
        WebApp app = addApp("https://example.org", 5);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SandboxActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class, intent).setup()) {
            controller.get().findViewById(R.id.btnSandboxBack).performClick();
            WebApp saved = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertEquals(5, saved.getContainerId());
            assertTrue(saved.isUseContainer());
            assertEquals(SandboxActivity.RESULT_CANCELED,
                    org.robolectric.Shadows.shadowOf(controller.get()).getResultCode());
        }
    }

    @Test
    public void legacyContainerRoutesToSandboxAndInvalidIdFallsBack() {
        WebApp legacy = new WebApp("https://example.org", 42);
        legacy.setContainerId(2);
        legacy.setUseContainer(false);
        Intent valid = WebViewLauncher.createWebViewIntent(
                legacy, RuntimeEnvironment.getApplication());
        assertNotNull(valid);
        assertEquals("com.cylonid.nativealpha.__WebViewActivity_2",
                valid.getComponent().getClassName());
        assertTrue(legacy.isUseContainer());

        WebApp invalid = new WebApp("https://example.org", 43);
        invalid.setContainerId(19);
        invalid.setUseContainer(true);
        Intent fallback = WebViewLauncher.createWebViewIntent(
                invalid, RuntimeEnvironment.getApplication());
        assertNotNull(fallback);
        assertEquals(WebViewActivity.class.getName(), fallback.getComponent().getClassName());
        assertEquals(Const.NO_CONTAINER, invalid.getContainerId());
        assertFalse(invalid.isUseContainer());
    }

    @Test
    public void settingsActivityPreservesOrderActiveContainerAndMonitorHash() {
        WebApp app = addApp("https://example.org/monitored", 3);
        app.setOrder(12);
        app.setActiveEntry(false);
        app.setLastMonitorHash("hash_meta_987");
        DataManager.getInstance().replaceWebApp(app);

        Intent intent = new Intent(RuntimeEnvironment.getApplication(), WebAppSettingsActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<WebAppSettingsActivity> controller =
                     Robolectric.buildActivity(WebAppSettingsActivity.class, intent).setup()) {
            WebAppSettingsActivity activity = controller.get();
            activity.findViewById(R.id.btnSave).performClick();

            WebApp saved = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertNotNull(saved);
            assertEquals(12, saved.getOrder());
            assertFalse(saved.isActiveEntry());
            assertEquals(3, saved.getContainerId());
            assertTrue(saved.isUseContainer());
            assertEquals("hash_meta_987", saved.getLastMonitorHash());
        }
    }

    private WebApp addApp(String url, int containerId) {
        DataManager.getInstance().loadAppData();
        WebApp app = new WebApp(url, DataManager.getInstance().getIncrementedID());
        app.setContainerId(containerId);
        app.setUseContainer(SandboxManager.isValidContainerId(containerId));
        DataManager.getInstance().addWebsite(app);
        return app;
    }

    private void assertCardLabel(View card, String expected) {
        assertEquals(expected, ((TextView) card.findViewById(R.id.sandboxName)).getText().toString());
    }

    private void measure(View view, int width, int height) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        );
        view.layout(0, 0, width, height);
    }

    private void resetDataManager() {
        DataManager dataManager = DataManager.getInstance();
        ReflectionHelpers.setField(dataManager, "websites", new ArrayList<WebApp>());
        ReflectionHelpers.setField(dataManager, "max_assigned_ID", -1);
        ReflectionHelpers.setField(dataManager, "settings", new GlobalSettings());
        ReflectionHelpers.setField(dataManager, "appdata", null);
    }
}
