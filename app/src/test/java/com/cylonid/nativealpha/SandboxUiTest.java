package com.cylonid.nativealpha;

import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.GlobalSettings;
import com.cylonid.nativealpha.model.Sandbox;
import com.cylonid.nativealpha.model.SandboxManager;
import com.cylonid.nativealpha.model.SandboxPreferences;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.App;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.WebViewLauncher;
import com.cylonid.nativealpha.adapter.SandboxChoiceAdapter;

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
        com.cylonid.nativealpha.util.EntryPointUtils.markOnboardingCompleted(context);
        DataManager.getInstance().setEulaData(true);
        resetDataManager();
        resetSandboxPreferences();
    }

    @After
    public void tearDown() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(WEB_APP_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(GENERAL_INFO_PREFERENCES, Context.MODE_PRIVATE)
                .edit().clear().commit();
        resetDataManager();
        resetSandboxPreferences();
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
            assertEquals("管理沙盒", ((TextView) activity.findViewById(R.id.txtSandboxTitle)).getText().toString());
            android.view.ViewGroup cardList = activity.findViewById(R.id.sandboxCardList);
            assertEquals("默认", ((TextView) cardList.getChildAt(0).findViewById(R.id.sandboxName)).getText().toString());
            assertEquals("绿色", ((TextView) cardList.getChildAt(2).findViewById(R.id.sandboxName)).getText().toString());
            assertEquals("橙色", ((TextView) cardList.getChildAt(3).findViewById(R.id.sandboxName)).getText().toString());
        }
    }

    @Test
    public void containerTracksMultipleAppsWithoutEviction() {
        Sandbox sandbox = new Sandbox(0);

        sandbox.registerWebApp(10);
        sandbox.registerWebApp(11);

        assertEquals(2, sandbox.getRegisteredWebAppCount());
        assertTrue(sandbox.containsWebApp(10));
        assertTrue(sandbox.containsWebApp(11));
        assertFalse(sandbox.isUnoccupied());

        sandbox.unregisterWebApp(10);

        assertFalse(sandbox.containsWebApp(10));
        assertTrue(sandbox.containsWebApp(11));
    }

    @Test
    public void selectionOnlySavesAfterApplyAndSynchronizesFlag() {
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
            assertFalse(activity.isFinishing());
            assertEquals(Const.NO_CONTAINER,
                    DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true).getContainerId());
            assertTrue(((android.view.ViewGroup) activity.findViewById(R.id.sandboxCardList))
                    .getChildAt(4).isSelected());
            activity.findViewById(R.id.btnSandboxApply).performClick();
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
            activity.findViewById(R.id.btnSandboxApply).performClick();
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
            ((android.view.ViewGroup) controller.get().findViewById(R.id.sandboxCardList))
                    .getChildAt(2).findViewById(R.id.sandboxCardHeader).performClick();
            controller.get().findViewById(R.id.btnSandboxBack).performClick();
            WebApp saved = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertEquals(5, saved.getContainerId());
            assertTrue(saved.isUseContainer());
            assertEquals(SandboxActivity.RESULT_CANCELED,
                    org.robolectric.Shadows.shadowOf(controller.get()).getResultCode());
        }
    }

    @Test
    public void pickOnlyUsesDraftAndReturnsIdWithoutSavingAnyConfiguration() {
        WebApp app = addApp("https://example.org", 5);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SandboxActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID())
                .putExtra(SandboxActivity.EXTRA_PICK_ONLY, true)
                .putExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, 2);
        String originalName = SandboxPreferences.getName(1);
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class, intent).setup()) {
            SandboxActivity activity = controller.get();
            android.view.ViewGroup cards = activity.findViewById(R.id.sandboxCardList);
            assertTrue(cards.getChildAt(3).isSelected());
            assertEquals(View.GONE, activity.findViewById(R.id.btnSandboxApply).getVisibility());
            assertEquals(View.GONE, cards.getChildAt(2)
                    .findViewById(R.id.sandboxPolicyEditor).getVisibility());
            cards.getChildAt(2).findViewById(R.id.sandboxCardHeader).performClick();

            assertEquals(5, DataManager.getInstance()
                    .getWebAppIgnoringGlobalOverride(app.getID(), true).getContainerId());
            assertEquals(originalName, SandboxPreferences.getName(1));
            assertTrue(SandboxPreferences.isCookiesAllowed(1));
            assertEquals(SandboxActivity.RESULT_OK, org.robolectric.Shadows.shadowOf(activity).getResultCode());
            assertEquals(1, org.robolectric.Shadows.shadowOf(activity).getResultIntent()
                    .getIntExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, -99));
            assertEquals(1, org.robolectric.Shadows.shadowOf(activity).getResultIntent().getExtras().size());
        }
    }

    @Test
    public void pickOnlyCanChooseDefaultWithoutAnApp() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SandboxActivity.class)
                .putExtra(SandboxActivity.EXTRA_PICK_ONLY, true)
                .putExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, 3);
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class, intent).setup()) {
            SandboxActivity activity = controller.get();
            ((android.view.ViewGroup) activity.findViewById(R.id.sandboxCardList))
                    .getChildAt(0).findViewById(R.id.sandboxCardHeader).performClick();
            assertEquals(Const.NO_CONTAINER, org.robolectric.Shadows.shadowOf(activity)
                    .getResultIntent().getIntExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, -99));
        }
    }

    @Test
    public void pendingSelectionSurvivesRecreationWithoutWriting() {
        WebApp app = addApp("https://example.org", 5);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SandboxActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class, intent).setup()) {
            ((android.view.ViewGroup) controller.get().findViewById(R.id.sandboxCardList))
                    .getChildAt(2).findViewById(R.id.sandboxCardHeader).performClick();
            controller.recreate();
            assertTrue(((android.view.ViewGroup) controller.get().findViewById(R.id.sandboxCardList))
                    .getChildAt(2).isSelected());
            assertEquals(5, DataManager.getInstance()
                    .getWebAppIgnoringGlobalOverride(app.getID(), true).getContainerId());
        }
    }

    @Test
    public void managementSavesNameAndCookiePolicyWithoutMovingApps() {
        WebApp first = addApp("https://example.org/one", 0);
        WebApp second = addApp("https://example.org/two", 0);
        try (ActivityController<SandboxActivity> controller =
                     Robolectric.buildActivity(SandboxActivity.class).setup()) {
            SandboxActivity activity = controller.get();
            android.view.ViewGroup cards = activity.findViewById(R.id.sandboxCardList);
            cards.getChildAt(1).findViewById(R.id.sandboxCardHeader).performClick();
            ((EditText) cards.getChildAt(1).findViewById(R.id.sandboxNameInput)).setText("Work");
            cards.getChildAt(1).findViewById(R.id.btnSandboxSaveName).performClick();
            assertEquals("Work", SandboxPreferences.getName(0));
            assertCardLabel(cards.getChildAt(1), "Work");
            ((CompoundButton) cards.getChildAt(1).findViewById(R.id.switchSandboxCookies))
                    .setChecked(false);
            assertFalse(SandboxPreferences.isCookiesAllowed(0));
            assertTrue(SandboxPreferences.isCookiesAllowed(1));
            assertEquals(0, DataManager.getInstance()
                    .getWebAppIgnoringGlobalOverride(first.getID(), true).getContainerId());
            assertEquals(0, DataManager.getInstance()
                    .getWebAppIgnoringGlobalOverride(second.getID(), true).getContainerId());
            assertEquals(View.GONE, cards.getChildAt(0).findViewById(R.id.sandboxLegacyRecovery).getVisibility());
        }
    }

    @Test
    public void settingsPickerEditsDraftAndDiscardKeepsOriginal() {
        WebApp app = addApp("https://example.org", 5);
        try (ActivityController<WebAppSettingsActivity> controller = settingsController(app)) {
            WebAppSettingsActivity activity = controller.get();
            returnSandboxChoice(activity, R.id.txtSettingsSandbox, 2);
            Intent secondPicker = openPicker(activity, R.id.cardSettingsSandbox);
            assertEquals(2, secondPicker.getIntExtra(SandboxActivity.EXTRA_SELECTED_CONTAINER_ID, -99));
            assertEquals(5, DataManager.getInstance()
                    .getWebAppIgnoringGlobalOverride(app.getID(), true).getContainerId());
            activity.findViewById(R.id.btnCancel).performClick();
            assertFalse(activity.isFinishing());
            latestExitDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(activity.isFinishing());
            assertEquals(WebAppSettingsActivity.RESULT_CANCELED,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
            assertEquals(5, DataManager.getInstance()
                    .getWebAppIgnoringGlobalOverride(app.getID(), true).getContainerId());
        }
    }

    @Test
    public void settingsBackPromptsAndCanSaveDraft() {
        WebApp app = addApp("https://example.org", 5);
        try (ActivityController<WebAppSettingsActivity> controller = settingsController(app)) {
            WebAppSettingsActivity activity = controller.get();
            returnSandboxChoice(activity, R.id.txtSettingsSandbox, Const.NO_CONTAINER);
            activity.getOnBackPressedDispatcher().onBackPressed();
            assertFalse(activity.isFinishing());
            latestExitDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            WebApp saved = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertEquals(Const.NO_CONTAINER, saved.getContainerId());
            assertFalse(saved.isUseContainer());
            assertEquals(WebAppSettingsActivity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void unchangedSettingsBackExitsAndDraftListenersNeverChangeLiveApp() {
        WebApp app = addApp("https://example.org", 1);
        try (ActivityController<WebAppSettingsActivity> controller = settingsController(app)) {
            controller.get().getOnBackPressedDispatcher().onBackPressed();
            assertTrue(controller.get().isFinishing());
            assertEquals(WebAppSettingsActivity.RESULT_CANCELED,
                    org.robolectric.Shadows.shadowOf(controller.get()).getResultCode());
        }
        try (ActivityController<WebAppSettingsActivity> controller = settingsController(app)) {
            WebAppSettingsActivity activity = controller.get();
            ((CompoundButton) activity.findViewById(R.id.switchExpertSettings)).setChecked(true);
            ((CompoundButton) activity.findViewById(R.id.switchOverrideGlobal)).setChecked(false);
            assertFalse(app.isShowExpertSettings());
            assertTrue(app.isOverrideGlobalSettings());
            assertTrue(activity.findViewById(R.id.cardSettingsSandbox).isClickable());
            assertTrue(activity.findViewById(R.id.switchCookies).isClickable());
        }
    }

    @Test
    public void recreatedDraftSavesWithoutOverwritingConcurrentFields() {
        WebApp app = addApp("https://example.org", 1);
        try (ActivityController<WebAppSettingsActivity> controller = settingsController(app)) {
            returnSandboxChoice(controller.get(), R.id.txtSettingsSandbox, 4);
            controller.get().findViewById(R.id.btnTabTools).performClick();
            ((EditText) controller.get().findViewById(R.id.txtWebAppName)).setText("Draft name");
            controller.recreate();
            assertTrue(controller.get().findViewById(R.id.btnTabTools).isSelected());
            WebApp concurrent = new WebApp(DataManager.getInstance()
                    .getWebAppIgnoringGlobalOverride(app.getID(), true));
            concurrent.setOrder(27);
            DataManager.getInstance().replaceWebApp(concurrent);
            controller.get().findViewById(R.id.btnSave).performClick();
            WebApp saved = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertEquals("Draft name", saved.getTitle());
            assertEquals(4, saved.getContainerId());
            assertEquals(27, saved.getOrder());
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

    private ActivityController<WebAppSettingsActivity> settingsController(WebApp app) {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), WebAppSettingsActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        return Robolectric.buildActivity(WebAppSettingsActivity.class, intent).setup();
    }

    private Intent openPicker(WebAppSettingsActivity activity, int entryId) {
        activity.findViewById(entryId).performClick();
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog)
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        if (dialog != null && dialog.isShowing()) {
            android.widget.Button btn = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (btn != null) btn.performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        Intent intent = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        if (intent == null) {
            org.robolectric.shadows.ShadowActivity.IntentForResult ifr =
                    org.robolectric.Shadows.shadowOf(activity).getNextStartedActivityForResult();
            if (ifr != null) intent = ifr.intent;
        }
        assertNotNull("Expected started activity from neutral button", intent);
        assertEquals(SandboxActivity.class.getName(), intent.getComponent().getClassName());
        assertTrue(intent.getBooleanExtra(SandboxActivity.EXTRA_PICK_ONLY, false));
        return intent;
    }

    private void returnSandboxChoice(WebAppSettingsActivity activity, int entryId, int containerId) {
        activity.findViewById(entryId).performClick();
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog)
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        android.widget.ListView listView = dialog.getListView();
        assertNotNull(listView);
        for (int i = 0; i < listView.getAdapter().getCount(); i++) {
            SandboxChoiceAdapter.Item item = (SandboxChoiceAdapter.Item) listView.getAdapter().getItem(i);
            if (item.getContainerId() == containerId) {
                listView.performItemClick(listView.getChildAt(i), i, listView.getAdapter().getItemId(i));
                return;
            }
        }
        org.junit.Assert.fail("Container ID " + containerId + " not found in dialog");
    }

    @Test
    public void webAppSettingsSandboxChoiceDialogUpdatesDraftImmediately() {
        WebApp app = addApp("https://example.org", 1);
        try (ActivityController<WebAppSettingsActivity> controller = settingsController(app)) {
            WebAppSettingsActivity activity = controller.get();
            activity.findViewById(R.id.txtSettingsSandbox).performClick();
            androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog)
                    org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            android.widget.ListView listView = dialog.getListView();
            assertNotNull(listView);
            assertEquals(9, listView.getAdapter().getCount());
            listView.performItemClick(null, 4, listView.getAdapter().getItemId(4));
            assertFalse(dialog.isShowing());
            String expectedName = SandboxPreferences.getName(3);
            assertTrue(((TextView) activity.findViewById(R.id.txtSettingsSandbox)).getText().toString().contains(expectedName));
        }
    }

    @Test
    public void quickSettingsShowsEightSandboxesAndSwitchesProcess() {
        WebApp app = addApp("https://example.org", Const.NO_CONTAINER);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), WebViewActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<WebViewActivity> controller =
                     Robolectric.buildActivity(WebViewActivity.class, intent).setup()) {
            WebViewActivity activity = controller.get();
            activity.showSandboxPickerDialog();
            androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog)
                    org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            android.widget.ListView listView = dialog.getListView();
            assertNotNull(listView);
            assertEquals(9, listView.getAdapter().getCount());
            listView.performItemClick(null, 4, listView.getAdapter().getItemId(4));
            WebApp updated = DataManager.getInstance().getWebAppIgnoringGlobalOverride(app.getID(), true);
            assertEquals(3, updated.getContainerId());
            assertTrue(updated.isUseContainer());
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void launchSandbox0Activity() {
        WebApp app = addApp("https://example.org", 0);
        Intent intent = WebViewLauncher.createWebViewIntent(app, RuntimeEnvironment.getApplication());
        try (ActivityController<__WebViewActivity_0> controller =
                     Robolectric.buildActivity(__WebViewActivity_0.class, intent).setup()) {
            assertNotNull(controller.get());
        }
    }

    private androidx.appcompat.app.AlertDialog latestExitDialog() {
        return (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
    }

    private void resetSandboxPreferences() {
        SandboxPreferences.setName(Const.NO_CONTAINER, "");
        SandboxPreferences.setCookiesAllowed(Const.NO_CONTAINER, true);
        for (int id = 0; id < SandboxManager.getSandboxCount(); id++) {
            SandboxPreferences.setName(id, "");
            SandboxPreferences.setCookiesAllowed(id, true);
        }
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
