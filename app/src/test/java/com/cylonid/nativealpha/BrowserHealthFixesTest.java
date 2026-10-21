package com.cylonid.nativealpha;

import android.content.Context;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.cylonid.nativealpha.browser.YouTubeAdBlock;
import com.cylonid.nativealpha.model.WebApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserHealthFixesTest {
    private WebViewActivity activity;
    private WebApp app;
    private ScriptCapturingWebView webView;
    private WebViewClient client;

    private static class ScriptCapturingWebView extends WebView {
        final List<String> evaluatedScripts = new ArrayList<>();

        ScriptCapturingWebView(Context context) {
            super(context);
        }

        @Override
        public void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
            evaluatedScripts.add(script);
            if (resultCallback != null) {
                resultCallback.onReceiveValue(null);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.buildActivity(WebViewActivity.class).get();
        activity.setTheme(R.style.AppTheme);
        activity.setContentView(R.layout.full_webview);
        app = new WebApp("https://example.org", 0);
        webView = new ScriptCapturingWebView(activity);
        ReflectionHelpers.setField(activity, "webapp", app);
        ReflectionHelpers.setField(activity, "wv", webView);
        ReflectionHelpers.setField(activity, "rootWebView", webView);

        Constructor<?> ctor = Class.forName(WebViewActivity.class.getName() + "$CustomBrowser")
                .getDeclaredConstructor(WebViewActivity.class);
        ctor.setAccessible(true);
        client = (WebViewClient) ctor.newInstance(activity);
    }

    @After
    public void tearDown() {
        webView.destroy();
        ((WebView) activity.findViewById(R.id.webview)).destroy();
        ((WebView) activity.findViewById(R.id.adblockwebview)).destroy();
    }

    private WebResourceRequest createRequest(String url, boolean isMainFrame) {
        return new WebResourceRequest() {
            @Override public Uri getUrl() { return Uri.parse(url); }
            @Override public boolean isForMainFrame() { return isMainFrame; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public Map<String, String> getRequestHeaders() { return Collections.emptyMap(); }
        };
    }

    @Test
    public void thirdPartySubresourceBlockedEvenWhenAdblockEnabled() {
        app.setBlockThirdPartyRequests(true);
        app.setUseAdblock(true);

        WebResourceRequest thirdPartyRequest = createRequest("https://tracker.analytics.com/log.js", false);
        WebResourceResponse response = client.shouldInterceptRequest(webView, thirdPartyRequest);

        assertNotNull(response);
        assertEquals("text/plain", response.getMimeType());
        assertEquals("utf-8", response.getEncoding());
        assertNull(response.getData());
    }

    @Test
    public void pageSettingsOnlyInjectsVisibilityChangeWhenMediaPlaybackInBackgroundAllowed() {
        app.setAllowJs(true);

        // When background media playback is disabled:
        app.setAllowMediaPlaybackInBackground(false);
        ReflectionHelpers.callInstanceMethod(client, "injectPageSettings",
                ReflectionHelpers.ClassParameter.from(WebView.class, webView));
        assertEquals(1, webView.evaluatedScripts.size());
        String scriptWithoutBg = webView.evaluatedScripts.get(0);
        assertFalse(scriptWithoutBg.contains("visibilitychange"));

        // Reset and test when background media playback is enabled:
        webView.evaluatedScripts.clear();
        app.setAllowMediaPlaybackInBackground(true);
        ReflectionHelpers.callInstanceMethod(client, "injectPageSettings",
                ReflectionHelpers.ClassParameter.from(WebView.class, webView));
        assertEquals(1, webView.evaluatedScripts.size());
        String scriptWithBg = webView.evaluatedScripts.get(0);
        assertTrue(scriptWithBg.contains("visibilitychange"));
        assertTrue(scriptWithBg.contains("stopImmediatePropagation"));
    }

    @Test
    public void userscriptAllowsGrantNoneAndRejectsPrivilegedGmApis() {
        app.setUserscriptEnabled(true);

        // Script 1: @grant none -> allowed
        app.setUserscriptCode("// ==UserScript==\n// @grant none\n// ==/UserScript==\nwindow.myTest = 123;");
        ReflectionHelpers.callInstanceMethod(client, "injectUserscriptIfAllowed",
                ReflectionHelpers.ClassParameter.from(WebView.class, webView));
        assertEquals(1, webView.evaluatedScripts.size());
        assertTrue(webView.evaluatedScripts.get(0).contains("window.myTest = 123"));

        // Script 2: @grant GM_setValue -> rejected
        webView.evaluatedScripts.clear();
        app.setUserscriptCode("// ==UserScript==\n// @grant GM_setValue\n// ==/UserScript==\nwindow.bad = 456;");
        ReflectionHelpers.callInstanceMethod(client, "injectUserscriptIfAllowed",
                ReflectionHelpers.ClassParameter.from(WebView.class, webView));
        assertEquals(0, webView.evaluatedScripts.size());

        // Script 3: @require -> rejected
        webView.evaluatedScripts.clear();
        app.setUserscriptCode("// ==UserScript==\n// @require https://cdn.example.com/lib.js\n// ==/UserScript==\nwindow.req = 789;");
        ReflectionHelpers.callInstanceMethod(client, "injectUserscriptIfAllowed",
                ReflectionHelpers.ClassParameter.from(WebView.class, webView));
        assertEquals(0, webView.evaluatedScripts.size());
    }

    @Test
    public void youTubeAdBlockScriptNarrowedObserver() {
        String script = YouTubeAdBlock.SCRIPT;
        assertFalse("Should not observe whole documentElement", script.contains("observer.observe(document.documentElement"));
        assertTrue("Should target video player element", script.contains("#movie_player, .html5-video-player"));
        assertTrue("Should support pageshow lifecycle restoration", script.contains("pageshow"));
        assertTrue("Should clean up on pagehide", script.contains("pagehide"));
    }
}
