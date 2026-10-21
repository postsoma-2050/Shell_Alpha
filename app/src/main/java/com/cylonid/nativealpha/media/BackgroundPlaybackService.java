package com.cylonid.nativealpha.media;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cylonid.nativealpha.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Keeps the process that actually owns a playing WebView alive, including sandbox processes. */
public class BackgroundPlaybackService extends Service {
    public interface Client {
        int getWebAppId();
        String getPlaybackTitle();
        Intent getPlaybackIntent();
        void stopPlayback();
    }

    private static final String CHANNEL_ID = "background_media_playback";
    private static final String ACTION_STOP = "com.cylonid.nativealpha.STOP_BACKGROUND_MEDIA";
    private static final long WAKELOCK_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final List<WeakReference<Client>> clients = new ArrayList<>();
    private static volatile BackgroundPlaybackService runningService;
    private boolean foregroundStarted;
    private PowerManager.WakeLock wakeLock;

    public static boolean start(Activity activity, Client client) {
        synchronized (clients) {
            for (WeakReference<Client> reference : clients) {
                if (reference.get() == client) return true;
            }
            // Two document windows can play the same Lite App: track owners, not only app IDs.
            clients.add(new WeakReference<>(client));
        }
        try {
            ContextCompat.startForegroundService(activity, serviceIntent(activity));
            // Notification denial does not prohibit media FGS; ask once so its stop control is visible.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                    && !activity.getSharedPreferences(CHANNEL_ID, MODE_PRIVATE).getBoolean("asked", false)) {
                activity.getSharedPreferences(CHANNEL_ID, MODE_PRIVATE).edit().putBoolean("asked", true).apply();
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4206);
            }
            return true;
        } catch (IllegalStateException | SecurityException exception) {
            synchronized (clients) {
                clients.removeIf(reference -> reference.get() == null || reference.get() == client);
            }
            Log.w("BackgroundMedia", "Unable to start playback service", exception);
            return false;
        }
    }

    public static void stop(Context context, Client client) {
        synchronized (clients) {
            clients.removeIf(reference -> reference.get() == null || reference.get() == client);
        }
        if (runningService != null) runningService.refreshOrStop();
        // Let a queued start reach onStartCommand and fulfill Android's foreground contract.
        // Cancelling it with stopService before startForeground can crash the owning process.
    }

    public static boolean isActive(Client client) {
        synchronized (clients) {
            for (WeakReference<Client> reference : clients) {
                if (reference.get() == client) return true;
            }
            return false;
        }
    }

    private static List<Client> activeClients() {
        List<Client> active = new ArrayList<>();
        synchronized (clients) {
            clients.removeIf(reference -> reference.get() == null);
            for (WeakReference<Client> reference : clients) {
                Client client = reference.get();
                if (client != null) active.add(client);
            }
        }
        return active;
    }

    static Class<? extends BackgroundPlaybackService> serviceClassForProcess(String processName) {
        if (processName.endsWith(":web_sandbox_0")) return Sandbox0.class;
        if (processName.endsWith(":web_sandbox_1")) return Sandbox1.class;
        if (processName.endsWith(":web_sandbox_2")) return Sandbox2.class;
        if (processName.endsWith(":web_sandbox_3")) return Sandbox3.class;
        if (processName.endsWith(":web_sandbox_4")) return Sandbox4.class;
        if (processName.endsWith(":web_sandbox_5")) return Sandbox5.class;
        if (processName.endsWith(":web_sandbox_6")) return Sandbox6.class;
        if (processName.endsWith(":web_sandbox_7")) return Sandbox7.class;
        return BackgroundPlaybackService.class;
    }

    private static Intent serviceIntent(Context context) {
        return new Intent(context, serviceClassForProcess(Application.getProcessName()));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        runningService = this;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                getString(R.string.background_media_channel), NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) stopAllPlayback();
        else refreshOrStop();
        // A killed process has no surviving WebView to resume; never restart an empty player.
        return START_NOT_STICKY;
    }

    @SuppressLint("WakelockTimeout") // Held only while clients report playing; released on every stop/destroy.
    private void refreshOrStop() {
        List<Client> active = activeClients();
        if (active.isEmpty()) {
            if (!foregroundStarted) {
                try {
                    enterForeground(new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.mipmap.native_alpha_white)
                            .setContentTitle(getString(R.string.background_media_channel))
                            .setContentText(getString(R.string.background_media_stopped))
                            .setOnlyAlertOnce(true)
                            .build());
                } catch (IllegalStateException | SecurityException exception) {
                    Log.w("BackgroundMedia", "Unable to complete cancelled foreground start", exception);
                }
            }
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
            stopSelf();
            return;
        }
        Client latest = active.get(active.size() - 1);
        Intent stopIntent = serviceIntent(this).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(this, notificationId(), stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.native_alpha_white)
                .setContentTitle(active.size() == 1 ? latest.getPlaybackTitle()
                        : getResources().getQuantityString(R.plurals.background_media_multiple,
                                active.size(), active.size()))
                .setContentText(getString(R.string.background_media_notification))
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_baseline_close_24, getString(R.string.background_media_stop), stopAction);
        Intent returnIntent = latest.getPlaybackIntent();
        if (returnIntent != null) {
            builder.setContentIntent(PendingIntent.getActivity(this, notificationId(), returnIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        Notification notification = builder.build();
        try {
            enterForeground(notification);
            if (wakeLock == null) {
                PowerManager power = getSystemService(PowerManager.class);
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":background-media");
                wakeLock.setReferenceCounted(false);
            }
            if (!wakeLock.isHeld()) wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        } catch (IllegalStateException | SecurityException exception) {
            Log.w("BackgroundMedia", "Unable to keep playback in the foreground", exception);
            stopAllPlayback();
        }
    }

    @SuppressLint("ForegroundServiceType")
    private void enterForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(notificationId(), notification);
        }
        foregroundStarted = true;
    }

    private int notificationId() {
        // Notifications are package-wide even though these services run in separate processes.
        String process = Application.getProcessName();
        int slot = -1;
        for (int i = 0; i < 8; i++) {
            if (process.endsWith(":web_sandbox_" + i)) slot = i;
        }
        return 20000 + slot + 1;
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void stopAllPlayback() {
        List<Client> active = activeClients();
        synchronized (clients) {
            clients.clear();
        }
        for (Client client : active) client.stopPlayback();
        refreshOrStop();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopAllPlayback();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        runningService = null;
        releaseWakeLock();
        List<Client> active = activeClients();
        synchronized (clients) {
            clients.clear();
        }
        for (Client client : active) client.stopPlayback();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Each manifest component lives beside its sandbox WebView, rather than sharing browser data.
    public static class Sandbox0 extends BackgroundPlaybackService {}
    public static class Sandbox1 extends BackgroundPlaybackService {}
    public static class Sandbox2 extends BackgroundPlaybackService {}
    public static class Sandbox3 extends BackgroundPlaybackService {}
    public static class Sandbox4 extends BackgroundPlaybackService {}
    public static class Sandbox5 extends BackgroundPlaybackService {}
    public static class Sandbox6 extends BackgroundPlaybackService {}
    public static class Sandbox7 extends BackgroundPlaybackService {}
}
