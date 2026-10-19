package com.cylonid.nativealpha.media;

import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bridges top-level WebView media state to the process-local playback service.
 *
 * <p>Only aggregate "playing" and "paused" messages are accepted. Media inside iframes is not
 * observed because a page-wide bridge would expose control across unrelated frame origins.</p>
 */
public final class BackgroundPlaybackController implements BackgroundPlaybackService.Client {
    private static final String TAG = "BackgroundMedia";
    private static final String BRIDGE_NAME = "NativeAlphaBackgroundMedia";
    private static final String PLAYING = "playing";
    private static final String PAUSED = "paused";

    private static final Map<BackgroundPlaybackController, Boolean> PROCESS_CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final AppCompatActivity activity;
    private final WebView webView;
    private volatile WebApp webApp;

    private boolean playbackEnabled;
    private boolean foreground;
    private boolean mediaActive;
    private boolean destroyed;
    private boolean listenerInstalled;
    private boolean pageReady;
    private int pageGeneration;
    private boolean captureScriptScheduled;
    @Nullable
    private String currentOrigin;

    public BackgroundPlaybackController(
            AppCompatActivity activity, WebView webView, WebApp webApp) {
        this.activity = activity;
        this.webView = webView;
        this.webApp = webApp;
        playbackEnabled = webApp.isAllowMediaPlaybackInBackground();
        foreground = activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                && !activity.isFinishing();
        PROCESS_CONTROLLERS.put(this, Boolean.TRUE);
        installListener();
    }

    public void onPageStarted(String url) {
        if (destroyed) return;
        resetPageState(originOf(url));
        BackgroundPlaybackService.stop(activity, this);
    }

    public void onPageFinished(String url) {
        if (destroyed) return;
        currentOrigin = originOf(url);
        pageGeneration++;
        pageReady = currentOrigin != null;
        evaluateCaptureScript();
    }

    public void onResume(WebApp latestWebApp) {
        if (destroyed) return;
        String pageOrigin = originOf(webView.getUrl());
        boolean samePage = equal(currentOrigin, pageOrigin);
        boolean validExistingPlayback = mediaActive
                && samePage
                && BackgroundPlaybackService.isActive(this);
        webApp = latestWebApp;
        playbackEnabled = latestWebApp.isAllowMediaPlaybackInBackground();
        foreground = true;
        mediaActive = validExistingPlayback;
        pageGeneration++;
        pageReady = pageOrigin != null;
        currentOrigin = pageOrigin;

        // Revalidate opt-out without pausing media that is still valid in the foreground.
        if (!playbackEnabled) BackgroundPlaybackService.stop(activity, this);
        evaluateCaptureScript();
    }

    /**
     * @return true when the activity should leave this WebView running while it is paused.
     */
    public boolean onPause() {
        foreground = false;
        captureScriptScheduled = false;
        boolean keepPlaying = playbackEnabled
                && mediaActive
                && BackgroundPlaybackService.isActive(this);
        if (keepPlaying) {
            return true;
        }

        stopPlaybackAndInvalidate(false);
        return false;
    }

    /**
     * pauseTimers() is process-global, so any foreground WebView or valid background player keeps
     * timers running for the whole process.
     */
    public boolean shouldPauseWebViewTimers() {
        synchronized (PROCESS_CONTROLLERS) {
            for (BackgroundPlaybackController controller : PROCESS_CONTROLLERS.keySet()) {
                if (controller.foreground
                        || (controller.mediaActive
                        && BackgroundPlaybackService.isActive(controller))) {
                    return false;
                }
            }
        }
        return true;
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        foreground = false;
        stopPlaybackAndInvalidate(true);
        removeListener();
        PROCESS_CONTROLLERS.remove(this);
    }

    @Override
    public int getWebAppId() {
        return webApp.getID();
    }

    @Override
    public String getPlaybackTitle() {
        String title = webApp.getTitle();
        if (title == null || title.trim().isEmpty()) title = webApp.getBaseUrl();
        return title == null || title.trim().isEmpty() ? activity.getString(R.string.background_media_channel) : title;
    }

