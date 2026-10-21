package com.cylonid.nativealpha;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;

import com.cylonid.nativealpha.monitors.WebMonitorWorker;
import com.cylonid.nativealpha.model.ConfigurationStore;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MonitorEntryReliabilityTest {
    @Before public void resetRecording() {
        RecordingConfigurationStore.calls = 0;
        RecordingConfigurationStore.immediate = false;
    }
    @Test
    public void immediateCheckIsOneTimeAndHasNoPeriodicDelay() {
        OneTimeWorkRequest request = WebMonitorWorker.immediateRequest();
        assertFalse(request.getWorkSpec().isPeriodic());
        assertEquals(0L, request.getWorkSpec().initialDelay);
        assertEquals(0L, request.getWorkSpec().intervalDuration);
        assertEquals(NetworkType.CONNECTED, request.getWorkSpec().constraints.getRequiredNetworkType());
        assertTrue(request.getTags().contains(WebMonitorWorker.IMMEDIATE_WORK_NAME));
        assertFalse(request.getTags().contains(WebMonitorWorker.UNIQUE_WORK_NAME));
    }

    @Test
    public void periodicCheckKeepsIndependentIdentityAndFifteenMinuteInterval() {
        PeriodicWorkRequest periodic = WebMonitorWorker.periodicRequest();
        OneTimeWorkRequest immediate = WebMonitorWorker.immediateRequest();
        assertTrue(periodic.getWorkSpec().isPeriodic());
        assertEquals(TimeUnit.MINUTES.toMillis(15), periodic.getWorkSpec().intervalDuration);
        assertEquals(NetworkType.CONNECTED, periodic.getWorkSpec().constraints.getRequiredNetworkType());
        assertTrue(periodic.getTags().contains(WebMonitorWorker.UNIQUE_WORK_NAME));
        assertFalse(periodic.getTags().contains(WebMonitorWorker.IMMEDIATE_WORK_NAME));
        assertNotEquals(periodic.getId(), immediate.getId());
        assertNotEquals(WebMonitorWorker.UNIQUE_WORK_NAME, WebMonitorWorker.IMMEDIATE_WORK_NAME);
    }

    @Test
    @Config(shadows = RecordingConfigurationStore.class)
    public void sandboxImmediateCheckForwardsOnceAndReturnsWithoutLocalWorkManager() {
        assertNull(WebMonitorWorker.enqueueImmediateCheck(remoteContext()));
        assertEquals(1, RecordingConfigurationStore.calls);
        assertTrue(RecordingConfigurationStore.immediate);
    }

    @Test
    @Config(shadows = RecordingConfigurationStore.class)
    public void sandboxPeriodicSyncForwardsOnceWithoutLocalWorkManager() {
        WebMonitorWorker.syncPeriodicWork(remoteContext());
        assertEquals(1, RecordingConfigurationStore.calls);
        assertFalse(RecordingConfigurationStore.immediate);
    }

    private Context remoteContext() {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public String getPackageName() {
                return Application.getProcessName() + ".remote_test";
            }

            @Override
            public Context getApplicationContext() {
                throw new AssertionError("Sandbox scheduling must not access local WorkManager");
            }
        };
    }

    @Implements(value = ConfigurationStore.class, isInAndroidSdk = false)
    public static class RecordingConfigurationStore {
        static int calls;
        static boolean immediate;

        @Implementation
        protected static void scheduleMonitors(boolean runImmediately) {
            calls++;
            immediate = runImmediately;
        }
    }
}
