package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.cylonid.nativealpha.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReaderTranslationPanelTest {
    @Test public void readingThemesKeepArticleAndPanelTextReadable() {
        for (int theme = 0; theme < 4; theme++) {
            ReaderAppearance colors = ReaderAppearance.of(theme);
            assertTrue(ColorUtils.calculateContrast(colors.ink, colors.surface) >= 4.5);
            assertTrue(ColorUtils.calculateContrast(colors.muted, colors.surface) >= 4.5);
            assertTrue(ColorUtils.calculateContrast(colors.accent, colors.surface) >= 4.5);
            assertTrue(ColorUtils.calculateContrast(colors.ink, colors.paper) >= 4.5);
        }
    }

    @Test public void successHidesRecoveryActionsAndFailureOffersThem() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.AppTheme);
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host);
        ReaderTranslationPanel owner = new ReaderTranslationPanel(host, 0);
        try {
            View panel = prepare(owner, host, "A short original.");
            assertFalse(panel.findViewById(R.id.readerTranslationCopy).isEnabled());
            assertEquals(View.GONE, panel.findViewById(R.id.readerTranslationErrorActions).getVisibility());
            ReflectionHelpers.callInstanceMethod(owner, "showResult", from(String.class, null), from(int.class, R.string.reader_translation_timeout));
            assertEquals(View.VISIBLE, panel.findViewById(R.id.readerTranslationErrorActions).getVisibility());
            assertFalse(panel.findViewById(R.id.readerTranslationCopy).isEnabled());
            ReflectionHelpers.callInstanceMethod(owner, "showResult", from(String.class, "这是一段译文。"), from(int.class, 0));
            assertEquals(View.GONE, panel.findViewById(R.id.readerTranslationErrorActions).getVisibility());
            assertTrue(panel.findViewById(R.id.readerTranslationCopy).isEnabled());
            panel.findViewById(R.id.readerTranslationClose).performClick();
            assertFalse(owner.isShowing());
            assertEquals(0, host.getChildCount());
        } finally { owner.close(); activity.finish(); }
    }

    @Test public void longOriginalCanExpandWithoutExpandingTheReadingOverlay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.AppTheme);
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host);
        ReaderTranslationPanel owner = new ReaderTranslationPanel(host, 2);
        try {
            String original = new String(new char[12]).replace("\0", "Reading should stay comfortable while showing a long original selection. ");
            View panel = prepare(owner, host, original);
            TextView source = panel.findViewById(R.id.readerTranslationSource);
            assertEquals(original, source.getText().toString());
            panel.findViewById(R.id.readerTranslationExpandSource).performClick();
            assertEquals(Integer.MAX_VALUE, source.getMaxLines());
            panel.findViewById(R.id.readerTranslationExpandSource).performClick();
            assertEquals(3, source.getMaxLines());
            assertEquals(ReaderAppearance.of(2).ink, ((TextView) panel.findViewById(R.id.readerTranslationHeading)).getCurrentTextColor());
        } finally { owner.close(); activity.finish(); }
    }

    @Test public void narrowAndLandscapeLayoutsRetainScrollableBodyAndReachableActionsAtLargeFonts() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            for (float scale : new float[]{1, 2}) for (int[] size : new int[][]{{320, 640}, {640, 320}, {800, 1000}}) {
                Configuration config = new Configuration(activity.getResources().getConfiguration());
                config.fontScale = scale;
                Context context = new android.view.ContextThemeWrapper(activity.createConfigurationContext(config), R.style.AppTheme);
                float density = context.getResources().getDisplayMetrics().density;
                int width = Math.round(size[0] * density), height = Math.round(size[1] * density);
                FrameLayout host = new FrameLayout(context);
                activity.setContentView(host);
                measure(host, width, height);
                ReaderTranslationPanel owner = new ReaderTranslationPanel(host, scale == 1 ? 0 : 2);
                try {
                    String original = new String(new char[12]).replace("\0", "Long original prose should scroll within the reading panel. ");
                    View panel = prepare(owner, host, original);
                    ReflectionHelpers.callInstanceMethod(owner, "showResult", from(String.class, original), from(int.class, 0));
                    ReflectionHelpers.callInstanceMethod(owner, "updateBounds");
                    measure(host, width, height);
                    String scenario = size[0] + "x" + size[1] + " font=" + scale;
                    assertTrue(scenario, panel.getHeight() < height);
                    assertTrue(scenario + " scroll body", panel.findViewById(R.id.readerTranslationScroll).getHeight() >= Math.round(48 * density));
                    for (int id : new int[]{R.id.readerTranslationCopy, R.id.readerTranslationClose, R.id.readerTranslationLanguage}) {
                        View action = panel.findViewById(id);
                        Rect bounds = new Rect(0, 0, action.getWidth(), action.getHeight());
                        ((android.view.ViewGroup) panel).offsetDescendantRectToMyCoords(action, bounds);
                        assertTrue(scenario + " action outside panel", bounds.left >= 0 && bounds.top >= 0
                                && bounds.right <= panel.getWidth() && bounds.bottom <= panel.getHeight());
                        assertTrue(scenario + " touch height", action.getHeight() >= Math.round(48 * density));
                    }
                } finally { owner.close(); }
            }
        } finally { activity.finish(); }
    }

    @Test public void shortTextHidesExpandButtonWhileLongTextShowsIt() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.AppTheme);
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host);
        ReaderTranslationPanel shortOwner = new ReaderTranslationPanel(host, 0);
        try {
            View shortPanel = prepare(shortOwner, host, "Short text below 140 characters.");
            assertEquals(View.GONE, shortPanel.findViewById(R.id.readerTranslationExpandSource).getVisibility());
        } finally { shortOwner.close(); host.removeAllViews(); }

        ReaderTranslationPanel longOwner = new ReaderTranslationPanel(host, 1);
        try {
            StringBuilder longBuilder = new StringBuilder();
            for (int i = 0; i < 20; i++) longBuilder.append("This is a sentence in a long paragraph exceeding 140 characters. ");
            String longText = longBuilder.toString();
            View longPanel = prepare(longOwner, host, longText);
            View expand = longPanel.findViewById(R.id.readerTranslationExpandSource);
            assertEquals(View.VISIBLE, expand.getVisibility());
            TextView source = longPanel.findViewById(R.id.readerTranslationSource);
            assertEquals(3, source.getMaxLines());
            expand.performClick();
            assertEquals(Integer.MAX_VALUE, source.getMaxLines());
            expand.performClick();
            assertEquals(3, source.getMaxLines());
        } finally { longOwner.close(); activity.finish(); }
    }

    @Test public void extremeLengthTranslationDoesNotOverflowAndCopiesFullText() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.AppTheme);
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host);
        ReaderTranslationPanel owner = new ReaderTranslationPanel(host, 3);
        try {
            StringBuilder sb = new StringBuilder();
            while (sb.length() < 1950) sb.append("Comprehensive long paragraph translation verification. ");
            String longSource = sb.substring(0, 1950);
            String longTranslated = "【译文】" + longSource;

            View panel = prepare(owner, host, longSource);
            ReflectionHelpers.callInstanceMethod(owner, "showResult", from(String.class, longTranslated), from(int.class, 0));
            measure(host, 1080, 1920);

            assertTrue("Panel height within bounds", panel.getHeight() < 1920);
            View copy = panel.findViewById(R.id.readerTranslationCopy);
            assertTrue("Copy button enabled", copy.isEnabled());
            copy.performClick();

            android.content.ClipboardManager cm = activity.getSystemService(android.content.ClipboardManager.class);
            assertNotNull(cm.getPrimaryClip());
            assertEquals(longTranslated, cm.getPrimaryClip().getItemAt(0).getText().toString());
        } finally { owner.close(); activity.finish(); }
    }

    private static View prepare(ReaderTranslationPanel owner, FrameLayout host, String text) {
        // Exercise layout/state independently of the live Google service.
        ReflectionHelpers.callInstanceMethod(owner, "buildContent", from(String.class, text));
        View panel = ReflectionHelpers.getField(owner, "panel");
        host.addView(panel, new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM));
        return panel;
    }
    private static void measure(View host, int width, int height) {
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, width, height);
    }
}