    @Override
    public Intent getPlaybackIntent() {
        Intent intent = new Intent(activity.getIntent());
        intent.putExtra(Const.INTENT_WEBAPPID, getWebAppId());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    /**
     * Called by the service notification, task removal, or foreground-service failure.
     */
    @Override
    public void stopPlayback() {
        runOnMainThread(() -> {
            if (!destroyed) stopPlaybackAndInvalidate(true);
        });
    }

    /**
     * Immediately blocks queued state events. Unlike the service callback, this may leave the
     * current page's listener available for a later onResume().
     */
    public void releasePlayback() {
        resetPageState(currentOrigin);
        BackgroundPlaybackService.stop(activity, this);
        if (!destroyed) pauseMedia();
    }

    private void stopPlaybackAndInvalidate(boolean pauseMedia) {
        resetPageState(currentOrigin);
        if (pauseMedia) {
            pauseMedia();
            BackgroundPlaybackService.stop(activity, this);
            pauseBackgroundWebViewIfUnused();
        } else {
            BackgroundPlaybackService.stop(activity, this);
        }
    }

    private void handlePlaybackState(boolean playing) {
        if (destroyed || !pageReady) return;
        mediaActive = playing;
        if (!playing) {
            BackgroundPlaybackService.stop(activity, this);
            pauseBackgroundWebViewIfUnused();
            return;
        }
        if (!playbackEnabled || !isForeground()) return;
        if (BackgroundPlaybackService.isActive(this)) {
            return;
        }
        // This is reached only from a real media state event or an initial current-state check.
        BackgroundPlaybackService.start(activity, this);
    }

    private boolean isForeground() {
        return foreground
                && !activity.isFinishing()
                && !activity.isDestroyed()
                && activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
    }

    private void installListener() {
        if (listenerInstalled
                || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return;
        }

        try {
            WebViewCompat.addWebMessageListener(
                    webView,
                    BRIDGE_NAME,
                    Collections.singleton("*"),
                    (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                        if (destroyed
                                || !pageReady
                                || !isMainFrame
                                || message.getType() != WebMessageCompat.TYPE_STRING) {
                            return;
                        }
                        String source = originOf(sourceOrigin.toString());
                        String page = originOf(view.getUrl());
                        if (!equal(currentOrigin, source) || !equal(currentOrigin, page)) return;

                        String prefix = pageGeneration + ":";
                        String data = message.getData();
                        if (data == null || !data.startsWith(prefix)) return;
                        String state = data.substring(prefix.length());
                        if (PLAYING.equals(state)) handlePlaybackState(true);
                        else if (PAUSED.equals(state)) handlePlaybackState(false);
                    });
            listenerInstalled = true;
        } catch (IllegalArgumentException
                 | IllegalStateException
                 | UnsupportedOperationException exception) {
            Log.w(TAG, "Unable to install the media state listener", exception);
        }
    }

    private void resetPageState(@Nullable String origin) {
        mediaActive = false;
        pageReady = false;
        pageGeneration++;
        currentOrigin = origin;
        captureScriptScheduled = false;
    }

    private void removeListener() {
        if (!listenerInstalled) return;
        listenerInstalled = false;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        try {
            WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME);
        } catch (IllegalArgumentException
                 | IllegalStateException
                 | UnsupportedOperationException exception) {
            Log.w(TAG, "Unable to remove the media state listener", exception);
        }
    }

    private void evaluateCaptureScript() {
        if (destroyed
                || captureScriptScheduled
                || !webView.getSettings().getJavaScriptEnabled()) {
            return;
        }
        captureScriptScheduled = true;
        final int generation = pageGeneration;
        webView.post(() -> {
            captureScriptScheduled = false;
            if (destroyed
                    || generation != pageGeneration
                    || !pageReady
                    || !webView.getSettings().getJavaScriptEnabled()) {
                return;
            }
            webView.evaluateJavascript(captureMediaScript(generation), null);
        });
    }

    private static String captureMediaScript(int generation) {
        String prefix = generation + ":";
        return "(function(){"
                + "var prefix='" + prefix + "';"
                + "var post=function(){"
                + "var nodes=document.querySelectorAll('audio,video'),playing=false;"
                + "for(var i=0;i<nodes.length;i++){var media=nodes[i];"
                + "if(!media.paused&&!media.ended){playing=true;break;}}"
                + "var bridge=window." + BRIDGE_NAME + ";"
                + "if(bridge&&typeof bridge.postMessage==='function'){"
                + "bridge.postMessage(window.__nativeAlphaBackgroundMediaCaptureToken"
                + "+(playing?'playing':'paused'));}};"
                + "window.__nativeAlphaBackgroundMediaCaptureToken=prefix;"
                + "if(!window.__nativeAlphaBackgroundMediaCaptureInstalled){"
                + "window.__nativeAlphaBackgroundMediaCaptureInstalled=true;"
                + "['playing','pause','ended','emptied'].forEach(function(type){"
                + "document.addEventListener(type,function(event){"
                + "if(event.target instanceof HTMLMediaElement){setTimeout(post,0);}},true);});}"
                + "post();})();";
    }

    private void pauseMedia() {
        if (webView.getSettings().getJavaScriptEnabled()) {
            webView.evaluateJavascript(
                    "document.querySelectorAll('audio,video')"
                            + ".forEach(function(media){media.pause();});",
                    null);
        }
    }

    private void pauseBackgroundWebViewIfUnused() {
        if (foreground || destroyed) return;
        webView.onPause();
        if (shouldPauseWebViewTimers()) webView.pauseTimers();
    }

    private void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else activity.runOnUiThread(action);
    }

    @Nullable
    private static String originOf(@Nullable String url) {
        if (url == null) return null;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) return null;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) return null;

        host = host.toLowerCase(Locale.ROOT);
        if (host.contains(":")) host = "[" + host + "]";
        int port = uri.getPort();
        if (("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }
        return scheme + "://" + host + (port >= 0 ? ":" + port : "");
    }

    private static boolean equal(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : first.equals(second);
    }
}
