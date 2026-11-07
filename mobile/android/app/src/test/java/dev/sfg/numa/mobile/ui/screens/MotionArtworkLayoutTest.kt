package dev.sfg.numa.mobile.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionArtworkLayoutTest {
    @Test fun portraitAndSquareCoversRemainEdgeToEdge() {
        for (aspect in listOf(0.4f, 0.75f, 1f, 2f)) {
            val frame = motionArtworkFrame(aspect, 1080f, 2400f, 220f, 1500f)
            assertEquals(0f, frame.left, 0f)
            assertEquals(0f, frame.top, 0f)
            assertEquals(1080f, frame.width, 0f)
            assertTrue(frame.height >= 1500f && frame.height <= 2400f)
        }
    }

    @Test fun controlsOverlapTheFadeInsteadOfCreatingAMargin() {
        val frame = motionArtworkFrame(0.75f, 1080f, 2400f, 220f, 1500f)
        assertEquals(1500f, frame.height * 0.88f, 0.001f)
    }

    @Test fun tallArtAndLandscapeStayInsideTheScreen() {
        assertEquals(2400f, motionArtworkFrame(0.3f, 1080f, 2400f, 220f, 1500f).height, 0f)
        val landscape = motionArtworkFrame(0.75f, 2400f, 1080f, 100f, 700f)
        assertEquals(2400f, landscape.width, 0f)
        assertEquals(1080f, landscape.height, 0f)
    }
}
