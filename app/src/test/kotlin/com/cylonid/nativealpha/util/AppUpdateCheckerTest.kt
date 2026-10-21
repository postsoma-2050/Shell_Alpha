package com.cylonid.nativealpha.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
