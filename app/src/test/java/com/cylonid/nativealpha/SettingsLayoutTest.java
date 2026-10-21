package com.cylonid.nativealpha;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.content.Intent;

import com.cylonid.nativealpha.databinding.GlobalSettingsBinding;
import com.cylonid.nativealpha.databinding.WebappSettingsBinding;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w320dp-h640dp")
public class SettingsLayoutTest {
    private LayoutInflater inflater() {
        return LayoutInflater.from(new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.AppTheme));
    }

    @Test public void appSettingsInflatesAndMeasuresOnSmallPhone() {
        View root = WebappSettingsBinding.inflate(inflater()).getRoot();
        checkScrollChildren(root);
        measure(root);
    }

    @Test public void globalSettingsInflatesAndMeasuresOnSmallPhone() {
        View root = GlobalSettingsBinding.inflate(inflater()).getRoot();
        checkScrollChildren(root);
        measure(root);
    }

    @Test public void globalSettingsActivityOpens() {
        DataManager.getInstance().loadAppData();
        try (ActivityController<SettingsActivity> controller =
                     Robolectric.buildActivity(SettingsActivity.class).setup()) {
            measure(controller.get().findViewById(android.R.id.content));
        }
    }

    @Test public void websiteDefaultsOpensAndAllTabsMeasureOnSmallPhone() {
        DataManager.getInstance().loadAppData();
        int id = DataManager.getInstance().getSettings().getGlobalWebApp().getID();
        checkAppSettingsActivity(id);
    }

    @Test public void individualAppSettingsOpensAndAllTabsMeasureOnSmallPhone() {
        DataManager.getInstance().loadAppData();
        WebApp app = new WebApp("https://example.org", DataManager.getInstance().getIncrementedID());
        DataManager.getInstance().addWebsite(app);
        checkAppSettingsActivity(app.getID());
    }

    @Test public void identitySettingsRemainEnabledWhenGlobalOverrideIsFalse() {
        DataManager.getInstance().loadAppData();
        WebApp app = new WebApp("https://example.org", DataManager.getInstance().getIncrementedID());
        app.setOverrideGlobalSettings(false);
        DataManager.getInstance().addWebsite(app);

        Intent intent = new Intent(RuntimeEnvironment.getApplication(), WebAppSettingsActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<WebAppSettingsActivity> controller =
                     Robolectric.buildActivity(WebAppSettingsActivity.class, intent).setup()) {
            WebAppSettingsActivity activity = controller.get();
            View txtName = activity.findViewById(R.id.txtWebAppName);
            View btnShortcut = activity.findViewById(R.id.btnRecreateShortcut);
            View sandboxCard = activity.findViewById(R.id.cardSettingsSandbox);
            View colorGrid = activity.findViewById(R.id.settingsColorGrid);
            View behaviorContainer = activity.findViewById(R.id.tabContainerBehavior);

            assertTrue("App name must remain editable", txtName.isEnabled());
            assertTrue("Shortcut button must remain clickable", btnShortcut.isEnabled());
            assertTrue("Sandbox card must remain clickable", sandboxCard.isEnabled());
            assertTrue("Theme color grid must remain enabled", colorGrid.isEnabled());
            assertFalse("Behavior container must be disabled when override is false", behaviorContainer.isEnabled());
        }
    }

    @Test public void sandboxEntryAndCookiePolicyUseButtonsWithTouchTargets() {
        WebappSettingsBinding binding = WebappSettingsBinding.inflate(inflater());
        measure(binding.getRoot());
        int touchSize = (int) (48 * binding.getRoot().getResources().getDisplayMetrics().density);
        int[] entries = {R.id.txtSettingsSandbox, R.id.switchCookies};
        for (int id : entries) {
            View entry = binding.getRoot().findViewById(id);
            assertTrue(entry instanceof com.google.android.material.button.MaterialButton);
            assertFalse(entry instanceof CompoundButton);
            assertTrue(entry.getMinimumHeight() >= touchSize);
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    public void chineseSandboxManagementAndRecoveryLayoutMeasures() {
        View root = inflater().inflate(R.layout.activity_sandbox, null);
        checkScrollChildren(root);
        measure(root);
        View card = inflater().inflate(R.layout.item_sandbox_card, null);
        card.findViewById(R.id.sandboxExpandedContent).setVisibility(View.VISIBLE);
        card.findViewById(R.id.sandboxLegacyRecovery).setVisibility(View.VISIBLE);
        measure(card);
        assertNotNull(card.findViewById(R.id.spinnerSandboxLegacySession));
        assertNotNull(card.findViewById(R.id.btnSandboxRecoverSession));
    }

    @Test public void backupImportOnSdk28DoesNotThrowNoSuchMethodError() throws Exception {
        java.io.File temp = java.io.File.createTempFile("backup", ".txt", RuntimeEnvironment.getApplication().getCacheDir());
        java.io.FileOutputStream fos = new java.io.FileOutputStream(temp);
        fos.write("invalid-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fos.close();

        android.net.Uri uri = android.net.Uri.fromFile(temp);
        boolean result = DataManager.getInstance().loadSharedPreferencesFromFile(uri);
        assertFalse(result);
        temp.delete();
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    public void verifyChineseHomeAndSettingsVisibleLabels() {
        android.content.Context ctx = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.AppTheme);
        String addWebapp = ctx.getString(R.string.add_webapp);
        assertNotNull(addWebapp);
        assertTrue(!addWebapp.isEmpty());

        String sandboxDefault = ctx.getString(R.string.sandbox_default_count, 3);
        assertTrue(sandboxDefault.contains("3"));

        String sandboxItem = ctx.getString(R.string.sandbox_item_count, 1, 5);
        assertTrue(sandboxItem.contains("1") && sandboxItem.contains("5"));

        String userscriptsMsg = ctx.getString(R.string.userscripts_empty_message);
        assertTrue(userscriptsMsg.contains("\n"));
        assertFalse(userscriptsMsg.contains("\\n"));

        String webmonitorsMsg = ctx.getString(R.string.webmonitors_empty_message);
        assertTrue(webmonitorsMsg.contains("\n"));
        assertFalse(webmonitorsMsg.contains("\\n"));

        View root = LayoutInflater.from(ctx).inflate(R.layout.activity_toolbar_base, null);
        assertNotNull(root);
    }

    private void checkAppSettingsActivity(int id) {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), WebAppSettingsActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, id);
        try (ActivityController<WebAppSettingsActivity> controller =
                     Robolectric.buildActivity(WebAppSettingsActivity.class, intent).setup()) {
            WebAppSettingsActivity activity = controller.get();
            int[] tabs = {R.id.btnTabBehavior, R.id.btnTabPrivacy, R.id.btnTabTheme, R.id.btnTabTools};
            for (int tab : tabs) {
                activity.findViewById(tab).performClick();
                measure(activity.findViewById(android.R.id.content));
            }
            GridLayout palette = activity.findViewById(R.id.settingsColorGrid);
            assertEquals(com.cylonid.nativealpha.util.ColorPalette.INSTANCE.getHERMIT_COLORS().size(), palette.getChildCount());
        }
    }

    @Test public void identityCardQuickEditIsClickableAndDisplaysDialog() {
        DataManager.getInstance().loadAppData();
        WebApp app = new WebApp("https://example.org", DataManager.getInstance().getIncrementedID());
        app.setTitle("Example Lite App");
        DataManager.getInstance().addWebsite(app);

        Intent intent = new Intent(RuntimeEnvironment.getApplication(), WebAppSettingsActivity.class)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
        try (ActivityController<WebAppSettingsActivity> controller =
                     Robolectric.buildActivity(WebAppSettingsActivity.class, intent).setup()) {
            WebAppSettingsActivity activity = controller.get();
            View identityCard = activity.findViewById(R.id.appIdentityCard);
            View btnEdit = activity.findViewById(R.id.btnEditIdentity);

            assertNotNull(identityCard);
            assertNotNull(btnEdit);
            assertTrue("Identity card must be clickable", identityCard.isClickable());
            assertTrue("Edit button must be clickable", btnEdit.isClickable());
            assertEquals(View.VISIBLE, btnEdit.getVisibility());

            btnEdit.performClick();
            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("Edit dialog should appear upon clicking edit button", dialog);
            assertTrue(dialog.isShowing());
        }
    }

    private void checkScrollChildren(View view) {
        if (view instanceof ScrollView) {
            assertEquals("ScrollView requires a single content container", 1,
                    ((ScrollView) view).getChildCount());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkScrollChildren(group.getChildAt(i));
        }
    }

    private void measure(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 320, 640);
    }
}
