package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.download.DownloadStore
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.ConcurrentLinkedQueue

class LyricsRepositoryCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val track = Track("queue-id", "Song", "Artist", "Album", durationMs = 208_000)
    private val requests = ConcurrentLinkedQueue<String>()
    private var available = true
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request.url.toString()
        val success = available && request.url.host == "lrclib.net" && request.url.encodedPath == "/api/get"
        val body = if (success) JSONObject().put("trackName", "Song").put("artistName", "Artist")
            .put("duration", request.url.queryParameter("duration")?.toDouble() ?: 208.0)
            .put("syncedLyrics", "[00:10.00]First phrase\n[00:30.00]Next phrase").toString() else "{}"
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (success) 200 else 404)
            .message("Test response").body(body.toResponseBody()).build()
    }.build()

    private fun legacy(store: DownloadStore): List<LyricLine> {
        val old = listOf(LyricLine("First phrase", 7_500, words = listOf(
            LyricWord("First", 7_500), LyricWord("phrase", 8_000),
        )))
        assertTrue(store.saveLyrics(track.id, old, listOf("Primera frase")))
        return old
    }

    @Test fun legacyWordTimedLyricsAreRevalidatedAndTheResultIsPersisted() = runBlocking {
        val store = DownloadStore(baseDir = temporary.newFolder())
        legacy(store)
        val repository = LyricsRepository(client, store = store) { "" }
        val lines = repository.lyrics(track)
        assertEquals(10_000L, lines.first().startMs)
        assertTrue(store.hasValidatedLyrics(track.id, repository.requestKey(track)))
        val count = requests.size
        assertEquals(lines, LyricsRepository(client, store = store) { "" }.lyrics(track))
        assertEquals(count, requests.size)
    }

    @Test fun offlineLegacyLyricsDoNotPreventRefreshAfterReconnection() = runBlocking {
        val store = DownloadStore(baseDir = temporary.newFolder())
        val old = legacy(store)
        var online = false
        val repository = LyricsRepository(client, store = store, isOnlineProvider = { online }) { "" }
        assertEquals(old, repository.lyrics(track))
        assertTrue(requests.isEmpty())
        online = true
        assertEquals(10_000L, repository.lyrics(track).first().startMs)
    }

    @Test fun failedRefreshKeepsOfflineLyricsWithoutMarkingThemValidated() = runBlocking {
        val store = DownloadStore(baseDir = temporary.newFolder())
        val old = legacy(store)
        available = false
        val repository = LyricsRepository(client, store = store) { "" }
        assertEquals(old, repository.lyrics(track))
        assertFalse(store.hasValidatedLyrics(track.id, repository.requestKey(track)))
        available = true
        assertEquals(10_000L, repository.lyrics(track).first().startMs)
    }

    @Test fun changingTheResolvedAudioSourceInvalidatesTheLookup() = runBlocking {
        var source = track.copy(id = "album-audio", durationMs = 210_000)
        val repository = LyricsRepository(client, sourceTrack = { source }) { "" }
        repository.lyrics(track)
        assertTrue(requests.any { it.contains("duration=210") })
        val key = repository.requestKey(track)
        val count = requests.size
        source = source.copy(id = "video-audio", durationMs = 220_000)
        assertNotEquals(key, repository.requestKey(track))
        repository.lyrics(track)
        assertTrue(requests.size > count)
        assertTrue(requests.any { it.contains("duration=220") })
    }
}
