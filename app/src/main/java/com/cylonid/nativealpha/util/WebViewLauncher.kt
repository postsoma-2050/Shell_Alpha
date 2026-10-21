package com.cylonid.nativealpha.util

import android.app.Activity
import android.content.Context
import com.cylonid.nativealpha.model.WebApp
import androidx.appcompat.app.AppCompatActivity
import com.cylonid.nativealpha.R
import com.google.android.material.snackbar.Snackbar
import android.content.Intent
import android.net.Uri
import com.cylonid.nativealpha.model.SandboxManager
import com.cylonid.nativealpha.model.DataManager
import java.lang.NullPointerException

object WebViewLauncher {
    const val EXTRA_TARGET_URL = "browserTargetUrl"
    @JvmStatic
    fun startWebView(webapp: WebApp, c: Context) {
        try {
            val latest = DataManager.getInstance().getWebAppIgnoringGlobalOverride(webapp.ID, true) ?: return
            val intent = createWebViewIntent(latest, c) ?: return
            if (c !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(intent)
        } catch (e: NullPointerException) {
            NotificationUtils.showInfoSnackbar(
                c as AppCompatActivity,
                c.getString(R.string.webview_activity_launch_failed),
                Snackbar.LENGTH_LONG
            )
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun startWebViewInNewProcess(webapp: WebApp, a: Activity) {
        try {
            // The target component already belongs to its sandbox process. Other windows
            // and background media in the caller's process must remain alive.
            val intent = createWebViewIntent(webapp, a) ?: return
            a.intent.getStringExtra(EXTRA_TARGET_URL)?.let { intent.putExtra(EXTRA_TARGET_URL, it) }
            a.startActivity(intent)
            a.finishAndRemoveTask()
        } catch (e: NullPointerException) {
            NotificationUtils.showInfoSnackbar(
                a,
                a.getString(R.string.webview_activity_launch_failed),
                Snackbar.LENGTH_LONG
            )
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun getWebViewClass(webapp: WebApp): Class<*> {
        SandboxManager.getInstance().normalizeSandbox(webapp)
        val packageName = "com.cylonid.nativealpha"
        return try {
            if (SandboxManager.isValidContainerId(webapp.containerId)) {
                Class.forName(packageName + ".__WebViewActivity_" + webapp.containerId)
            } else {
                Class.forName("$packageName.WebViewActivity")
            }
        } catch (e: ClassNotFoundException) {
            Class.forName("$packageName.WebViewActivity")
        }
    }

    @JvmStatic
    fun createWebViewIntent(webapp: WebApp, c: Context?): Intent? {
        val webview_class = getWebViewClass(webapp)
        val intent = Intent(c, webview_class)
        if (webapp.isBiometricProtection) intent.flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        intent.putExtra(Const.INTENT_WEBAPPID, webapp.ID)
        intent.data = Uri.Builder().scheme("shellalpha").authority("lite-app")
            .appendPath(webapp.ID.toString()).build()
        intent.action = Intent.ACTION_VIEW
        return intent
    }
}
