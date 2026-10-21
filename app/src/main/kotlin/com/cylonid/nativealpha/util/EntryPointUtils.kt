package com.cylonid.nativealpha.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.cylonid.nativealpha.activities.NewsActivity
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.ConfigurationStore

object EntryPointUtils {
    private const val GENERAL_INFO_PREFERENCES = "com.cylonid.nativealpha.GENERAL_INFO"
    private const val ONBOARDING_REVISION_KEY = "shellAlphaOnboardingRevision"
    private const val CURRENT_ONBOARDING_REVISION = 1

    @JvmStatic
    fun entryPointReached(a: Activity) {
        if (shouldShowOnboarding(a)) {
            a.startActivity(
                Intent(a, NewsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        DataManager.getInstance().loadAppData()
    }

    @JvmStatic
    fun shouldShowOnboarding(context: Context): Boolean {
        val generalInfo = ConfigurationStore.preferences(GENERAL_INFO_PREFERENCES)
        return !DataManager.getInstance().eulaData ||
            generalInfo.getInt(ONBOARDING_REVISION_KEY, 0) < CURRENT_ONBOARDING_REVISION
    }

    @JvmStatic
    fun markOnboardingCompleted(context: Context) {
        ConfigurationStore.preferences(GENERAL_INFO_PREFERENCES).edit()
            .putInt(ONBOARDING_REVISION_KEY, CURRENT_ONBOARDING_REVISION)
            .apply()
    }
}
