package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricHighlightTimingTest {
    @Test fun firstWordDoesNotWaitForALaterParagraphBoundary() {
        val line = LyricLine("Hello world", startMs = 1_300,
            words = listOf(LyricWord("Hello", 1_000, 1_500), LyricWord("world", 1_500, 2_000)))
        assertEquals(1_000L, lyricHighlightStartMs(line))
    }

    @Test fun anEarlyParagraphAndLineOnlyLyricsKeepTheirTiming() {
        assertEquals(900L, lyricHighlightStartMs(LyricLine("Hello", startMs = 900,
            words = listOf(LyricWord("Hello", 1_000, 1_500)))))
        assertEquals(2_000L, lyricHighlightStartMs(LyricLine("Hello", startMs = 2_000)))
        assertNull(lyricHighlightStartMs(LyricLine("Hello")))
    }

    @Test fun theWordSweepStartsAtTheWordTimestampAndNeverBefore() {
        val word = LyricWord("Hello", 1_000, 1_500)
        assertEquals(0f, lyricWordProgress(word, 999f), 0f)
        assertEquals(0.5f, lyricWordProgress(word, 1_250f), 0.001f)
        assertEquals(1f, lyricWordProgress(word, 1_500f), 0f)
    }
}
