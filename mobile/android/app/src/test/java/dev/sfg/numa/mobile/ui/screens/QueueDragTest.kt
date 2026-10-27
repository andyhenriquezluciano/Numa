package dev.sfg.numa.mobile.ui.screens

import org.junit.Assert.*
import org.junit.Test

class QueueDragTest {
    @Test fun `drag maps upcoming offsets without touching current or history`() {
        assertEquals(5 to 8, queueDragMove("upcoming:0:a", "upcoming:3:b", 4))
        assertNull(queueDragMove("history:0:a", "upcoming:3:b", 4))
        assertNull(queueDragMove("upcoming:0:a", "history:3:b", 4))
    }
    @Test fun `dragging onto same position does not reorder`() {
        assertNull(queueDragMove("upcoming:2:a", "upcoming:2:a", 0))
        assertNull(queueDragMove(null, "upcoming:2:a", 0))
        assertNull(queueDragMove("upcoming:-1:a", "upcoming:2:a", 0))
    }
}
