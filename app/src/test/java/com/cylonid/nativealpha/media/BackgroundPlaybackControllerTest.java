package com.cylonid.nativealpha.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BackgroundPlaybackControllerTest {

    @Before
    public void setUp() {
        clearState();
    }

    @After
    public void tearDown() {
        clearState();
    }

    @Test
    public void enabledPlayingOnPauseKeepsActiveServiceAndNoOwnGlobalPause() {
        WebApp webApp = new WebApp("https://example.com", 101);
        webApp.setAllowMediaPlaybackInBackground(true);

        try (ActivityController<TestActivity> activityController =
                     Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = activityController.get();
            RecordingWebView webView = new RecordingWebView(activity);
            BackgroundPlaybackController controller =
                    new BackgroundPlaybackController(activity, webView, webApp);

            ReflectionHelpers.setField(controller, "pageReady", true);
            ReflectionHelpers.setField(controller, "currentOrigin", "https://example.com");
            ReflectionHelpers.callInstanceMethod(controller, "handlePlaybackState",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));

            assertTrue(BackgroundPlaybackService.isActive(controller));
            boolean keepPlaying = controller.onPause();
            assertTrue(keepPlaying);
            assertFalse(webView.onPauseCalled);
            assertFalse(webView.pauseTimersCalled);
            assertTrue(BackgroundPlaybackService.isActive(controller));
            assertFalse(controller.shouldPauseWebViewTimers());
            assertEquals(0, webView.pauseCalls);
            assertEquals(0, webView.timerPauseCalls);

            controller.destroy();
        }
    }

    @Test
    public void disabledAndOrdinaryForegroundResumeDoNotForcePauseMedia() {
        WebApp disabledApp = new WebApp("https://example.com", 102);
        disabledApp.setAllowMediaPlaybackInBackground(false);

        try (ActivityController<TestActivity> activityController =
                     Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = activityController.get();
            RecordingWebView webView = new RecordingWebView(activity);
            BackgroundPlaybackController controller =
                    new BackgroundPlaybackController(activity, webView, disabledApp);

            ReflectionHelpers.setField(controller, "pageReady", true);
            ReflectionHelpers.setField(controller, "currentOrigin", "https://example.com");
            ReflectionHelpers.callInstanceMethod(controller, "handlePlaybackState",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));

            assertFalse(BackgroundPlaybackService.isActive(controller));
            assertFalse(controller.onPause());
            assertNull(webView.lastEvaluatedJavascript);

            controller.onResume(disabledApp);
            assertFalse(BackgroundPlaybackService.isActive(controller));
            assertFalse(webView.scripts.stream().anyMatch(script -> script.contains("media.pause()")));

            controller.destroy();
        }
    }

    @Test
    public void stopAndEndedOnBackgroundPausesWebViewAndReleasesService() {
        WebApp webApp = new WebApp("https://example.com", 103);
        webApp.setAllowMediaPlaybackInBackground(true);

        try (ActivityController<TestActivity> activityController =
                     Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = activityController.get();
            RecordingWebView webView = new RecordingWebView(activity);
            BackgroundPlaybackController controller =
                    new BackgroundPlaybackController(activity, webView, webApp);

            ReflectionHelpers.setField(controller, "pageReady", true);
            ReflectionHelpers.setField(controller, "currentOrigin", "https://example.com");
            ReflectionHelpers.callInstanceMethod(controller, "handlePlaybackState",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));

            assertTrue(controller.onPause());
            assertFalse(webView.onPauseCalled);
            assertTrue(BackgroundPlaybackService.isActive(controller));

            controller.stopPlayback();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertFalse(BackgroundPlaybackService.isActive(controller));
            assertTrue(webView.onPauseCalled);
            assertTrue(webView.pauseTimersCalled);
            assertNotNull(webView.lastEvaluatedJavascript);
            assertTrue(webView.lastEvaluatedJavascript.contains("pause()"));
            assertFalse((Boolean) ReflectionHelpers.getField(controller, "mediaActive"));
            assertTrue(controller.shouldPauseWebViewTimers());
            assertEquals(1, webView.pauseCalls);
            assertEquals(1, webView.timerPauseCalls);

            controller.onResume(webApp);
            ReflectionHelpers.setField(controller, "pageReady", true);
            ReflectionHelpers.callInstanceMethod(controller, "handlePlaybackState",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            assertTrue(controller.onPause());
            ReflectionHelpers.callInstanceMethod(controller, "handlePlaybackState",
                    ReflectionHelpers.ClassParameter.from(boolean.class, false));
            assertFalse(BackgroundPlaybackService.isActive(controller));
            assertEquals(2, webView.pauseCalls);
            assertEquals(2, webView.timerPauseCalls);

            controller.destroy();
        }
    }

    @Test
    public void otherForegroundWebViewNotGloballyPausedByLastBackgroundStop() {
        WebApp bgApp = new WebApp("https://bg.example.com", 104);
        bgApp.setAllowMediaPlaybackInBackground(true);

        WebApp fgApp = new WebApp("https://fg.example.com", 105);

        try (ActivityController<TestActivity> bgControllerHolder =
                     Robolectric.buildActivity(TestActivity.class).setup();
             ActivityController<TestActivity> fgControllerHolder =
                     Robolectric.buildActivity(TestActivity.class).setup()) {

            TestActivity bgActivity = bgControllerHolder.get();
            RecordingWebView bgWebView = new RecordingWebView(bgActivity);
            BackgroundPlaybackController bgController =
                    new BackgroundPlaybackController(bgActivity, bgWebView, bgApp);

            ReflectionHelpers.setField(bgController, "pageReady", true);
            ReflectionHelpers.setField(bgController, "currentOrigin", "https://bg.example.com");
            ReflectionHelpers.callInstanceMethod(bgController, "handlePlaybackState",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));

            assertTrue(bgController.onPause());
            assertTrue(BackgroundPlaybackService.isActive(bgController));

            TestActivity fgActivity = fgControllerHolder.get();
            RecordingWebView fgWebView = new RecordingWebView(fgActivity);
            BackgroundPlaybackController fgController =
                    new BackgroundPlaybackController(fgActivity, fgWebView, fgApp);

            assertTrue((Boolean) ReflectionHelpers.getField(fgController, "foreground"));

            bgController.stopPlayback();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertFalse(BackgroundPlaybackService.isActive(bgController));
            assertTrue(bgWebView.onPauseCalled);
            assertFalse(bgWebView.pauseTimersCalled);
            assertFalse(fgWebView.pauseTimersCalled);
            assertFalse(bgController.shouldPauseWebViewTimers());
            assertFalse(fgController.shouldPauseWebViewTimers());
            assertEquals(1, bgWebView.pauseCalls);
            assertEquals(0, bgWebView.timerPauseCalls);
            assertEquals(0, fgWebView.pauseCalls);

            bgController.destroy();
            fgController.destroy();
        }
    }

    private void clearState() {
        Object clients = ReflectionHelpers.getStaticField(
                BackgroundPlaybackService.class, "clients");
        if (clients instanceof List) {
            ((List<?>) clients).clear();
        }
        ReflectionHelpers.setStaticField(
                BackgroundPlaybackService.class, "runningService", null);

        Object controllers = ReflectionHelpers.getStaticField(
                BackgroundPlaybackController.class, "PROCESS_CONTROLLERS");
        if (controllers instanceof Map) {
            ((Map<?, ?>) controllers).clear();
        }
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).clearStartedServices();
    }

    public static class RecordingWebView extends WebView {
        int pauseCalls;
        int timerPauseCalls;
        boolean onPauseCalled;
        boolean pauseTimersCalled;
        String lastEvaluatedJavascript;
        final List<String> scripts = new ArrayList<>();

        RecordingWebView(Context context) {
            super(context);
            getSettings().setJavaScriptEnabled(true);
        }

        @Override
        public void onPause() {
            pauseCalls++;
            onPauseCalled = true;
            super.onPause();
        }

        @Override
        public void pauseTimers() {
            timerPauseCalls++;
            pauseTimersCalled = true;
            super.pauseTimers();
        }

        @Override
        public void evaluateJavascript(String script, ValueCallback<String> callback) {
            scripts.add(script);
            lastEvaluatedJavascript = script;
            super.evaluateJavascript(script, callback);
        }
    }

    public static class TestActivity extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle state) {
            setTheme(R.style.AppTheme);
            super.onCreate(state);
        }
    }
}
