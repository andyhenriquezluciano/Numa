package dev.sfg.numa.mobile.lockscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.ui.text.uiText
import kotlinx.coroutines.CancellationException

@Composable
internal fun LockScreenLyrics(state: PlaybackSnapshot, graph: NumaGraph, modifier: Modifier = Modifier) {
    val track = state.currentTrack?.let { if (state.durationMs > 0) it.copy(durationMs = state.durationMs) else it }
    val lines by produceState<List<LyricLine>?>(null, track?.id, track?.durationMs) {
        value = null
        value = if (track == null) emptyList() else try {
            if (track.isLocal) dev.sfg.numa.mobile.local.localPlaybackLyrics(graph, track) else graph.lyrics.lyrics(track)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }
    val available = lines.orEmpty()
    val position = state.positionMs
    val index = available.indexOfLast { it.startMs?.let { start -> start <= position } == true }
    val current = available.getOrNull(index)
    val expired = current?.endMs?.let { position > it + 1_500L } == true
    val text = when {
        track == null -> ""
        lines == null -> uiText("Finding lyrics…", "Buscando letra…")
        available.isEmpty() -> uiText("Lyrics unavailable", "Letra no disponible")
        available.none { it.startMs != null } -> available.first().text
        current == null || expired -> "♪"
        else -> current.text.ifBlank { "♪" }
    }
    val next = if (index >= 0) available.getOrNull(index + 1)?.text.orEmpty() else ""
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(text to next, label = "LockScreenLyric") { (line, upcoming) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(line, color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (upcoming.isNotBlank()) Text(upcoming, color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
