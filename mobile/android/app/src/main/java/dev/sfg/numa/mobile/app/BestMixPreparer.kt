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

package dev.sfg.numa.mobile.app

import android.util.Log
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.network.awaitResponse
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.playback.ResolvedStream
import dev.sfg.numa.mobile.playback.smart.BestMixSorter
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Analyzes cached or temporary saver audio incrementally with bounded network waits. */
internal class BestMixPreparer(private val graph: NumaGraph, cacheRoot: File) {
    private val audioDir = File(cacheRoot, "best-mix-v2")
    private val http = graph.http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()

    /** Ensures every analyzable song in [tracks] has a feature row. */
    suspend fun prepare(tracks: List<Track>, label: String, onProgress: (String) -> Unit) {
        val store = graph.bestMixFeatures
        val missing = withContext(Dispatchers.IO) {
            tracks.distinctBy(Track::id).filter { track ->
                val duration = track.durationMs / 1000.0
                track.id.isNotBlank() && duration in 0.0..7200.0 && !store.has(track.id, duration)
            }
        }
        Log.i(TAG, "$label reused ${tracks.size - missing.size}/${tracks.size} cached analyses")
        if (missing.isEmpty()) return
        onProgress(uiText("Preparing audio (0/${missing.size})…", "Preparando audio (0/${missing.size})…"))
        val analyzed = java.util.concurrent.atomic.AtomicInteger(0)
        withContext(Dispatchers.IO) {
            prepareBestMixTracks(missing, fetch = ::fetch, analyze = { track, file ->
                try { if (analyzeTrack(track, file)) analyzed.incrementAndGet() }
                finally { if (file.parentFile == audioDir) file.delete() }
            }, progress = { count ->
                val ready = analyzed.get()
                onProgress(uiText("Checked $count/${missing.size} · $ready analyzed", "Revisadas $count/${missing.size} · $ready analizadas"))
            })
        }
    }

    /** The saver stream on disk, or the listener's own download when the provider cannot serve one. */
    private suspend fun fetch(track: Track): File? {
        graph.downloads.getDownloadedFile(track.id)?.takeIf { it.isFile && it.length() > 0 }?.let { return it }
        if (track.isLocal) {
            return runCatching { File(java.net.URI(track.localUri)) }.getOrNull()?.takeIf { it.isFile }
        }
        val file = File(audioDir, "${java.security.MessageDigest.getInstance("SHA-256").digest(track.id.toByteArray()).joinToString("") { "%02x".format(it) }}.audio")
        if (file.length() in 1..MAX_BYTES) return file
        val saved = try {
            save(graph.streams.resolveSaver(track), file)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Best Mix download failed for ${track.id}", error)
            false
        }
        return if (saved) file else graph.downloads.getDownloadedFile(track.id)
    }

    /** Desktop's fetch: googlevideo only, at most 24 MB, 1 MB ranges each checked against Content-Range. */
    private suspend fun save(stream: ResolvedStream, file: File): Boolean {
        val size = stream.contentLength
        val host = runCatching { java.net.URI(stream.url) }.getOrNull()
        if (host?.scheme != "https" || host.host?.let { it.endsWith(".googlevideo.com") || it.endsWith(".c.youtube.com") } != true || size !in 1..MAX_BYTES) {
            return false
        }
        audioDir.mkdirs()
        val partial = File(audioDir, "${file.name}.part")
        try {
            partial.outputStream().use { out ->
                var offset = 0L
                while (offset < size) {
                    currentCoroutineContext().ensureActive()
                    val end = minOf(size - 1, offset + CHUNK_BYTES - 1)
                    val request = Request.Builder().url(stream.url)
                        .header("Accept-Encoding", "identity")
                        .apply { stream.requestHeaders.forEach { (name, value) -> header(name, value) } }
                        .header("Range", "bytes=$offset-$end")
                        .build()
                    http.newCall(request).awaitResponse().use { response ->
                        val range = response.header("Content-Range")?.trim()
                        if (response.code != 206 || range == null || !range.startsWith("bytes $offset-$end/")) {
                            throw IOException("Unexpected range reply ${response.code} $range")
                        }
                        val bytes = response.body.bytes()
                        if (bytes.size.toLong() != end - offset + 1) throw IOException("Short range")
                        out.write(bytes)
                    }
                    offset = end + 1
                }
            }
            return partial.renameTo(file)
        } finally {
            partial.delete()
        }
    }

    private fun analyzeTrack(track: Track, file: File) = try {
        BestMixSorter.analyze(graph.bestMixFeatures, track, file)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        // One unanalysable song keeps its queue position; it must not cancel the other workers.
        Log.w(TAG, "Best Mix analysis failed for ${track.id}", error)
        false
    }

    private companion object {
        const val TAG = "BestMixPreparer"
        /** Temporary analysis downloads use 1 MB ranges and a 24 MB ceiling. */
        const val CHUNK_BYTES = 1L shl 20
        const val MAX_BYTES = 24L * 1024 * 1024
    }
}
