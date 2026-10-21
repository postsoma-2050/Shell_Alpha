package com.cylonid.nativealpha;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.util.EntryPointUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w320dp-h640dp")
public class HomeNavigationTest {

    @Before
    public void setUp() {
        DataManager.getInstance().setEulaData(true);
        EntryPointUtils.markOnboardingCompleted(RuntimeEnvironment.getApplication());
        DataManager.getInstance().loadAppData();
    }

    @Test
    public void hamburgerOpensDrawerAndBackClosesItFirst() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            DrawerLayout drawer = activity.findViewById(R.id.homeDrawerLayout);
            ImageButton hamburger = activity.findViewById(R.id.btnMainDrawer);

            assertNotNull(drawer);
            assertNotNull(hamburger);
            hamburger.performClick();
            assertTrue(drawer.isDrawerOpen(GravityCompat.START));

            activity.getOnBackPressedDispatcher().onBackPressed();
            assertFalse(drawer.isDrawerOpen(GravityCompat.START));
            assertFalse(activity.isFinishing());

            activity.getOnBackPressedDispatcher().onBackPressed();
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void sandboxNavigationClosesDrawerAndTargetsSandboxActivity() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            DrawerLayout drawer = activity.findViewById(R.id.homeDrawerLayout);
            View sandboxes = activity.findViewById(R.id.navSandboxes);

            activity.openHomeNavigation();
            sandboxes.performClick();
            assertFalse(drawer.isDrawerOpen(GravityCompat.START));

            ShadowActivity shadowActivity = Shadows.shadowOf(activity);
            Intent intent = shadowActivity.getNextStartedActivity();
            assertNotNull(intent);
            assertEquals(
                    "com.cylonid.nativealpha.SandboxActivity",
                    intent.getComponent().getClassName());
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    public void navigationLayoutsExposeMatchingIdsAndChineseLabels() {
        int[] ids = {
                R.id.homeDrawerLayout,
                R.id.homeNavigationDrawer,
                R.id.navLiteApps,
                R.id.navSandboxes,
                R.id.navGlobalSettings,
                R.id.navAbout
        };

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertNavigationIds(activity.findViewById(android.R.id.content), ids);

            TextView sandboxLabel = findFirstTextView(activity.findViewById(R.id.navSandboxes));
            assertNotNull(sandboxLabel);
            assertEquals("沙盒", sandboxLabel.getText().toString());
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w480dp-h800dp")
    public void wideNavigationLayoutExposesMatchingIdsAndChineseLabels() {
        int[] ids = {
                R.id.homeDrawerLayout,
                R.id.homeNavigationDrawer,
                R.id.navLiteApps,
                R.id.navSandboxes,
                R.id.navGlobalSettings,
                R.id.navAbout
        };

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertNavigationIds(activity.findViewById(android.R.id.content), ids);

            TextView sandboxLabel = findFirstTextView(activity.findViewById(R.id.navSandboxes));
            assertNotNull(sandboxLabel);
            assertEquals("沙盒", sandboxLabel.getText().toString());
        }
    }

    private void assertNavigationIds(View root, int[] ids) {
        for (int id : ids) {
            assertNotNull(root.findViewById(id));
        }
    }

    private TextView findFirstTextView(View view) {
        if (view instanceof TextView) return (TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findFirstTextView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
