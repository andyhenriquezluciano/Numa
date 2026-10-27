package dev.sfg.numa.mobile.widget

import dev.sfg.numa.mobile.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLyricLinesTest {
    private val lines = listOf(LyricLine("First", 1000, 2000), LyricLine("Second", 6000, 7000))
    @Test fun neverAdvancesBeforeTheRealTimestamp() {
        assertEquals("♪" to "First", widgetLyricLines(lines, 999))
        assertEquals("First" to "Second", widgetLyricLines(lines, 1000))
        assertEquals("♪" to "Second", widgetLyricLines(lines, 5999))
        assertEquals("Second" to null, widgetLyricLines(lines, 6000))
    }
    @Test fun seekingBackwardsRestoresTheCorrectLine() {
        assertEquals("Second" to null, widgetLyricLines(lines, 6500))
        assertEquals("First" to "Second", widgetLyricLines(lines, 1500))
    }
    @Test fun missingAndUnsynchronizedLyricsRemainReadable() {
        assertEquals(null to null, widgetLyricLines(emptyList(), 1000))
        assertEquals("One" to "Two", widgetLyricLines(listOf(LyricLine("One"), LyricLine("Two")), 100_000))
    }
}
