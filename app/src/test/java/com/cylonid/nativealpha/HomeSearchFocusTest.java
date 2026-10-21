package com.cylonid.nativealpha;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import org.robolectric.shadows.ShadowInputMethodManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeSearchFocusTest {

    @Before
    public void setUp() {
        DataManager.getInstance().setEulaData(true);
        EntryPointUtils.markOnboardingCompleted(RuntimeEnvironment.getApplication());
        DataManager.getInstance().loadAppData();
        DataManager.getInstance().getWebsites().clear();
        DataManager.getInstance().addWebsite(new com.cylonid.nativealpha.model.WebApp("https://example.org", 0));
    }

    @Test
    public void homeLaunch_searchDoesNotStealFocusByDefault_imeHidden() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            EditText search = activity.findViewById(R.id.searchWebApps);
            View rootContent = activity.findViewById(R.id.homeMainContent);
            DrawerLayout drawer = activity.findViewById(R.id.homeDrawerLayout);

            assertNotNull(search);
            assertNotNull(rootContent);
            assertNotNull(drawer);

            assertTrue("Drawer root should be focusable in touch mode", drawer.isFocusableInTouchMode());
            assertTrue("Main content root should be focusable in touch mode", rootContent.isFocusableInTouchMode());
            assertFalse("Search box must not steal or retain focus on home launch", search.isFocused());
            assertFalse("Search box must not have focus on home launch", search.hasFocus());

            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            ShadowInputMethodManager shadowImm = Shadows.shadowOf(imm);
            assertFalse("Keyboard must be hidden by default on home launch", shadowImm.isSoftInputVisible());
        }
    }

    @Test
    public void searchActionOrAppLaunch_clearsSearchFocusAndHidesIme() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            EditText search = activity.findViewById(R.id.searchWebApps);
            assertNotNull(search);

            // User actively focuses and enters text
            search.requestFocus();
            search.setText("https://example.org");
            assertTrue("Active click allows user to focus search box", search.hasFocus());

            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            ShadowInputMethodManager shadowImm = Shadows.shadowOf(imm);
            imm.showSoftInput(search, 0);

            // Submitting search to launch web page
            search.onEditorAction(EditorInfo.IME_ACTION_GO);

            assertFalse("Search focus must be cleared when departing to web page", search.hasFocus());
            assertFalse("Keyboard must be hidden when departing to web page", shadowImm.isSoftInputVisible());

            ShadowActivity shadowActivity = Shadows.shadowOf(activity);
            Intent launchedIntent = shadowActivity.getNextStartedActivity();
            assertNotNull("Departure intent should be started", launchedIntent);
        }
    }

    @Test
    public void returnFromWebApp_doesNotAutofocusSearchOrPopIme_preservesQueryText() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            EditText search = activity.findViewById(R.id.searchWebApps);
            assertNotNull(search);

            // 1. User previously searched on home screen
            search.requestFocus();
            search.setText("my-web-app");
            assertTrue(search.hasFocus());

            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            ShadowInputMethodManager shadowImm = Shadows.shadowOf(imm);
            imm.showSoftInput(search, 0);

            // 2. User leaves home page (pause)
            controller.pause();
            assertFalse("Leaving home screen clears search focus", search.hasFocus());
            assertFalse("Leaving home screen hides soft input", shadowImm.isSoftInputVisible());
            controller.stop();

            // 3. User returns to home from web app by clicking Home button (singleTask intent -> resume)
            Intent homeIntent = new Intent(activity, MainActivity.class);
            controller.newIntent(homeIntent);
            controller.restart();
            controller.start();
            controller.resume();

            // 4. Returning to home does not autofocus search, does not pop keyboard, retains query
            assertFalse("Returning to home must not autofocus search box", search.isFocused());
            assertFalse("Returning to home must not give focus to search", search.hasFocus());
            assertFalse("Returning to home must not automatically pop up keyboard", shadowImm.isSoftInputVisible());
            assertEquals("Existing search text must be preserved", "my-web-app", search.getText().toString());
        }
    }
}
