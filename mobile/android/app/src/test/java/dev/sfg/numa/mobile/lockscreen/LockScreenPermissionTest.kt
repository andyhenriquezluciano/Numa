package dev.sfg.numa.mobile.lockscreen

import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test

class LockScreenPermissionTest {
    @Test fun repeatedEnableAndDisableAlwaysOpenSettingsAndDisableClosesFirst() {
        val events = mutableListOf<String>()
        repeat(2) {
            changeLockScreenPreference(true, { events += "enabled:$it" }, { events += "close" }, { events += "settings" })
            changeLockScreenPreference(false, { events += "enabled:$it" }, { events += "close" }, { events += "settings" })
        }
        assertEquals(List(2) { listOf("enabled:true", "settings", "enabled:false", "close", "settings") }.flatten(), events)
    }

    @Test fun appPermissionPageIsPreferredAndStopsFallbacks() {
        val opened = mutableListOf<Pair<String, String?>>()
        assertTrue(openLockScreenPermissionSettings("dev.sfg.numa.mobile") { action, target ->
            opened.add(action to target); true
        })
        assertEquals(listOf(Settings.ACTION_MANAGE_OVERLAY_PERMISSION to "dev.sfg.numa.mobile"), opened)
    }

    @Test fun unsupportedAppPageFallsBackToOverlayPermissionList() {
        val opened = mutableListOf<Pair<String, String?>>()
        assertTrue(openLockScreenPermissionSettings("dev.sfg.numa.mobile") { action, target ->
            opened.add(action to target); target == null
        })
        assertEquals(listOf(Settings.ACTION_MANAGE_OVERLAY_PERMISSION to "dev.sfg.numa.mobile",
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION to null), opened)
    }

    @Test fun devicesWithoutOverlayPagesCanOpenAppDetails() {
        val opened = mutableListOf<Pair<String, String?>>()
        assertTrue(openLockScreenPermissionSettings("dev.sfg.numa.mobile") { action, target ->
            opened.add(action to target); action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        })
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS to "dev.sfg.numa.mobile", opened.last())
        assertEquals(3, opened.size)
    }

    @Test fun failedNavigationIsReportedAfterTryingGeneralSettings() {
        val opened = mutableListOf<Pair<String, String?>>()
        assertFalse(openLockScreenPermissionSettings("dev.sfg.numa.mobile") { action, target ->
            opened.add(action to target); false
        })
        assertEquals(4, opened.size)
        assertEquals(Settings.ACTION_SETTINGS to null, opened.last())
    }
}
