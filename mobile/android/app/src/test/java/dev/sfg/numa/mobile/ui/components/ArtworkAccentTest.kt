package dev.sfg.numa.mobile.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkAccentTest {
    @Test fun `small purple detail leaves a gray cover neutral`() {
        val accent = dominantArtworkAccent(100, 100) { x, y ->
            if (x in 40..59 && y in 40..59) 0xFFAA00DD.toInt() else 0xFF222222.toInt()
        }
        assertEquals(Color(0xFFB8B8B8), accent)
    }

    @Test fun `broad cover color wins over a vivid detail`() {
        val accent = dominantArtworkAccent(100, 100) { x, _ ->
            if (x < 20) 0xFFAA00DD.toInt() else 0xFF2C8ECA.toInt()
        }
        assertTrue(accent.blue > accent.green && accent.green > accent.red)
    }
}
