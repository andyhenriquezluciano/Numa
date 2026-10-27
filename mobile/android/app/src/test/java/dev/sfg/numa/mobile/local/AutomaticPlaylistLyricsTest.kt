package dev.sfg.numa.mobile.local

import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class AutomaticPlaylistLyricsTest {
    @Test fun automaticTranscriptRetainsRealWordTimingAcrossRestart() {
        val line = LyricLine("First phrase", 23_981, 25_000, words = listOf(
            LyricWord("First", 23_981, 24_500), LyricWord("phrase", 24_500, 25_000),
        ))
        assertEquals(line, LocalLyricsParser.parse(automaticLyricsText(listOf(line))).single())
    }

    @Test fun `complete success omits zero missing lyrics`() {
        assertEquals("Se añadieron 100 letras.", automaticLyricsSummary(100, 0, 0, 0))
    }

    @Test fun `partial success reports missing count only when nonzero`() {
        val result = automaticLyricsSummary(150, 50, 0, 0)
        assertTrue(result.contains("Se añadieron 150 letras."))
        assertTrue(result.contains("No se encontraron letras para 50 canciones."))
    }

    @get:Rule val folder = TemporaryFolder()

    @Test fun `automatic transcript retains millisecond timing`() {
        val lines = listOf(LyricLine("First", 1234), LyricLine("Second", 65789))
        val restored = LocalLyricsParser.parse(automaticLyricsText(lines))
        assertEquals(lines.map { it.text }, restored.map { it.text })
        assertEquals(lines.map { it.startMs }, restored.map { it.startMs })
    }

    @Test fun `plain transcript has no invented timestamps`() {
        val lines = listOf(LyricLine("First"), LyricLine("Second"))
        assertEquals(lines, LocalLyricsParser.parse(automaticLyricsText(lines)))
    }

    @Test fun `automatic provenance survives library restart`() {
        val store = LocalStore(folder.newFolder("lyrics"))
        val song = LocalSong(id = "local:1", uri = "content://song/1", title = "Song",
            lyricsPath = "/lyrics/1.lrc", automaticLyrics = true, automaticLyricsVersion = AUTOMATIC_LYRICS_VERSION)
        store.save(LocalSnapshot(songs = listOf(song)))
        val restored = LocalStore(store.root).load().songs.single()
        assertTrue(restored.automaticLyrics)
        assertEquals(AUTOMATIC_LYRICS_VERSION, restored.automaticLyricsVersion)
        assertEquals(song.lyricsPath, restored.lyricsPath)
    }
}
