package dev.sfg.numa.mobile.local

import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun localPlaybackLyrics(graph: NumaGraph, track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
    val saved = graph.localLibrary.lyricsFor(track)
    if (!graph.networkMonitor.isOnline.value || !graph.localLibrary.automaticLyricsNeedRefresh(track)) return@withContext saved
    try {
        val refreshed = graph.lyrics.lyrics(track)
        if (refreshed.isNotEmpty() && graph.localLibrary.saveAutomaticLyrics(track.id, refreshed, replaceAutomatic = true)) {
            return@withContext graph.localLibrary.lyricsFor(track)
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { /* Retain the saved transcript when a provider is unavailable. */ }
    saved
}
