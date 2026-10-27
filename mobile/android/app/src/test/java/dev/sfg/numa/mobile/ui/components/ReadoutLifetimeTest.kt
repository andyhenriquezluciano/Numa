package dev.sfg.numa.mobile.ui.components

import org.junit.Assert.*
import org.junit.Test

class ReadoutLifetimeTest {
    @Test fun replayingSameSongDuringExitGetsFreshGestureState() {
        val lifetime = ReadoutLifetime()
        val dismissed = lifetime.observe("song")
        assertEquals(dismissed, lifetime.observe(null))
        assertFalse(lifetime.isCurrent(dismissed))
        val replay = lifetime.observe("song")
        assertNotEquals(dismissed, replay)
        assertFalse(lifetime.isCurrent(dismissed))
        assertTrue(lifetime.isCurrent(replay))
    }

    @Test fun songChangeInvalidatesPendingDismissal() {
        val lifetime = ReadoutLifetime()
        val old = lifetime.observe("first")
        val next = lifetime.observe("second")
        assertNotEquals(old, next)
        assertFalse(lifetime.isCurrent(old))
        assertTrue(lifetime.isCurrent(next))
    }

    @Test fun playbackUpdatesDoNotInterruptGesture() {
        val lifetime = ReadoutLifetime()
        val active = lifetime.observe("song")
        repeat(10) { assertEquals(active, lifetime.observe("song")) }
        assertTrue(lifetime.isCurrent(active))
    }
}
