package com.cylonid.nativealpha

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.cylonid.nativealpha.activities.NewsActivity
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.util.EntryPointUtils
import com.google.android.material.button.MaterialButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-rUS-w320dp-h640dp")
class OnboardingTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearOnboardingState() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        generalInfo().edit().clear().commit()
        resetDataManager()
    }

    @After
    fun cleanUp() {
        generalInfo().edit().clear().commit()
        resetDataManager()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun freshUserSeesDynamicVersionAndConfirmationPersists() {
        assertTrue(EntryPointUtils.shouldShowOnboarding(context))

        Robolectric.buildActivity(NewsActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertEquals(
                context.getString(R.string.onboarding_version, BuildConfig.VERSION_NAME),
                activity.findViewById<TextView>(R.id.onboardingVersion).text.toString()
            )
            assertEquals(
                "Welcome to ShellAlpha",
                activity.findViewById<TextView>(R.id.onboardingHeadline).text.toString()
            )
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.onboardingEulaSection).visibility
            )
            assertEquals(
                "I understand, continue",
                activity.findViewById<MaterialButton>(R.id.btnContinue).text.toString()
            )

            activity.findViewById<MaterialButton>(R.id.btnContinue).performClick()

            assertTrue(activity.isFinishing)
            assertTrue(DataManager.getInstance().eulaData)
            assertEquals(BuildConfig.VERSION_CODE, DataManager.getInstance().lastShownUpdate)
            assertFalse(EntryPointUtils.shouldShowOnboarding(context))
            assertFalse(EntryPointUtils.shouldShowOnboarding(context))
        }
    }

    @Test
    fun existingUserWithOldVersionRecordSeesNewPageOnceWithoutReacceptingEula() {
        DataManager.getInstance().eulaData = true
        DataManager.getInstance().lastShownUpdate = BuildConfig.VERSION_CODE - 20

        assertTrue(EntryPointUtils.shouldShowOnboarding(context))

        Robolectric.buildActivity(NewsActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertEquals(
                View.GONE,
                activity.findViewById<View>(R.id.onboardingEulaSection).visibility
            )
            assertEquals(
                context.getString(R.string.onboarding_continue),
                activity.findViewById<MaterialButton>(R.id.btnContinue).text.toString()
            )

            activity.findViewById<MaterialButton>(R.id.btnContinue).performClick()

            assertEquals(BuildConfig.VERSION_CODE, DataManager.getInstance().lastShownUpdate)
            assertFalse(EntryPointUtils.shouldShowOnboarding(context))
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun chineseSystemLanguageRendersChineseOnboarding() {
        Robolectric.buildActivity(NewsActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertEquals(
                "欢迎使用 ShellAlpha",
                activity.findViewById<TextView>(R.id.onboardingHeadline).text.toString()
            )
            assertEquals(
                "了解并继续",
                activity.findViewById<MaterialButton>(R.id.btnContinue).text.toString()
            )
            assertEquals(
                "了解沙箱边界",
                activity.findViewById<TextView>(R.id.onboardingSandboxTitle).text.toString()
            )
        }
    }

    private fun generalInfo() = context.getSharedPreferences(
        "com.cylonid.nativealpha.GENERAL_INFO",
        Context.MODE_PRIVATE
    )

    private fun resetDataManager() {
        ReflectionHelpers.setField(DataManager.getInstance(), "websites", mutableListOf<Any>())
        ReflectionHelpers.setField(DataManager.getInstance(), "max_assigned_ID", -1)
        ReflectionHelpers.setField(
            DataManager.getInstance(),
            "settings",
            com.cylonid.nativealpha.model.GlobalSettings()
        )
        ReflectionHelpers.setField(DataManager.getInstance(), "appdata", null)
    }
}
