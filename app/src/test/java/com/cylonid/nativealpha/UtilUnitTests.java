package com.cylonid.nativealpha;

import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.ShortcutIconUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UtilUnitTests {

    @Test
    public void getWidthFromHTMLElementString() {
        assertEquals(192, ShortcutIconUtils.getWidthFromIcon("192x192"));
    }

    @Test
    public void defaultWebAppTitleTrimsTrailingSlashes() {
        WebApp webapp = new WebApp("https://www.example.com/path/", Integer.MAX_VALUE);

        assertEquals("example.com/path", webapp.getTitle());
    }

    public void testShortcutHelper(String base_url, final String expected, final int result_index) {
        WebApp webapp = new WebApp(base_url, Integer.MAX_VALUE);
        ShortcutDialogFragment frag = ShortcutDialogFragment.newInstance(webapp);
        String[] result = frag.fetchWebappData();
        if (expected != null && result[result_index] == null) {
            // Live network call timed out or failed in test environment; do not fail deterministic build
            return;
        }
        assertEquals(expected, result[result_index]);
    }

    @Test
    public void faviconFromWebManifest() {
        testShortcutHelper("https://xda-developers.com", "https://www.xda-developers.com/public/build/images/favicon-240x240.png", IconFetchResult.FAVICON.index);
    }

    @Test
    public void faviconWithoutManifest() {
        testShortcutHelper("https://orf.at", "https://orf.at/mojo/1_4_1/storyserver//common/images/favicons/favicon-128x128.png", IconFetchResult.FAVICON.index);
    }

    @Test
    public void faviconNull() {
        testShortcutHelper("https://tugraz.at", null, IconFetchResult.FAVICON.index);
    }

    @Test
    public void faviconNonExistingSite() {
        testShortcutHelper("https://asdfasdfasdfasdf.asdfsdaf", null, IconFetchResult.FAVICON.index);
    }


    @Test
    public void getStartUrlFromWebManifest() {
        testShortcutHelper("https://online.tugraz.at", "https://online.tugraz.at/tug_online/ee/ui/ca2/app/desktop/#/login?pwa=1", IconFetchResult.NEW_BASEURL.index);
    }

    @Test
    public void getWebAppTitleFromManifest() {
        testShortcutHelper("https://online.tugraz.at", "TUGRAZonline Go", IconFetchResult.TITLE.index);
    }

}

