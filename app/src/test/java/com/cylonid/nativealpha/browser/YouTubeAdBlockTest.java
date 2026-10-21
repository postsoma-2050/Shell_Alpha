package com.cylonid.nativealpha.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.webkit.WebResourceResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.InputStream;

@RunWith(RobolectricTestRunner.class)
public class YouTubeAdBlockTest {

    @Test
    public void testSupportsYouTubeDomains() {
        assertTrue(YouTubeAdBlock.supports("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(YouTubeAdBlock.supports("https://www.youtube.com/"));
        assertTrue(YouTubeAdBlock.supports("https://youtube.com/feed/explore"));
        assertTrue(YouTubeAdBlock.supports("https://youtu.be/dQw4w9WgXcQ"));
        assertTrue(YouTubeAdBlock.supports("http://music.youtube.com/"));

        assertFalse(YouTubeAdBlock.supports("https://example.com"));
        assertFalse(YouTubeAdBlock.supports("https://notyoutube.com"));
        assertFalse(YouTubeAdBlock.supports(null));
        assertFalse(YouTubeAdBlock.supports("not a valid url"));
    }

    @Test
    public void testShouldInterceptAdRequests() {
        // DoubleClick & Google ad servers
        assertTrue(YouTubeAdBlock.shouldIntercept("https://googleads.g.doubleclick.net/pagead/id"));
        assertTrue(YouTubeAdBlock.shouldIntercept("https://pubads.g.doubleclick.net/gampad/ads"));
        assertTrue(YouTubeAdBlock.shouldIntercept("https://pagead2.googlesyndication.com/pagead/show_ads.js"));
        assertTrue(YouTubeAdBlock.shouldIntercept("https://adservice.google.com/adsid/google/ui"));

        // Regular content requests must NOT be blocked
        assertFalse(YouTubeAdBlock.shouldIntercept("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(YouTubeAdBlock.shouldIntercept("https://www.youtube.com/s/desktop/123/css/base.css"));
        assertFalse(YouTubeAdBlock.shouldIntercept("https://rr1---sn-xxx.googlevideo.com/videoplayback?expire=123"));
        assertFalse(YouTubeAdBlock.shouldIntercept("https://example.com/some/image.png"));
        assertFalse(YouTubeAdBlock.shouldIntercept(null));
    }

    @Test
    public void testCreateEmptyResponse() throws Exception {
        WebResourceResponse response = YouTubeAdBlock.createEmptyResponse();
        assertNotNull(response);
        assertNotNull(response.getMimeType());
        InputStream stream = response.getData();
        assertNotNull(stream);
        // Verify stream is empty
        org.junit.Assert.assertEquals(-1, stream.read());
    }

    @Test
    public void testScriptContainsCoreSelectors() {
        String script = YouTubeAdBlock.SCRIPT;
        assertNotNull(script);
        assertTrue(script.contains("ytm-promoted-sparkles-web-renderer"));
        assertTrue(script.contains("ytd-ad-slot-renderer"));
        assertTrue(script.contains(".ytp-ad-skip-button"));
        assertTrue(script.contains("playbackRate = 16.0"));
        assertTrue(script.contains("ad-showing"));
    }
}
