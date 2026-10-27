package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LyricsTimingValidationTest {
    private val reference = listOf(
        LyricLine("First phrase", 10_000), LyricLine("Another phrase", 30_000), LyricLine("Final phrase", 60_000),
    )
    private fun rich(shift: Long) = reference.map { line ->
        val start = line.startMs!! + shift
        line.copy(startMs = start, words = line.text.split(' ').mapIndexed { index, text ->
            LyricWord(text, start + index * 300, start + (index + 1) * 300)
        })
    }

    @Test fun constantLeadOrLagCannotReplaceTheMatchedTimeline() = runBlocking {
        for (offset in listOf(-2_500L, 2_500L)) {
            assertEquals(reference, firstSynchronizedLyrics({ reference }, { rich(offset) }))
        }
        assertEquals(rich(200), firstSynchronizedLyrics({ reference }, { rich(200) }))
    }

    @Test fun sameTextWithDriftingOrReorderedTimesIsNotCompatible() {
        assertFalse(compatibleLyricTiming(reference, rich(0).mapIndexed { index, line ->
            line.copy(words = line.words.map { it.copy(startMs = it.startMs + index * 2_000) })
        }))
        assertFalse(compatibleLyricTiming(reference, rich(0).reversed()))
    }

    @Test fun fastIncompatibleWordsDoNotHideASlowerCompatibleSource() = runBlocking {
        val compatible = rich(200)
        assertEquals(compatible, firstWordTimedLyrics(listOf(
            { rich(-2_500) }, { kotlinx.coroutines.delay(20); compatible },
        ), reference = { reference }))
    }

    @Test fun phraseAlignmentSupportsDifferentLineBreaksAndRepeatedChoruses() {
        val split = listOf(LyricLine("Sing again", 1_000), LyricLine("And again", 3_000),
            LyricLine("Sing again", 10_000), LyricLine("And again", 12_000))
        val grouped = listOf(1_000L, 10_000L).map { start ->
            LyricLine("Sing again And again", start, words = listOf(
                LyricWord("Sing", start), LyricWord("again", start + 300),
                LyricWord("And", start + 2_000), LyricWord("again", start + 2_300),
            ))
        }
        assertTrue(compatibleLyricTiming(split, grouped))
    }

    @Test fun candidateRankingRejectsRemixesAndPrioritizesTheClosestDuration() {
        fun candidate(title: String, album: String, duration: Int) = JSONObject()
            .put("track_name", title).put("artist_name", "Artist").put("album_name", album).put("duration", duration)
        val remix = candidate("Song (Remix)", "Album", 208)
        val compilation = candidate("Song", "Collection", 208)
        val album = candidate("Song", "Album", 209)
        val wrongDuration = candidate("Song", "Album", 220)
        val selected = rankedLyricsCandidates(JSONArray(listOf(remix, compilation, wrongDuration, album)),
            "Song", "Artist", "Album", 208_000)
        assertEquals(listOf(compilation, album), selected)
    }

    @Test fun lyricsPlusClockDurationMustMatchThePlayingEdition() {
        val metadata = JSONObject().put("title", "Song").put("artist", "Artist").put("totalDuration", "3:27.853")
        assertTrue(matchesLyricsTrack(metadata, "Song", "Artist", 208_000))
        assertFalse(matchesLyricsTrack(metadata, "Song", "Artist", 220_000))
    }

    @Test fun cacheIdentityChangesWithSourceAndEdition() {
        val track = Track("audio", "Song", "Artist", "Album", durationMs = 208_000)
        for (other in listOf(track.copy(id = "video"), track.copy(durationMs = 211_000), track.copy(album = "Live"))) {
            assertNotEquals(lyricCacheIdentity(track), lyricCacheIdentity(other))
        }
    }

    @Test fun recordingSpecificWordsCanOverrideGenericLrcWithTheWrongEditionTiming() = runBlocking {
        val selected = rich(2_700)
        assertEquals(selected, firstSynchronizedLyrics({ reference }, { selected }, recordingMatched = { it == selected }))
        assertEquals(reference, firstSynchronizedLyrics({ reference }, { selected }))
    }

    @Test fun shortAndLongEditionsCannotMatchDespiteTheSameTitleAndAlbum() {
        val short = JSONObject().put("track_name", "Example").put("artist_name", "Artist")
            .put("duration", 208).put("isrc", "GBAYE1400474")
        val long = JSONObject(short.toString()).put("duration", 211).put("isrc", "GBAYE0001456")
        assertFalse(matchesRecording(short, "Example", "Artist", 211_000))
        assertTrue(matchesRecording(long, "Example", "Artist", 211_000))
        assertFalse(matchesRecording(long.put("isrc", ""), "Example", "Artist", 211_000))
    }
}
