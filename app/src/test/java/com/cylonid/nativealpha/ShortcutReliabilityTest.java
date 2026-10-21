package com.cylonid.nativealpha;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.ShortcutIconUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLooper;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ShortcutReliabilityTest {
    private Context context;
    private ShortcutManager shortcutManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        shortcutManager = context.getSystemService(ShortcutManager.class);
        Shadows.shadowOf(shortcutManager).setIsRequestPinShortcutSupported(true);
    }

    @Test
    public void sameTitleForDifferentAppsPinsBothWithTheirOwnIdentity() {
        WebApp first = app(41, "Shared title");
        WebApp second = app(72, "Shared title");

        assertTrue(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(first, first.getTitle())));
        assertTrue(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(second, second.getTitle())));

        assertEquals(2, shortcutManager.getPinnedShortcuts().size());
        assertEquals(41, pinned("lite-app-41").getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
        assertEquals(72, pinned("lite-app-72").getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
        assertEquals("Shared title", pinned("lite-app-41").getShortLabel().toString());
        assertEquals("Shared title", pinned("lite-app-72").getShortLabel().toString());
    }

    @Test
    public void renamedAppUpdatesItsPinnedShortcutWithoutAddingAnother() {
        WebApp app = app(41, "Original title");
        assertTrue(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(app, app.getTitle())));

        app.setTitle("Renamed title");
        assertTrue(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(app, app.getTitle())));

        assertEquals(1, shortcutManager.getPinnedShortcuts().size());
        assertEquals("Renamed title", pinned("lite-app-41").getShortLabel().toString());
        assertEquals("Renamed title", pinned("lite-app-41").getLongLabel().toString());
        assertEquals(41, pinned("lite-app-41").getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
    }

    @Test
    public void titleBasedLegacyShortcutIsLeftUntouched() {
        WebApp app = app(41, "Shared title");
        pinLegacy("Shared title", "Shared title", launchIntent(app(72, "Shared title")));

        assertTrue(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(app, app.getTitle())));

        assertEquals(2, shortcutManager.getPinnedShortcuts().size());
        assertEquals(72, pinned("Shared title").getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
        assertTrue(pinned("Shared title").isEnabled());
        assertEquals(41, pinned("lite-app-41").getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
    }

    @Test
    public void legacyTitleCollidingWithStableIdOfAnotherAppIsNotOverwritten() {
        pinLegacy("lite-app-41", "41", launchIntent(app(72, "41")));

        assertFalse(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(app(41, "New"), "New")));

        assertEquals(1, shortcutManager.getPinnedShortcuts().size());
        assertEquals("41", pinned("lite-app-41").getShortLabel().toString());
        assertEquals(72, pinned("lite-app-41").getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
        assertTrue(pinned("lite-app-41").isEnabled());
    }

    @Test
    public void legacyTitleCollidingWithStableIdWithoutOwnerIsNotOverwritten() {
        pinLegacy("lite-app-41", "Unknown owner", new Intent(Intent.ACTION_VIEW));

        assertFalse(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(app(41, "New"), "New")));

        assertEquals(1, shortcutManager.getPinnedShortcuts().size());
        assertEquals("Unknown owner", pinned("lite-app-41").getShortLabel().toString());
        assertTrue(pinned("lite-app-41").isEnabled());
    }

    @Test
    public void missingLaunchIntentIsRejectedBeforeBuilding() {
        assertNull(ShortcutDialogFragment.buildShortcutInfo(context, app(41, "Title"),
                "Title", null, icon()));
        assertTrue(shortcutManager.getPinnedShortcuts().isEmpty());
    }

    @Test
    public void blankLabelsFallBackAndCustomLabelSurvivesAnEmptyAppTitle() {
        WebApp app = app(41, " App title ");
        assertEquals("App title", shortcut(app, null).getShortLabel().toString());
        assertEquals("App title", shortcut(app, "  \n\t").getShortLabel().toString());

        app.setTitle("");
        assertEquals("Custom title", shortcut(app, " Custom title ").getShortLabel().toString());
        assertEquals(context.getString(R.string.app_name), shortcut(app, " ").getShortLabel().toString());
    }

    @Test
    public void unsupportedLauncherDoesNotPinAShortcut() {
        Shadows.shadowOf(shortcutManager).setIsRequestPinShortcutSupported(false);

        assertFalse(ShortcutDialogFragment.pinOrUpdateShortcut(context, shortcut(app(41, "Title"), "Title")));

        assertTrue(shortcutManager.getPinnedShortcuts().isEmpty());
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    public void recreationRestoresAppIdentityAndEditedTitleWithoutNetwork() {
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        manager.getWebsites().clear();
        WebApp app = app(41, "Original title");
        manager.addWebsite(app);
        assertTrue(ShortcutIconUtils.saveFavicon(context, app.getID(),
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)));

        boolean choreographerWasPaused = ShadowChoreographer.isPaused();
        ShadowChoreographer.setPaused(true);
        try (ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            OfflineShortcutDialogFragment fragment = new OfflineShortcutDialogFragment();
            fragment.setArguments(ShortcutDialogFragment.newInstance(app).getArguments());
            fragment.showNow(controller.get().getSupportFragmentManager(), "shortcut");
            dispatchPendingDialogMessages();
            EditText title = fragment.requireDialog().findViewById(R.id.websiteTitle);
            title.setText("My edited title");

            Bundle savedState = new Bundle();
            controller.saveInstanceState(savedState).pause().stop().destroy();
            try (ActivityController<TestActivity> controller2 = Robolectric.buildActivity(TestActivity.class)
                    .create(savedState).start().restoreInstanceState(savedState).postCreate(savedState).resume().visible()) {
                dispatchPendingDialogMessages();

                ShortcutDialogFragment restored = (ShortcutDialogFragment) controller2.get()
                        .getSupportFragmentManager().findFragmentByTag("shortcut");
                assertNotNull(restored);
                EditText restoredTitle = restored.requireDialog().findViewById(R.id.websiteTitle);
                assertEquals("My edited title", restoredTitle.getText().toString());
                ((AlertDialog) restored.requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();

                assertEquals(1, shortcutManager.getPinnedShortcuts().size());
                assertEquals("My edited title", pinned("lite-app-41").getShortLabel().toString());
                assertEquals(41, pinned("lite-app-41").getIntent().getIntExtra(Const.INTENT_WEBAPPID, -1));
            }
        } finally {
            ShadowChoreographer.setPaused(choreographerWasPaused);
            ShortcutIconUtils.deleteFavicon(context, app.getID());
        }
    }

    private void dispatchPendingDialogMessages() {
        // Deliver Dialog.onShow without draining an animation's self-posting frame callbacks.
        AtomicBoolean reachedMarker = new AtomicBoolean();
        new Handler(Looper.getMainLooper()).post(() -> reachedMarker.set(true));
        ShadowLooper looper = Shadows.shadowOf(Looper.getMainLooper());
        looper.idleFor(java.time.Duration.ofMillis(100));
        assertTrue("Dialog messages must be delivered within 500 tasks", reachedMarker.get());
    }

    @Test
    public void recreatedDialogForDeletedAppDoesNotUseAnotherAppAtItsOldPosition() {
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        manager.getWebsites().clear();
        manager.addWebsite(app(72, "Different app at index zero"));

        try (ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            OfflineShortcutDialogFragment fragment = new OfflineShortcutDialogFragment();
            fragment.setArguments(ShortcutDialogFragment.newInstance(app(0, "Deleted app")).getArguments());
            fragment.showNow(controller.get().getSupportFragmentManager(), "shortcut");

            assertNull(fragment.requireDialog().findViewById(R.id.websiteTitle));
            ((AlertDialog) fragment.requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(shortcutManager.getPinnedShortcuts().isEmpty());
        }
    }

    private WebApp app(int id, String title) {
        WebApp app = new WebApp("https://example.invalid/" + id, id);
        app.setTitle(title);
        return app;
    }

    private Intent launchIntent(WebApp app) {
        return new Intent(context, TestActivity.class).setAction(Intent.ACTION_VIEW)
                .putExtra(Const.INTENT_WEBAPPID, app.getID());
    }

    private IconCompat icon() {
        return IconCompat.createWithResource(context, R.mipmap.ic_shellalpha_fg);
    }

    private ShortcutInfoCompat shortcut(WebApp app, String label) {
        ShortcutInfoCompat shortcut = ShortcutDialogFragment.buildShortcutInfo(
                context, app, label, launchIntent(app), icon());
        assertNotNull(shortcut);
        return shortcut;
    }

    private void pinLegacy(String id, String label, Intent intent) {
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, id)
                .setShortLabel(label).setIntent(intent).build();
        assertTrue(shortcutManager.requestPinShortcut(shortcut, null));
    }

    private ShortcutInfo pinned(String id) {
        List<ShortcutInfo> shortcuts = shortcutManager.getPinnedShortcuts();
        return shortcuts.stream().filter(shortcut -> shortcut.getId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing pinned shortcut " + id));
    }

    public static class OfflineShortcutDialogFragment extends ShortcutDialogFragment {
        @Override
        public String[] fetchWebappData() {
            throw new AssertionError("Shortcut reliability tests must not fetch network data");
        }
    }

    public static class TestActivity extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.AppTheme);
            super.onCreate(state);
            setContentView(new android.view.View(this));
        }
    }
}
