package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.model.LyricLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Give compatible word timing a bounded chance before settling for line-only lyrics. */
internal suspend fun firstSynchronizedLyrics(
    primary: suspend () -> List<LyricLine>,
    secondary: suspend () -> List<LyricLine>,
    recordingMatched: (List<LyricLine>) -> Boolean = { false },
): List<LyricLine> = coroutineScope {
    val fallback = async { lyricsAttempt(secondary) }
    val preferred = lyricsAttempt(primary)
    if (preferred.any { it.startMs != null }) {
        if (!preferred.hasWordTiming()) {
            val richer = withTimeoutOrNull(3_000) { fallback.await() }
            if (richer != null && richer.hasWordTiming() && sameLyricTranscript(preferred, richer)
                && (recordingMatched(richer) || compatibleLyricTiming(preferred, richer))) return@coroutineScope richer
        }
        fallback.cancel()
        return@coroutineScope preferred
    }
    val alternative = fallback.await()
    when {
        alternative.any { it.startMs != null } -> alternative
        preferred.isNotEmpty() -> preferred
        else -> alternative
    }
}

internal suspend fun firstWordTimedLyrics(
    sources: List<suspend () -> List<LyricLine>>,
    reference: suspend () -> List<LyricLine> = { emptyList() },
    recordingMatched: (List<LyricLine>) -> Boolean = { false },
): List<LyricLine> = coroutineScope {
    val results = Channel<List<LyricLine>>(sources.size.coerceAtLeast(1))
    val jobs = sources.map { source -> launch {
        val candidate = lyricsAttempt(source).withoutEditorialNotes()
        val matched = reference()
        val compatible = matched.none { it.startMs != null } ||
            (sameLyricTranscript(matched, candidate) &&
                (recordingMatched(candidate) || compatibleLyricTiming(matched, candidate)))
        results.send(candidate.takeIf { compatible }.orEmpty())
    } }
    var synced = emptyList<LyricLine>()
    var plain = emptyList<LyricLine>()
    try {
        repeat(sources.size) {
            val lines = results.receive()
            if (lines.hasWordTiming()) return@coroutineScope lines
            if (synced.isEmpty() && lines.any { it.startMs != null }) synced = lines
            if (plain.isEmpty()) plain = lines
        }
        synced.ifEmpty { plain }
    } finally {
        jobs.forEach { it.cancel() }
        results.cancel()
    }
}

internal fun List<LyricLine>.hasWordTiming(): Boolean = any { line ->
    line.words.size > 1 && line.words.map { it.startMs }.distinct().size > 1
}

internal fun sameLyricTranscript(a: List<LyricLine>, b: List<LyricLine>): Boolean {
    fun tokens(lines: List<LyricLine>) = lines.joinToString(" ") { it.text }.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotBlank() }
    val left = tokens(a); val right = tokens(b)
    if (left.isEmpty() || right.isEmpty() || right.size.toDouble() / left.size !in 0.7..1.4) return false
    val shared = left.toSet().intersect(right.toSet()).size
    return shared.toDouble() / maxOf(left.toSet().size, right.toSet().size) >= 0.85
}

internal suspend fun lyricsAttempt(source: suspend () -> List<LyricLine>): List<LyricLine> =
    try { source() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

internal suspend fun requestLyricsText(client: OkHttpClient, request: Request): String? =
    suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching { if (it.isSuccessful) it.body.string() else null }
                    if (continuation.isActive) result.fold(
                        onSuccess = { value -> continuation.resume(value) },
                        onFailure = { error -> continuation.resumeWithException(error) })
                }
            }
        })
    }
