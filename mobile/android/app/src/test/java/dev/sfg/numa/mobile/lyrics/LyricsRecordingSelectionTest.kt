package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class LyricsRecordingSelectionTest {
    @Test fun exactRecordingBeatsShortAlbumEditionAndMislabelledLrc() = runBlocking {
        val fetched = ConcurrentLinkedQueue<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            fetched += request.url.toString()
            val body = when (request.url.host) {
                "lrclib.net" -> JSONObject().put("trackName", "Example").put("artistName", "Artist")
                    .put("duration", 211).put("syncedLyrics", "[00:21.281]First phrase\n[00:26.608]Second phrase").toString()
                "lyrics-api.binimum.org" -> JSONObject().put("results", JSONArray(listOf(
                    candidate("Example (Remix)", "Album", 211, "remix"),
                    candidate("Example", "Album", 208, "short"),
                    candidate("Example", "Collection", 211, "correct"),
                ))).toString()
                "fixture.invalid" -> """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
                    <p begin="23.981" end="25"><span begin="23.981" end="24.5">First </span><span begin="24.5" end="25">phrase</span></p>
                    <p begin="29.308" end="31"><span begin="29.308" end="30">Second </span><span begin="30" end="31">phrase</span></p>
                </div></body></tt>"""
                else -> "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Test response")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = LyricsRepository(client) { "" }
        val result = repository.lyrics(Track("queue", "Example", "Artist", "Album", durationMs = 211_000))
        assertEquals(23_981L, result.first().startMs)
        assertTrue(result.hasWordTiming())
        assertTrue(fetched.any { it == "https://fixture.invalid/correct.ttml" })
        assertFalse(fetched.any { it.contains("/short.ttml") || it.contains("/remix.ttml") })
    }

    private fun candidate(title: String, album: String, duration: Int, file: String): JSONObject = JSONObject()
        .put("track_name", title).put("artist_name", "Artist").put("album_name", album).put("duration", duration)
        .put("isrc", "GBAYE0001456").put("lyricsUrl", "https://fixture.invalid/$file.ttml")
}
