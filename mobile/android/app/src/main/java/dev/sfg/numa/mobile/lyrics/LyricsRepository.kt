/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.download.DownloadStore
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native port of Numa desktop's am-lyrics → LRCLIB → YouTube resolver chain. */
class LyricsRepository(
    http: OkHttpClient,
    private val store: DownloadStore? = null,
    private val isOnlineProvider: () -> Boolean = { true },
    private val sourceTrack: (Track) -> Track = { it },
    /** Plain lyrics from YouTube Music's Lyrics tab, via the provider's `lyrics.youtube`. */
    private val youtubeLyrics: suspend (videoId: String) -> String,
) {
    private data class Metadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val videoId: String,
    )

    private val providerHttp = http.newBuilder().callTimeout(PROVIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    private class RequestLock(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val requestLocks = mutableMapOf<String, RequestLock>()
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>?): Boolean = size > 32
    }

    suspend fun lyrics(track: Track): List<LyricLine> {
        val lookup = sourceTrack(track)
        val key = lyricCacheIdentity(lookup)
        val request = synchronized(requestLocks) {
            requestLocks.getOrPut(key) { RequestLock() }.also { it.users++ }
        }
        try {
            return request.mutex.withLock { loadLyrics(track, lookup, key) }
        } finally {
            synchronized(requestLocks) {
                if (--request.users == 0) requestLocks.remove(key)
            }
        }
    }

    fun requestKey(track: Track): String = lyricCacheIdentity(sourceTrack(track))

    private suspend fun loadLyrics(track: Track, lookup: Track, key: String): List<LyricLine> = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val saved = store?.loadLyrics(track.id)
        val usableSaved = saved?.withoutEditorialNotes().orEmpty()
        if (!isOnlineProvider()) return@withContext usableSaved
        if (usableSaved.isSynchronized() && store?.hasValidatedLyrics(track.id, key) == true) {
            synchronized(cache) { cache[key] = usableSaved }
            return@withContext usableSaved
        }

        val metadata = lookup.lyricsMetadata() ?: return@withContext usableSaved

        // Prefer a matched lyric transcript over album commentary from rich-lyrics feeds.
        val verified = java.util.concurrent.ConcurrentHashMap.newKeySet<List<LyricLine>>()
        val matched = coroutineScope {
            val reference = async { lyricsAttempt { fromLrcLib(metadata).withoutEditorialNotes() } }
            firstSynchronizedLyrics(
                primary = { reference.await() },
                secondary = { fromAmLyrics(metadata, { reference.await() }, verified).withoutEditorialNotes() },
                recordingMatched = { it in verified },
            )
        }
        val result = matched.ifEmpty { usableSaved }.ifEmpty {
            runCatching { if (track.isLocal) emptyList() else LyricsParser.plain(youtubeLyrics(metadata.videoId)).withoutEditorialNotes() }
                .getOrElse { if (it is CancellationException) throw it; emptyList() }
        }

        if (matched.isNotEmpty()) {
            if (saved != null) {
                val translations = if (saved.map { it.text } == matched.map { it.text }) store.loadSpanishLyrics(track.id).orEmpty() else emptyList()
                store.saveLyrics(track.id, matched, translations, timingIdentity = key)
            }
            synchronized(cache) { cache[key] = matched }
        }
        result
    }

    private suspend fun fromAmLyrics(metadata: Metadata, reference: suspend () -> List<LyricLine>,
        verified: MutableSet<List<LyricLine>>): List<LyricLine> = firstWordTimedLyrics(
        listOf(
            { fromBiniLyrics(metadata, verified) }, { fromBetterLyrics(metadata) }, { fromLyricsPlus(metadata, verified) },
        ),
        reference,
        recordingMatched = { it in verified },
    )

    private suspend fun fromBiniLyrics(metadata: Metadata, verified: MutableSet<List<LyricLine>>): List<LyricLine> {
        val url = "https://lyrics-api.binimum.org/".toHttpUrl().newBuilder()
            .addQueryParameter("track", metadata.title)
            .addQueryParameter("artist", metadata.artist)
            .withAlbumAndDuration(metadata, "album", "duration")
            .build()
        val root = requestObject(url) ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        for (candidate in rankedLyricsCandidates(results, metadata.title, metadata.artist, metadata.album, metadata.durationMs)) {
            val lyricsUrl = candidate.optString("lyricsUrl").takeIf(String::isNotBlank) ?: continue
            val lines = try { requestText(lyricsUrl.toHttpUrl())?.let(LyricsParser::ttml).orEmpty() }
                catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
            if (lines.isNotEmpty() && !lines.containsEditorialNotes()) {
                if (matchesRecording(candidate, metadata.title, metadata.artist, metadata.durationMs)) verified += lines
                return lines
            }
        }
        return emptyList()
    }

    private suspend fun fromBetterLyrics(metadata: Metadata): List<LyricLine> {
        val url = "https://api.betterlyrics.org/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", metadata.title)
            .addQueryParameter("a", metadata.artist)
            .withAlbumAndDuration(metadata, "al", "d")
            .addQueryParameter("videoId", metadata.videoId)
            .build()
        val root = requestObject(url) ?: return emptyList()
        val ttml = root.optString("ttml").ifBlank { root.optString("lyrics") }
        return LyricsParser.ttml(ttml)
    }

    private suspend fun fromLyricsPlus(metadata: Metadata, verified: MutableSet<List<LyricLine>>): List<LyricLine> {
        var plain = emptyList<LyricLine>()
        for (server in LYRICS_PLUS_SERVERS) {
            val url = "$server/v2/lyrics/get".toHttpUrl().newBuilder()
                .addQueryParameter("title", metadata.title)
                .addQueryParameter("artist", metadata.artist)
                .withAlbumAndDuration(metadata, "album", "duration")
                .build()
            val lines = runCatching {
                val payload = requestObject(url) ?: return@runCatching emptyList()
                val identity = payload.optJSONObject("metadata") ?: return@runCatching emptyList()
                if (!matchesLyricsTrack(identity, metadata.title, metadata.artist, metadata.durationMs)) return@runCatching emptyList()
                LyricsParser.amPayload(payload).also { lines ->
                    if (matchesRecording(identity, metadata.title, metadata.artist, metadata.durationMs)) verified += lines
                }
            }
                .getOrElse { if (it is CancellationException) throw it; emptyList() }
            if (lines.containsEditorialNotes()) continue
            if (lines.isSynchronized()) return lines
            if (plain.isEmpty()) plain = lines
        }
        return plain
    }

    private suspend fun fromLrcLib(metadata: Metadata): List<LyricLine> {
        val exactUrl = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", metadata.title)
            .addQueryParameter("artist_name", metadata.artist)
            .withAlbumAndDuration(metadata, "album_name", "duration")
            .build()
        val exact = runCatching {
            requestObject(exactUrl)
                ?.takeIf { matchesLyricsTrack(it, metadata.title, metadata.artist, metadata.durationMs) }
                ?.let(LyricsParser::lrcLib).orEmpty().withoutEditorialNotes()
        }
            .getOrElse { if (it is CancellationException) throw it; emptyList() }
        if (exact.isSynchronized()) return exact

        val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", metadata.title)
            .addQueryParameter("artist_name", metadata.artist)
            .build()
        val search = runCatching { requestArray(searchUrl) }
            .getOrElse { if (it is CancellationException) throw it; null } ?: return exact
        val matches = rankedLyricsCandidates(search, metadata.title, metadata.artist, metadata.album, metadata.durationMs)
            .map(LyricsParser::lrcLib).filter { it.isNotEmpty() && !it.containsEditorialNotes() }
        return matches.firstOrNull { it.isSynchronized() } ?: exact.ifEmpty { matches.firstOrNull().orEmpty() }
    }

    private fun HttpUrl.Builder.withAlbumAndDuration(
        metadata: Metadata,
        albumKey: String,
        durationKey: String,
    ): HttpUrl.Builder = apply {
        if (metadata.album.isNotBlank()) addQueryParameter(albumKey, metadata.album)
        if (metadata.durationMs > 0) addQueryParameter(durationKey, (metadata.durationMs / 1_000).toString())
    }

    private suspend fun requestObject(url: HttpUrl): JSONObject? = requestText(url)?.let(::JSONObject)
    private suspend fun requestArray(url: HttpUrl): JSONArray? = requestText(url)?.let(::JSONArray)

    private suspend fun requestText(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return requestLyricsText(providerHttp, request)
    }

    private fun Track.lyricsMetadata(): Metadata? {
        val cleanTitle = title.cleanLookupText()
        val cleanArtist = artist.substringBefore(',').cleanLookupText()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        return Metadata(
            cleanTitle,
            cleanArtist,
            album.cleanLookupText().takeUnless { it.isEngagementMetric() }.orEmpty(),
            durationMs,
            id,
        )
    }

    private fun String.cleanLookupText(): String = replace(
        Regex("\\([^)]*(?:official|video|visualizer|lyrics?|audio|hd|4k)[^)]*\\)", RegexOption.IGNORE_CASE),
        "",
    ).replace(
        Regex("\\[[^]]*(?:official|video|visualizer|lyrics?|audio|hd|4k)[^]]*]", RegexOption.IGNORE_CASE),
        "",
    ).replace(Regex("\\s+-\\s+Topic$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ").trim()

    private fun String.isEngagementMetric(): Boolean = contains(
        Regex("\\b(?:plays?|views?|listeners?|subscribers?)\\b", RegexOption.IGNORE_CASE),
    )

    private fun List<LyricLine>.isSynchronized(): Boolean = any { it.startMs != null }

    private companion object {
        const val PROVIDER_TIMEOUT_SECONDS = 8L
        const val USER_AGENT = "Numa Android/2.0"
        val LYRICS_PLUS_SERVERS = listOf(
            "https://lyricsplus.binimum.org",
            "https://lyricsplus-seven.vercel.app",
            "https://lyricsplus.prjktla.workers.dev",
            "https://lyrics-plus-backend.vercel.app",
        )
    }
}
