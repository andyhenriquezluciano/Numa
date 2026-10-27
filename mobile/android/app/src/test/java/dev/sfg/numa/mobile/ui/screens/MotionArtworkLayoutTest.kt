package dev.sfg.numa.mobile.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionArtworkLayoutTest {
    @Test fun squareAndPortraitVersionsMeetTheSameControls() {
        val square = motionArtworkAspect(1f, 0.46f, 1080f, 1400f)
        val portrait = motionArtworkAspect(0.75f, 0.46f, 1080f, 1400f)
        assertEquals(square, portrait, 0.0001f)
        assertEquals(1400f, 1080f / square * 0.88f, 0.01f)
    }

    @Test fun scaledScreensKeepTheSameProportions() {
        assertEquals(motionArtworkAspect(1f, 0.46f, 1080f, 1400f),
            motionArtworkAspect(1f, 0.46f, 720f, 1400f * 720f / 1080f), 0.0001f)
    }

    @Test fun measurementBeforeLayoutUsesNaturalAspect() {
        assertEquals(1f, motionArtworkAspect(1f, 0.46f, 1080f, null), 0f)
        assertEquals(0.75f, motionArtworkAspect(0.75f, 0.46f, 1080f, 0f), 0f)
    }

    @Test fun tallArtworkAndSmallScreensStayWithinBounds() {
        assertEquals(0.46f, motionArtworkAspect(0.4f, 0.46f, 1080f, 1400f), 0f)
        val aspect = motionArtworkAspect(1f, 0.46f, 1080f, 3000f)
        assertTrue(aspect >= 0.46f && aspect <= 1f)
    }
}
