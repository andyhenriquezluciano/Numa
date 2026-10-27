package dev.sfg.numa.mobile.lockscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenPolicyTest {
    @Test fun staticArtworkKeepsItsFrameInExpandedMode() {
        assertTrue(LockScreenPolicy.framedArtwork(true, true, false))
        assertFalse(LockScreenPolicy.framedArtwork(true, true, true))
        assertTrue(LockScreenPolicy.framedArtwork(true, false, true))
        assertTrue(LockScreenPolicy.framedArtwork(false, true, true))
    }
    @Test fun automaticOpeningRequiresPermissionPlaybackAndLockedDevice() {
        for (enabled in listOf(false, true))
            for (playing in listOf(false, true))
                for (locked in listOf(false, true))
                    for (permission in listOf(false, true)) {
                        val show = LockScreenPolicy.autoOpen(enabled, playing, locked, permission)
                        if (enabled && playing && locked && permission) assertTrue(show) else assertFalse(show)
                    }
    }
    @Test fun disabledNeverShowsOverKeyguard() {
        for (locked in listOf(false, true))
            for (screenOff in listOf(false, true))
                for (preview in listOf(false, true))
                    assertFalse(LockScreenPolicy.shouldShow(false, locked, screenOff, preview))
    }

    @Test fun unlockedNotificationOpensNormalApplication() {
        assertFalse(LockScreenPolicy.shouldShow(true, false, false, false))
        assertTrue(LockScreenPolicy.shouldShow(true, true, false, false))
    }

    @Test fun optInSupportsExplicitPreviewAndForegroundScreenOff() {
        assertTrue(LockScreenPolicy.shouldShow(true, false, true, false))
        assertTrue(LockScreenPolicy.shouldShow(true, false, false, true))
    }

    @Test fun animationStopsWithDisplayOrPlayback() {
        assertFalse(LockScreenPolicy.animate(false, true))
        assertFalse(LockScreenPolicy.animate(true, false))
        assertFalse(LockScreenPolicy.animate(false, false))
        assertTrue(LockScreenPolicy.animate(true, true))
    }
}
