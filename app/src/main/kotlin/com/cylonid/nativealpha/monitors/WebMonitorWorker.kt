package com.cylonid.nativealpha.monitors

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.annotation.VisibleForTesting
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.ConfigurationStore
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.WebViewLauncher
import com.himanshurawat.hasher.HashType
import com.himanshurawat.hasher.Hasher
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

class WebMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result = synchronized(MONITOR_LOCK) {
        val dataManager = DataManager.getInstance()
        dataManager.loadAppData()

        dataManager.activeWebsites
            .filter { it.isMonitorEnabled }
            .forEach { webApp -> monitor(webApp) }

        dataManager.saveWebAppData()
        Result.success()
    }

    private fun monitor(webApp: WebApp) {
        try {
            val uri = URI(webApp.baseUrl)
            if ((!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) ||
                uri.host.isNullOrBlank() || uri.rawUserInfo != null
            ) return
            val document = Jsoup.connect(webApp.baseUrl)
                .timeout(8_000)
                .userAgent(Const.DESKTOP_USER_AGENT)
                .get()
            val selector = webApp.monitorSelector.ifBlank { "body" }
            val elements = document.select(selector)
            // Missing content is a failed check, not an empty-page change.
            if (elements.isEmpty()) return
            val content = elements.text()
            val newHash = Hasher.Companion.hash(content, HashType.SHA_256)

            if (webApp.lastMonitorHash.isNotBlank() && webApp.lastMonitorHash != newHash) {
                showChangeNotification(webApp)
            }
            webApp.lastMonitorHash = newHash
        } catch (_: Exception) {
            // A failed fetch leaves the previous hash intact so the next run can retry.
        }
    }

    private fun showChangeNotification(webApp: WebApp) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.monitor_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val intent = WebViewLauncher.createWebViewIntent(webApp, applicationContext)
            ?: return
        intent.flags = intent.flags or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            webApp.ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.native_alpha)
            .setContentTitle(applicationContext.getString(R.string.monitor_notification_title))
            .setContentText(
                applicationContext.getString(R.string.monitor_notification_text, webApp.title)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            notificationManager.notify(webApp.ID, notification)
        } catch (_: SecurityException) {
            // Notification access can be revoked between checking and posting.
        }
    }

    companion object {
        @VisibleForTesting
        const val UNIQUE_WORK_NAME = "WebMonitorWork"
        @VisibleForTesting
        const val IMMEDIATE_WORK_NAME = "WebMonitorImmediateWork"
        private const val CHANNEL_ID = "web_monitor_changes"
        private val MONITOR_LOCK = Any()

        @JvmStatic
        @VisibleForTesting
        fun immediateRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<WebMonitorWorker>()
                .setConstraints(networkConstraints())
                .addTag(IMMEDIATE_WORK_NAME)
                .build()

        @JvmStatic
        @VisibleForTesting
        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<WebMonitorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .addTag(UNIQUE_WORK_NAME)
                .build()

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        @JvmStatic
        fun enqueueImmediateCheck(context: Context): Operation? {
            if (Application.getProcessName() != context.packageName) {
                ConfigurationStore.scheduleMonitors(true)
                return null
            }
            DataManager.getInstance().loadAppData()
            if (DataManager.getInstance().activeWebsites.none { it.isMonitorEnabled }) return null
            return WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                immediateRequest()
            )
        }

        @JvmStatic
        fun syncPeriodicWork(context: Context) {
            if (Application.getProcessName() != context.packageName) {
                ConfigurationStore.scheduleMonitors(false)
                return
            }
            DataManager.getInstance().loadAppData()
            val hasMonitors = DataManager.getInstance().activeWebsites.any { it.isMonitorEnabled }
            val workManager = WorkManager.getInstance(context)
            if (!hasMonitors) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest()
            )
        }
    }
}
