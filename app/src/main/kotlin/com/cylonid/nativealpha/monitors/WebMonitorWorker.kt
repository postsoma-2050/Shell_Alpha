package com.cylonid.nativealpha.monitors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.WebViewLauncher
import com.himanshurawat.hasher.HashType
import com.himanshurawat.hasher.Hasher
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class WebMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val dataManager = DataManager.getInstance()
        dataManager.loadAppData()

        dataManager.activeWebsites
            .filter { it.isMonitorEnabled }
            .forEach { webApp -> monitor(webApp) }

        dataManager.saveWebAppData()
        return Result.success()
    }

    private fun monitor(webApp: WebApp) {
        try {
            val document = Jsoup.connect(webApp.baseUrl)
                .timeout(8_000)
                .userAgent(Const.DESKTOP_USER_AGENT)
                .get()
            val selector = webApp.monitorSelector.ifBlank { "body" }
            val content = document.select(selector).text()
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
        notificationManager.notify(webApp.ID, notification)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "WebMonitorWork"
        private const val CHANNEL_ID = "web_monitor_changes"

        @JvmStatic
        fun syncPeriodicWork(context: Context) {
            DataManager.getInstance().loadAppData()
            val hasMonitors = DataManager.getInstance().activeWebsites.any { it.isMonitorEnabled }
            val workManager = WorkManager.getInstance(context)
            if (!hasMonitors) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<WebMonitorWorker>(15, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
