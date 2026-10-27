package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LyricsSelectionTest {
    private val plain = listOf(LyricLine("First test phrase", 1_000, 3_000))
    private val rich = listOf(LyricLine("First test phrase", 1_000, 3_000,
        words = listOf(LyricWord("First", 1_000, 1_500), LyricWord("test", 1_500, 2_000), LyricWord("phrase", 2_000, 3_000))))

    @Test fun compatibleWordTimingIsPreferredToLineOnlyLyrics() = runBlocking {
        assertEquals(rich, firstSynchronizedLyrics({ plain }, { delay(20); rich }))
    }
    @Test fun unrelatedWordTimedTranscriptCannotReplaceMatchedLyrics() = runBlocking {
        val unrelated = rich.map { it.copy(text = "Entirely different source") }
        assertEquals(plain, firstSynchronizedLyrics({ plain }, { unrelated }))
    }
    @Test fun failedOrSlowRichProviderDoesNotPreventLineLyrics() = runBlocking {
        assertEquals(plain, firstSynchronizedLyrics({ plain }, { error("unavailable") }))
        assertEquals(plain, firstSynchronizedLyrics({ plain }, { delay(4_000); rich }))
    }
    @Test fun existingWordTimingDoesNotWaitForFallback() = runBlocking {
        assertEquals(rich, firstSynchronizedLyrics({ rich }, { delay(3_000); plain }))
    }
    @Test fun knownDurationRejectsDifferentEditsAndUnknownDurations() {
        val candidate = JSONObject().put("trackName", "Example").put("artistName", "Artist").put("duration", 210)
        assertTrue(matchesLyricsTrack(candidate, "Example", "Artist", 210_000))
        assertFalse(matchesLyricsTrack(candidate.put("duration", 215), "Example", "Artist", 210_000))
        assertFalse(matchesLyricsTrack(candidate.put("duration", 0), "Example", "Artist", 210_000))
    }
    @Test fun fastLineOnlyProviderDoesNotHideAnotherProvidersWordTiming() = runBlocking {
        assertEquals(rich, firstWordTimedLyrics(listOf({ plain }, { delay(20); rich }, { delay(4_000); emptyList() })))
    }
}
