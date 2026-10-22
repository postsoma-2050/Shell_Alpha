package com.cylonid.nativealpha.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.cylonid.nativealpha.BuildConfig
import com.cylonid.nativealpha.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AppUpdateChecker {
    const val GITHUB_OWNER = "postsoma-2050"
    const val GITHUB_REPO = "Shell_Alpha"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RELEASES_WEB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    const val PREFS_NAME = "app_update_prefs"
    const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
    const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
    const val KEY_IGNORED_VERSION = "ignored_version"
    const val KEY_SNOOZE_UNTIL_TIMESTAMP = "snooze_until_timestamp"

    // 24 hours between silent background checks
    const val SILENT_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    // 7 days snooze duration when user delays or dismisses update
    const val SNOOZE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L

    fun isAutoCheckEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHECK_ENABLED, true)
    }

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHECK_ENABLED, enabled)
            .apply()
    }

    fun snooze(context: Context, durationMs: Long = SNOOZE_INTERVAL_MS) {
        val snoozeUntil = System.currentTimeMillis() + durationMs
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SNOOZE_UNTIL_TIMESTAMP, snoozeUntil)
            .apply()
    }

    fun isSnoozed(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val snoozeUntil = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SNOOZE_UNTIL_TIMESTAMP, 0L)
        return now < snoozeUntil
    }

    fun getIgnoredVersion(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IGNORED_VERSION, null)
    }

    fun clearIgnoredVersion(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_IGNORED_VERSION)
            .apply()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val downloadUrl: String?
    )

    sealed class CheckResult {
        data class UpdateAvailable(val releaseInfo: ReleaseInfo) : CheckResult()
        object UpToDate : CheckResult()
        object NoReleaseFound : CheckResult()
        data class Error(val exception: Throwable) : CheckResult()
    }

    /**
     * Compares remote and local version strings (e.g. "v1.5.4" vs "1.5.3").
     * Returns true if remote is strictly newer than local.
     */
    @VisibleForTesting
    fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        val cleanRemote = cleanVersion(remoteVersion)
        val cleanLocal = cleanVersion(localVersion)

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }

        if (remoteParts.isEmpty() || localParts.isEmpty()) {
            return false
        }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    @VisibleForTesting
    fun cleanVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
            .trim()
    }

    /**
     * Synchronous check on the network endpoint. Must be called off the main thread.
     */
    @VisibleForTesting
    fun checkLatestReleaseDirect(
        apiUrl: String = LATEST_RELEASE_API,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): CheckResult {
        return runCatching {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ShellAlpha/$currentVersion")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@runCatching CheckResult.NoReleaseFound
                }
                if (responseCode !in 200..299) {
                    throw IllegalStateException("GitHub API returned HTTP $responseCode")
                }

                val responseJson = connection.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }

                val jsonObject = JSONObject(responseJson)
                val tagName = jsonObject.optString("tag_name", "").trim()
                val versionName = cleanVersion(tagName)
                val name = jsonObject.optString("name", tagName)
                val body = jsonObject.optString("body", "")
                val htmlUrl = jsonObject.optString("html_url", RELEASES_WEB_URL)

                var apkDownloadUrl: String? = null
                val assets = jsonObject.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i)
                        val assetName = asset?.optString("name", "").orEmpty()
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val releaseInfo = ReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    name = name,
                    body = body,
                    htmlUrl = htmlUrl,
                    downloadUrl = apkDownloadUrl
                )

                if (isNewerVersion(versionName, currentVersion)) {
                    CheckResult.UpdateAvailable(releaseInfo)
                } else {
                    CheckResult.UpToDate
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { CheckResult.Error(it) }
    }

    /**
     * Silent check suitable for app startup.
     * Rate-limited to at most once per 24 hours.
     * Only displays dialog if an unignored update is available.
     */
    fun checkSilentlyOnStartup(activity: Activity) {
        if (!isAutoCheckEnabled(activity)) {
            return
        }

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        if (isSnoozed(activity, now)) {
            return
        }

        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        if (now - lastCheck < SILENT_CHECK_INTERVAL_MS) {
            return
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

        executor.execute {
            val result = checkLatestReleaseDirect()
            mainHandler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (result is CheckResult.UpdateAvailable) {
                    val ignoredVersion = prefs.getString(KEY_IGNORED_VERSION, null)
                    if (ignoredVersion != result.releaseInfo.tagName && ignoredVersion != result.releaseInfo.versionName) {
                        showUpdateDialog(activity, result.releaseInfo, isManual = false)
                    }
                }
            }
        }
    }

    /**
     * Manual check triggered by the user (e.g. from the About page).
     * Always shows feedback (progress toast, dialog, or up-to-date message).
     */
    fun checkManually(activity: Activity, onComplete: ((CheckResult) -> Unit)? = null) {
        Toast.makeText(activity, R.string.checking_for_updates, Toast.LENGTH_SHORT).show()

        executor.execute {
            val result = checkLatestReleaseDirect()
            mainHandler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                when (result) {
                    is CheckResult.UpdateAvailable -> {
                        showUpdateDialog(activity, result.releaseInfo, isManual = true)
                    }
                    is CheckResult.UpToDate, is CheckResult.NoReleaseFound -> {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is CheckResult.Error -> {
                        Toast.makeText(
                            activity,
                            R.string.update_check_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                onComplete?.invoke(result)
            }
        }
    }

    fun showUpdateDialog(activity: Activity, releaseInfo: ReleaseInfo, isManual: Boolean) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val displayTitle = if (releaseInfo.name.isNotBlank()) releaseInfo.name else "v${releaseInfo.versionName}"
        val notes = if (releaseInfo.body.isNotBlank()) releaseInfo.body.trim() else ""

        val message = activity.getString(
            R.string.update_available_msg,
            displayTitle,
            BuildConfig.VERSION_NAME,
            if (notes.isNotBlank()) notes else activity.getString(R.string.update_now)
        )

        val builder = MaterialAlertDialogBuilder(activity)
            .setIcon(R.drawable.ic_system_update_24)
            .setTitle(activity.getString(R.string.update_available_title))
            .setMessage(message)
            .setPositiveButton(R.string.update_now) { _, _ ->
                val targetUrl = releaseInfo.downloadUrl?.takeIf { it.isNotBlank() } ?: releaseInfo.htmlUrl
                openUrlInBrowser(activity, targetUrl)
            }
            .setNegativeButton(if (isManual) R.string.update_later else R.string.update_later_7d) { _, _ ->
                if (!isManual) {
                    snooze(activity)
                }
            }

        if (!isManual) {
            builder.setOnCancelListener {
                snooze(activity)
            }
            builder.setNeutralButton(R.string.update_ignore_version) { _, _ ->
                prefs.edit().putString(KEY_IGNORED_VERSION, releaseInfo.tagName).apply()
                Toast.makeText(activity, R.string.update_version_ignored, Toast.LENGTH_SHORT).show()
            }
        }

        builder.show()
    }

    private fun openUrlInBrowser(context: Context, url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
