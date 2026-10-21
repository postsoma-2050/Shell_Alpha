package com.cylonid.nativealpha;

import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.SandboxPreferences;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.EntryPointUtils;
import com.cylonid.nativealpha.util.WebViewLauncher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeEntryReliabilityTest {
    @Before
    public void setUp() {
        DataManager manager = DataManager.getInstance();
        manager.setEulaData(true);
        EntryPointUtils.markOnboardingCompleted(RuntimeEnvironment.getApplication());
        manager.loadAppData();
        manager.getWebsites().clear();
        manager.addWebsite(new WebApp("https://unrelated.example/", 1));
    }

    @Test
    public void normalizesHostInputAndRejectsUnsupportedOrMalformedUrls() {
        assertEquals("https://example.org/path", MainActivity.normalizedWebsiteUrl(" example.org/path "));
        assertEquals("HTTP://example.org/path", MainActivity.normalizedWebsiteUrl("HTTP://example.org/path"));
        for (String input : Arrays.asList("", "   ", "javascript:alert(1)", "file:///tmp/a",
                "https://", "https://user:pass@example.org", "https://example.org:99999", "not a URL")) {
            assertNull(input, MainActivity.normalizedWebsiteUrl(input));
        }
    }

    @Test
    public void matchingKeepsEverySandboxIdentityAndRejectsOtherOriginsAndInactiveApps() {
        WebApp first = new WebApp("https://EXAMPLE.org/", 41);
        first.setContainerId(0);
        WebApp second = new WebApp("https://example.org:443/account", 72);
        second.setContainerId(1);
        WebApp inactive = new WebApp("https://example.org/", 88);
        inactive.setActiveEntry(false);
        List<WebApp> matches = MainActivity.matchingWebApps("https://example.org/deep/path?q=1",
                Arrays.asList(first, second, inactive,
                        new WebApp("https://example.org.evil.invalid/", 90),
                        new WebApp("http://example.org/", 91),
                        new WebApp("https://example.org:8443/", 92)));
        assertEquals(Arrays.asList(first, second), matches);
    }

    @Test
    public void externalDeepLinkReusesAppIdAndSandboxAndKeepsTargetPath() {
        DataManager manager = DataManager.getInstance();
        WebApp existing = new WebApp("https://example.org/", 41);
        existing.setContainerId(2);
        existing.setUseContainer(true);
        manager.addWebsite(existing);
        int count = manager.getWebsites().size();
        String target = "https://example.org/deep/path?q=1#part";
        Intent incoming = new Intent(Intent.ACTION_VIEW, Uri.parse(target));

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.onNewIntent(incoming);
            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals(41, launched.getIntExtra(Const.INTENT_WEBAPPID, -1));
            assertEquals("com.cylonid.nativealpha.__WebViewActivity_2",
                    launched.getComponent().getClassName());
            assertEquals(target, launched.getStringExtra(WebViewLauncher.EXTRA_TARGET_URL));
            assertNull(incoming.getData());
            assertEquals(count, manager.getWebsites().size());
        }
    }

    @Test
    public void multipleMatchesShowSandboxIdentityAndLaunchOnlyChosenApp() {
        DataManager manager = DataManager.getInstance();
        WebApp first = new WebApp("https://example.org/", 41);
        first.setTitle("Shared title");
        first.setContainerId(0);
        WebApp second = new WebApp("https://example.org/", 72);
        second.setTitle("Shared title");
        second.setContainerId(1);
        manager.addWebsite(first);
        manager.addWebsite(second);
        SandboxPreferences.setName(0, "Personal");
        SandboxPreferences.setName(1, "Work");
        String target = "https://example.org/deep";
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.onNewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(target)));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            AlertDialog chooser = (AlertDialog) ShadowDialog.getLatestDialog();
            assertTrue(chooser.getListView().getAdapter().getItem(0).toString().contains("Personal"));
            assertTrue(chooser.getListView().getAdapter().getItem(1).toString().contains("Work"));
            chooser.getListView().performItemClick(null, 1, 1);
            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(72, launched.getIntExtra(Const.INTENT_WEBAPPID, -1));
            assertEquals(target, launched.getStringExtra(WebViewLauncher.EXTRA_TARGET_URL));
        }
    }

    @Test
    public void createFormOffersDefaultAndExplicitSandboxAndRestoresSelection() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.fab).performClick();
            AlertDialog form = (AlertDialog) ShadowDialog.getLatestDialog();
            Button select = form.findViewById(R.id.selectSandbox);
            assertTrue(select.getText().toString().contains(
                    SandboxPreferences.getName(Const.NO_CONTAINER)));
            ((EditText) form.findViewById(R.id.websiteUrl)).setText("https://example.org/deep");
            select.performClick();
            AlertDialog picker = (AlertDialog) ShadowDialog.getLatestDialog();
            assertEquals(9, picker.getListView().getAdapter().getCount());
            picker.getListView().performItemClick(null, 4, 4);
            String selected = select.getText().toString();
            assertTrue(selected.contains(SandboxPreferences.getName(3)));
            controller.recreate();
            AlertDialog restored = (AlertDialog) ShadowDialog.getLatestDialog();
            assertEquals(selected,
                    ((TextView) restored.findViewById(R.id.selectSandbox)).getText().toString());
            assertEquals("https://example.org/deep",
                    ((EditText) restored.findViewById(R.id.websiteUrl)).getText().toString());
        }
    }
}
