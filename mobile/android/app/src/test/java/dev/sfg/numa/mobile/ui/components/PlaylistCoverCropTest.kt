package dev.sfg.numa.mobile.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class PlaylistCoverCropTest {
    @Test fun `wide cover cannot expose empty edges`() {
        assertEquals(Offset(150f, 0f), clampCoverPan(1000, 500, 300, 1f, Offset(900f, 900f)))
        assertEquals(Offset(-150f, 0f), clampCoverPan(1000, 500, 300, 1f, Offset(-900f, -900f)))
    }
    @Test fun `zoom allows bounded movement on both axes`() {
        assertEquals(Offset(450f, 150f), clampCoverPan(1000, 500, 300, 2f, Offset(900f, 900f)))
        assertEquals(Offset.Zero, clampCoverPan(500, 500, 300, 1f, Offset(100f, 100f)))
    }
    @Test fun `unmeasured viewport cannot shift cover`() {
        assertEquals(Offset.Zero, clampCoverPan(1000, 500, 0, 1f, Offset(100f, 100f)))
    }
}
