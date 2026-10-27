package dev.sfg.numa.mobile.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressGateTest {
    @Test fun firstAndFinalUpdatesAreAlwaysPublished() {
        val gate = DownloadProgressGate(100)
        assertTrue(gate.publish(0, false))
        assertFalse(gate.publish(1, false))
        assertTrue(gate.publish(2, true))
    }

    @Test fun intermediateUpdatesAreRateLimited() {
        val gate = DownloadProgressGate(100)
        assertTrue(gate.publish(0, false))
        assertFalse(gate.publish(99, false))
        assertTrue(gate.publish(100, false))
        assertFalse(gate.publish(101, false))
        assertTrue(gate.publish(200, false))
    }
}
