package com.cylonid.nativealpha;

import android.content.Context;
import android.graphics.Bitmap;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cylonid.nativealpha.fragments.webapplist.WebAppListAdapter;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.EntryPointUtils;
import com.cylonid.nativealpha.util.ShortcutIconUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w360dp-h640dp")
public class HomeViewModeTest {
    private static final String PREFERENCES = "home_refinement";
    private static final String GRID_SPAN_COUNT = "grid_span_count";

    @Before
    public void setUp() {
        DataManager.getInstance().setEulaData(true);
        EntryPointUtils.markOnboardingCompleted(RuntimeEnvironment.getApplication());
        RuntimeEnvironment.getApplication()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        DataManager.getInstance().loadAppData();
        for (WebApp app : DataManager.getInstance().getActiveWebsites()) cacheIcon(app.getID());
        cacheIcon(9001);
        cacheIcon(9002);
    }

    @Test
    public void defaultAndTogglePersistOnlyFourOrOneColumns() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ImageButton toggle = activity.findViewById(R.id.btnGridColumnsToggle);
            SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);

            assertNotNull(toggle);
            assertEquals(4, ((GridLayoutManager) recycler(activity).getLayoutManager()).getSpanCount());
            assertEquals("Switch to list view", toggle.getContentDescription().toString());
            assertTrue(!preferences.contains(GRID_SPAN_COUNT));

            toggle.performClick();
            assertTrue(recycler(activity).getLayoutManager() instanceof LinearLayoutManager);
            assertTrue(!(recycler(activity).getLayoutManager() instanceof GridLayoutManager));
            assertEquals(1, preferences.getInt(GRID_SPAN_COUNT, -1));
            assertEquals("Switch to four-column icon grid", toggle.getContentDescription().toString());

            toggle.performClick();
            assertEquals(4, ((GridLayoutManager) recycler(activity).getLayoutManager()).getSpanCount());
            assertEquals(4, preferences.getInt(GRID_SPAN_COUNT, -1));
        }
    }

    @Test
    public void legacySpanPreferenceMigratesToListOrGrid() {
        SharedPreferences preferences = RuntimeEnvironment.getApplication()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        preferences.edit().putInt(GRID_SPAN_COUNT, 1).commit();

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            assertTrue(recycler(controller.get()).getLayoutManager() instanceof LinearLayoutManager);
            assertTrue(!(recycler(controller.get()).getLayoutManager() instanceof GridLayoutManager));
            assertEquals(1, preferences.getInt(GRID_SPAN_COUNT, -1));
        }

        preferences.edit().putInt(GRID_SPAN_COUNT, 3).commit();
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            GridLayoutManager manager =
                    (GridLayoutManager) recycler(controller.get()).getLayoutManager();
            assertEquals(4, manager.getSpanCount());
            assertEquals(4, preferences.getInt(GRID_SPAN_COUNT, -1));
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    public void listRowsKeepActionsAndTextSeparateAtLargeFont() {
        assertListRowGeometry(2f);
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    public void listRowsKeepActionsAndTextSeparateOnNormalPhone() {
        assertListRowGeometry(1f);
    }

    @Test
    public void searchStillFiltersAfterSwitchingModes() {
        DataManager.getInstance().addWebsite(new WebApp("https://search.example", 9002));
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.btnGridColumnsToggle).performClick();
            EditText search = activity.findViewById(R.id.searchWebApps);
            search.setText("definitely-no-such-lite-app");
            activity.findViewById(android.R.id.content).measure(
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
            assertEquals(View.VISIBLE, activity.findViewById(R.id.emptyState).getVisibility());
            TextView emptyTitle = activity.findViewById(R.id.emptyStateTitle);
            assertEquals(
                    activity.getString(R.string.home_no_search_results_title),
                    emptyTitle.getText().toString()
            );
        }
    }

    private void cacheIcon(int id) {
        ShortcutIconUtils.saveFavicon(RuntimeEnvironment.getApplication(), id,
                Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888));
    }

    private void assertListRowGeometry(float fontScale) {
        Configuration configuration = new Configuration(
                RuntimeEnvironment.getApplication().getResources().getConfiguration());
        configuration.fontScale = fontScale;
        Context themedContext = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication().createConfigurationContext(configuration),
                R.style.AppTheme);

        WebApp app = new WebApp("https://www.example.com/a/very/long/path", 9001);
        app.setTitle("A long Lite App title that must stay separate from actions");
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            WebAppListAdapter adapter = new WebAppListAdapter(
                    java.util.Collections.singletonList(app),
                    controller.get(),
                    webapp -> kotlin.Unit.INSTANCE
            );
            adapter.setListMode(true);

            View row = android.view.LayoutInflater.from(themedContext)
                    .inflate(R.layout.web_app_list_item, null, false);
            WebAppListAdapter.ViewHolder holder = adapter.getViewHolderForTest(row);
            adapter.onBindViewHolderForTest(app, holder, 0);
            int width = themedContext.getResources().getConfiguration().screenWidthDp
                    * themedContext.getResources().getDisplayMetrics().densityDpi / 160;
            row.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());

            LinearLayout root = (LinearLayout) row;
            LinearLayout text = row.findViewById(R.id.homeItemTextContainer);
            View icon = row.findViewById(R.id.iconTile);
            View settings = row.findViewById(R.id.btnWebAppSettings);
            View overflow = row.findViewById(R.id.btnWebAppMenu);
            TextView title = row.findViewById(R.id.btnWebAppTitle);
            TextView url = row.findViewById(R.id.txtWebAppUrl);

            assertEquals(LinearLayout.HORIZONTAL, root.getOrientation());
            assertTrue(row.getMeasuredHeight() >= dp(themedContext, 72));
            assertTrue(icon.getMeasuredWidth() >= dp(themedContext, 48));
            assertTrue(settings.getMeasuredWidth() >= dp(themedContext, 48));
            assertTrue(overflow.getMeasuredWidth() >= dp(themedContext, 48));
            assertTrue(settings.getVisibility() == View.VISIBLE);
            assertTrue(overflow.getVisibility() == View.VISIBLE);
            assertEquals(1, title.getMaxLines());
            assertEquals(1, url.getMaxLines());
            assertEquals("example.com/a/very/long/path", url.getText().toString());
            assertTrue(text.getRight() <= settings.getLeft());
            assertTrue(settings.getRight() <= overflow.getLeft());
        }
    }

    private RecyclerView recycler(MainActivity activity) {
        return activity.findViewById(R.id.web_app_list);
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
