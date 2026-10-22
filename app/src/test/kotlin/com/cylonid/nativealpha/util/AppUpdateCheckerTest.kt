package com.cylonid.nativealpha.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateCheckerTest {

    @Test
    fun testCleanVersion() {
        assertEquals("1.5.4", AppUpdateChecker.cleanVersion("v1.5.4"))
        assertEquals("1.5.4", AppUpdateChecker.cleanVersion("V1.5.4"))
        assertEquals("1.5.4", AppUpdateChecker.cleanVersion(" 1.5.4 "))
        assertEquals("1.5.4", AppUpdateChecker.cleanVersion("v1.5.4-beta.1"))
        assertEquals("2.0.0", AppUpdateChecker.cleanVersion("v2.0.0+build123"))
    }

    @Test
    fun testIsNewerVersion_patchUpgrade() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.5.4", "1.5.3"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.5.4", "1.5.3"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.5.10", "1.5.3"))
    }

    @Test
    fun testIsNewerVersion_minorAndMajorUpgrade() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.6.0", "1.5.3"))
        assertTrue(AppUpdateChecker.isNewerVersion("2.0.0", "1.5.3"))
        assertTrue(AppUpdateChecker.isNewerVersion("v2.1.0", "v1.9.9"))
    }

    @Test
    fun testIsNewerVersion_sameOrOlder() {
        assertFalse(AppUpdateChecker.isNewerVersion("1.5.3", "1.5.3"))
        assertFalse(AppUpdateChecker.isNewerVersion("v1.5.3", "1.5.3"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.5.2", "1.5.3"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.4.9", "1.5.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun testIsNewerVersion_differentSegmentLengths() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.5.3.1", "1.5.3"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.5", "1.5.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.5.0", "1.5"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.5.1", "1.5"))
    }

    @Test
    fun testIsNewerVersion_invalidStrings() {
        assertFalse(AppUpdateChecker.isNewerVersion("", "1.5.3"))
        assertFalse(AppUpdateChecker.isNewerVersion("abc", "1.5.3"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.5.4", "invalid"))
    }

    @Test
    fun testAutoCheckPreferences() {
        val context = org.robolectric.RuntimeEnvironment.application
        // Default is enabled
        assertTrue(AppUpdateChecker.isAutoCheckEnabled(context))

        AppUpdateChecker.setAutoCheckEnabled(context, false)
        assertFalse(AppUpdateChecker.isAutoCheckEnabled(context))

        AppUpdateChecker.setAutoCheckEnabled(context, true)
        assertTrue(AppUpdateChecker.isAutoCheckEnabled(context))
    }

    @Test
    fun testSnoozeLogic() {
        val context = org.robolectric.RuntimeEnvironment.application
        val baseTime = 1_000_000L

        assertFalse(AppUpdateChecker.isSnoozed(context, baseTime))

        AppUpdateChecker.snooze(context, 7 * 24 * 60 * 60 * 1000L)
        // Immediately snoozed relative to current time
        val now = System.currentTimeMillis()
        assertTrue(AppUpdateChecker.isSnoozed(context, now + 1000))
        // 8 days later, snooze expired
        assertFalse(AppUpdateChecker.isSnoozed(context, now + 8 * 24 * 60 * 60 * 1000L))
    }

    @Test
    fun testIgnoredVersionPreferences() {
        val context = org.robolectric.RuntimeEnvironment.application
        val prefs = context.getSharedPreferences(AppUpdateChecker.PREFS_NAME, android.content.Context.MODE_PRIVATE)

        AppUpdateChecker.clearIgnoredVersion(context)
        org.junit.Assert.assertNull(AppUpdateChecker.getIgnoredVersion(context) as Any?)

        prefs.edit().putString(AppUpdateChecker.KEY_IGNORED_VERSION, "v1.6.1").apply()
        assertEquals("v1.6.1", AppUpdateChecker.getIgnoredVersion(context))

        AppUpdateChecker.clearIgnoredVersion(context)
        org.junit.Assert.assertNull(AppUpdateChecker.getIgnoredVersion(context) as Any?)
    }
}

