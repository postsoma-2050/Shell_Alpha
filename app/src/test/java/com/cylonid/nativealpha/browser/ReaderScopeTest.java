package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Intent;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.view.menu.MenuBuilder;
import com.cylonid.nativealpha.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReaderScopeTest {
    @Test public void translationMenuFollowsReaderToggleAndPreservesCopy() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ReaderWebView reader = new ReaderWebView(activity, text -> fail("No selection requested"));
        try {
            boolean[] copied = {false};
            ActionMode.Callback delegate = new ActionMode.Callback() {
                public boolean onCreateActionMode(ActionMode mode, Menu menu) { return true; }
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
                public void onDestroyActionMode(ActionMode mode) { }
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    copied[0] = item.getItemId() == android.R.id.copy;
                    return copied[0];
                }
            };
            TestActionMode mode = new TestActionMode();
            Menu menu = new MenuBuilder(activity);
            MenuItem copy = menu.add(Menu.NONE, android.R.id.copy, 0, "Copy");
            ActionMode.Callback callback = reader.selectionCallback(delegate);
            assertTrue(callback.onCreateActionMode(mode, menu));
            assertNotNull(menu.findItem(R.id.readerTranslateSelection));
            assertTrue(callback.onActionItemClicked(mode, copy));
            assertTrue(copied[0]);
            reader.setTranslationEnabled(false);
            assertTrue(mode.invalidated);
            callback.onPrepareActionMode(mode, menu);
            assertNull(menu.findItem(R.id.readerTranslateSelection));
            reader.setTranslationEnabled(true);
            callback.onPrepareActionMode(mode, menu);
            assertNotNull(menu.findItem(R.id.readerTranslateSelection));
            reader.deactivate();
            assertTrue(mode.finished);
            callback.onPrepareActionMode(mode, menu);
            assertNull(menu.findItem(R.id.readerTranslateSelection));
        } finally { reader.destroy(); activity.finish(); }
    }

    private static class TestActionMode extends ActionMode {
        boolean invalidated;
        boolean finished;
        public void invalidate() { invalidated = true; }
        public void finish() { finished = true; }
        public void setTitle(CharSequence value) { }
        public void setTitle(int value) { }
        public void setSubtitle(CharSequence value) { }
        public void setSubtitle(int value) { }
        public void setCustomView(android.view.View view) { }
        public CharSequence getTitle() { return ""; }
        public CharSequence getSubtitle() { return ""; }
        public android.view.View getCustomView() { return null; }
        public Menu getMenu() { return null; }
        public android.view.MenuInflater getMenuInflater() { return null; }
    }

    @Test public void shellAlphaIsNotASystemTextTranslationHandler() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            Intent intent = new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                    .setPackage(activity.getPackageName());
            assertTrue(activity.getPackageManager().queryIntentActivities(intent, 0).isEmpty());
        } finally { activity.finish(); }
    }

    @Test public void readerCannotLoadNetworkFilesOrContentProviders() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ReaderWebView reader = new ReaderWebView(activity, text -> { });
        try {
            assertTrue(reader.getSettings().getBlockNetworkLoads());
            assertFalse(reader.getSettings().getAllowFileAccess());
            assertFalse(reader.getSettings().getAllowContentAccess());
        } finally { reader.destroy(); activity.finish(); }
    }
}
