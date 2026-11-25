package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricHighlightTimingTest {
    @Test fun timelineFindsPauseBoundariesAndLastLineWithTheSameStart() {
        val timeline = LyricTimeline(listOf(
            LyricDisplayItem.Line(LyricLine("First", startMs = 1_000), 0),
            LyricDisplayItem.Pause(0, 2_000, 9_000),
            LyricDisplayItem.Line(LyricLine("Second", startMs = 9_000), 1),
            LyricDisplayItem.Line(LyricLine("Third", startMs = 9_000), 2),
        ))
        assertEquals(-1, timeline.activeIndex(999f))
        assertEquals(0, timeline.activeIndex(1_000f))
        assertEquals(1, timeline.activeIndex(2_000f))
        assertEquals(1, timeline.activeIndex(8_999f))
        assertEquals(3, timeline.activeIndex(9_000f))
    }

    @Test fun irregularProviderTimestampsKeepDisplayOrderAndPausePriority() {
        val timeline = LyricTimeline(listOf(
            LyricDisplayItem.Line(LyricLine("Later", startMs = 8_000), 0),
            LyricDisplayItem.Pause(0, 10_000, 14_000),
            LyricDisplayItem.Line(LyricLine("Earlier", startMs = 1_000), 1),
            LyricDisplayItem.Pause(1, 9_000, 12_000),
        ))
        assertEquals(2, timeline.activeIndex(8_000f))
        assertEquals(3, timeline.activeIndex(9_000f))
        assertEquals(1, timeline.activeIndex(10_000f))
        assertEquals(2, timeline.activeIndex(14_000f))
    }

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

    @Test fun visualLookaheadCompensatesDuringPlaybackOnly() {
        assertEquals(1_000L, lyricVisualPositionMs(910L, playing = true))
        assertEquals(910L, lyricVisualPositionMs(910L, playing = false))
    }

    @Test fun perSongCorrectionWorksForBothPausedAndPlayingLyrics() {
        assertEquals(1_100L, lyricVisualPositionMs(1_000L, playing = false, offsetMs = 100))
        assertEquals(990L, lyricVisualPositionMs(1_000L, playing = true, offsetMs = -100))
    }

    @Test fun instrumentalPauseEndsAtFirstSungWord() {
        val line = LyricLine("Previous", startMs = 1_000, endMs = 1_500)
        val next = LyricLine("Next", startMs = 9_250, words = listOf(LyricWord("Next", 9_000, 9_500)))
        assertEquals(1_900L to 9_000L, lyricPauseWindow(line, next))
    }
}
