package com.cylonid.nativealpha.media;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.PowerManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowService;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BackgroundPlaybackServiceTest {
    private static final String STOP_ACTION =
            "com.cylonid.nativealpha.STOP_BACKGROUND_MEDIA";
    private static final String CHANNEL_ID = "background_media_playback";

    private Application application;
    private ServiceController<? extends BackgroundPlaybackService> serviceController;

    @Before
    public void setUp() {
        application = RuntimeEnvironment.getApplication();
        clearServiceState();
        application.getSharedPreferences(CHANNEL_ID, Context.MODE_PRIVATE)
                .edit().clear().commit();
        Shadows.shadowOf(application).clearStartedServices();
    }

    @After
    public void tearDown() {
        if (serviceController != null) {
            try {
                serviceController.destroy();
            } finally {
                serviceController = null;
            }
        }
        clearServiceState();
        application.getSharedPreferences(CHANNEL_ID, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void playbackEndingBeforeServiceStartStillCompletesForegroundContract() {
        FakeClient client = new FakeClient(88, "Short clip");
        try (ActivityController<FakeActivity> activity =
                     Robolectric.buildActivity(FakeActivity.class).setup()) {
            assertTrue(BackgroundPlaybackService.start(activity.get(), client));
            BackgroundPlaybackService.stop(application, client);
            assertFalse(BackgroundPlaybackService.isActive(client));
            serviceController = Robolectric.buildService(BackgroundPlaybackService.class).create();
            serviceController.startCommand(0, 1);
            ShadowService service = Shadows.shadowOf(serviceController.get());
            assertEquals(20000, service.getLastForegroundNotificationId());
            assertTrue(service.isForegroundStopped());
            assertTrue(service.isStoppedBySelf());
            assertNull(ReflectionHelpers.getField(serviceController.get(), "wakeLock"));
        }
    }

    @Test
    public void processNamesRouteToMatchingManifestServices() {
        Class<?>[] sandboxClasses = {
                BackgroundPlaybackService.Sandbox0.class,
                BackgroundPlaybackService.Sandbox1.class,
                BackgroundPlaybackService.Sandbox2.class,
                BackgroundPlaybackService.Sandbox3.class,
                BackgroundPlaybackService.Sandbox4.class,
                BackgroundPlaybackService.Sandbox5.class,
                BackgroundPlaybackService.Sandbox6.class,
                BackgroundPlaybackService.Sandbox7.class
        };

        String packageName = application.getPackageName();
        assertSame(BackgroundPlaybackService.class,
                BackgroundPlaybackService.serviceClassForProcess(packageName));
        for (int slot = 0; slot < sandboxClasses.length; slot++) {
            assertSame(sandboxClasses[slot], BackgroundPlaybackService.serviceClassForProcess(
                    packageName + ":web_sandbox_" + slot));
        }
    }

    @Test
    public void generatedManifestKeepsEachServicePrivateInItsOwningProcess()
            throws PackageManager.NameNotFoundException {
        String packageName = application.getPackageName();
        PackageManager packageManager = application.getPackageManager();
        int flags = PackageManager.GET_SERVICES;

        PackageInfo packageInfo = packageManager.getPackageInfo(
                packageName, PackageManager.GET_PERMISSIONS);
        List<String> permissions = Arrays.asList(packageInfo.requestedPermissions);
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE"));
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"));
        assertTrue(permissions.contains("android.permission.WAKE_LOCK"));
        assertTrue(permissions.contains("android.permission.POST_NOTIFICATIONS"));

        assertService(packageManager.getServiceInfo(
                new ComponentName(packageName, BackgroundPlaybackService.class.getName()),
                flags), packageName);
        for (int slot = 0; slot < 8; slot++) {
            Class<?> serviceClass = BackgroundPlaybackService.serviceClassForProcess(
                    packageName + ":web_sandbox_" + slot);
            assertService(packageManager.getServiceInfo(
                    new ComponentName(packageName, serviceClass.getName()), flags),
                    packageName + ":web_sandbox_" + slot);
        }
    }

    @Test
    public void startingTwiceKeepsBothOwnersAndOneOwnerStopPreservesTheOther() {
        FakeClient first = new FakeClient(41, "First window");
        FakeClient second = new FakeClient(41, "Second window");

        try (ActivityController<FakeActivity> activity =
                     Robolectric.buildActivity(FakeActivity.class).setup()) {
            assertTrue(BackgroundPlaybackService.start(activity.get(), first));
            assertTrue(BackgroundPlaybackService.start(activity.get(), second));

            List<Intent> startedServices = Shadows.shadowOf(application).getAllStartedServices();
            assertEquals(2, startedServices.size());
            assertEquals(BackgroundPlaybackService.class.getName(),
                    startedServices.get(0).getComponent().getClassName());
            assertTrue(BackgroundPlaybackService.isActive(first));
            assertTrue(BackgroundPlaybackService.isActive(second));

            serviceController = Robolectric.buildService(
                    BackgroundPlaybackService.class).create();
            ShadowService shadowService = Shadows.shadowOf(serviceController.get());
            serviceController.startCommand(0, 1);

            assertNotNull(shadowService.getLastForegroundNotification());
            assertEquals(20000, shadowService.getLastForegroundNotificationId());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                        serviceController.get().getForegroundServiceType());
            } else {
                int foregroundType = ReflectionHelpers.callInstanceMethod(
                        shadowService, "getForegroundServiceType");
                assertEquals(0, foregroundType);
            }
            assertTrue(Shadows.shadowOf(application.getSystemService(PowerManager.class))
                    .getLatestWakeLock().isHeld());

            BackgroundPlaybackService.stop(application, first);

            assertFalse(BackgroundPlaybackService.isActive(first));
            assertTrue(BackgroundPlaybackService.isActive(second));
            assertEquals(0, first.stopCount);
            assertEquals(0, second.stopCount);
            assertTrue(shadowService.isLastForegroundNotificationAttached());
            assertTrue(Shadows.shadowOf(application.getSystemService(PowerManager.class))
                    .getLatestWakeLock().isHeld());
        }
    }

    @Test
    public void notificationStopStopsBothOwnersAndRemovesForegroundAndWakeLock()
            throws PendingIntent.CanceledException {
        FakeClient first = new FakeClient(7, "First window");
        FakeClient second = new FakeClient(7, "Second window");

        try (ActivityController<FakeActivity> activity =
                     Robolectric.buildActivity(FakeActivity.class).setup()) {
            assertTrue(BackgroundPlaybackService.start(activity.get(), first));
            assertTrue(BackgroundPlaybackService.start(activity.get(), second));

            serviceController = Robolectric.buildService(
                    BackgroundPlaybackService.class).create();
            ShadowService shadowService = Shadows.shadowOf(serviceController.get());
            serviceController.startCommand(0, 1);

            Notification notification = shadowService.getLastForegroundNotification();
            assertNotNull(notification);
            Notification.Action stop = notification.actions[0];
            PendingIntent pendingStop = stop.actionIntent;
            assertEquals(STOP_ACTION,
                    Shadows.shadowOf(pendingStop).getSavedIntent().getAction());

            Shadows.shadowOf(application).clearStartedServices();
            pendingStop.send();
            Intent deliveredStop = Shadows.shadowOf(application).getNextStartedService();
            assertNotNull(deliveredStop);
            assertEquals(STOP_ACTION, deliveredStop.getAction());

            serviceController.get().onStartCommand(deliveredStop, 0, 2);

            assertEquals(1, first.stopCount);
            assertEquals(1, second.stopCount);
            assertFalse(BackgroundPlaybackService.isActive(first));
            assertFalse(BackgroundPlaybackService.isActive(second));
            assertTrue(shadowService.isStoppedBySelf());
            assertTrue(shadowService.isForegroundStopped());
            assertTrue(shadowService.getNotificationShouldRemoved());
            assertFalse(Shadows.shadowOf(application.getSystemService(PowerManager.class))
                    .getLatestWakeLock().isHeld());
        }
    }

    @Test
    public void taskRemovalStopsEveryOwnerAndLeavesNoForegroundResources() {
        FakeClient first = new FakeClient(9, "First window");
        FakeClient second = new FakeClient(9, "Second window");

        try (ActivityController<FakeActivity> activity =
                     Robolectric.buildActivity(FakeActivity.class).setup()) {
            assertTrue(BackgroundPlaybackService.start(activity.get(), first));
            assertTrue(BackgroundPlaybackService.start(activity.get(), second));

            serviceController = Robolectric.buildService(
                    BackgroundPlaybackService.class).create();
            ShadowService shadowService = Shadows.shadowOf(serviceController.get());
            serviceController.startCommand(0, 1);
            serviceController.get().onTaskRemoved(new Intent());

            assertEquals(1, first.stopCount);
            assertEquals(1, second.stopCount);
            assertFalse(BackgroundPlaybackService.isActive(first));
            assertFalse(BackgroundPlaybackService.isActive(second));
            assertTrue(shadowService.isStoppedBySelf());
            assertTrue(shadowService.isForegroundStopped());
            assertFalse(shadowService.isLastForegroundNotificationAttached());
            assertFalse(Shadows.shadowOf(application.getSystemService(PowerManager.class))
                    .getLatestWakeLock().isHeld());
        }
    }

    @Test
    @Config(sdk = 35)
    public void postNotificationPermissionIsRequestedOnceOnFirstPlay() {
        FakeClient first = new FakeClient(12, "First window");
        FakeClient second = new FakeClient(13, "Second window");

        try (ActivityController<FakeActivity> activity =
                     Robolectric.buildActivity(FakeActivity.class).setup()) {
            assertTrue(BackgroundPlaybackService.start(activity.get(), first));
            ShadowActivity.PermissionsRequest request =
                    Shadows.shadowOf(activity.get()).getLastRequestedPermission();
            assertNotNull(request);
            assertArrayEquals(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    request.requestedPermissions);
            assertEquals(4206, request.requestCode);

            assertTrue(BackgroundPlaybackService.start(activity.get(), second));
            assertSame(request,
                    Shadows.shadowOf(activity.get()).getLastRequestedPermission());
            assertTrue(application.getSharedPreferences(CHANNEL_ID, Context.MODE_PRIVATE)
                    .getBoolean("asked", false));
        }
    }

    private void assertService(ServiceInfo info, String expectedProcess) {
        assertFalse(info.exported);
        assertEquals(expectedProcess, info.processName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    info.getForegroundServiceType());
        }
        assertNull(info.permission);
    }

    private void clearServiceState() {
        Object clients = ReflectionHelpers.getStaticField(
                BackgroundPlaybackService.class, "clients");
        if (clients instanceof List) {
            ((List<?>) clients).clear();
        }
        ReflectionHelpers.setStaticField(
                BackgroundPlaybackService.class, "runningService", null);
    }

    public static class FakeActivity extends Activity {}

    private static class FakeClient implements BackgroundPlaybackService.Client {
        private final int webAppId;
        private final String title;
        private int stopCount;

        FakeClient(int webAppId, String title) {
            this.webAppId = webAppId;
            this.title = title;
        }

        @Override
        public int getWebAppId() {
            return webAppId;
        }

        @Override
        public String getPlaybackTitle() {
            return title;
        }

        @Override
        public Intent getPlaybackIntent() {
            return new Intent(RuntimeEnvironment.getApplication(), FakeActivity.class);
        }

        @Override
        public void stopPlayback() {
            stopCount++;
        }
    }
}
