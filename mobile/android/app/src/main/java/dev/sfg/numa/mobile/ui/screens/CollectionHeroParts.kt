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

package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.glass.GlassAlertDialog

import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface

import dev.sfg.numa.mobile.ui.text.uiText

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.model.BestMixJob
import dev.sfg.numa.mobile.model.BrowseDetail
import dev.sfg.numa.mobile.model.LocalBestMixJob
import dev.sfg.numa.mobile.model.CatalogKind
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Playlists credit the most frequent artist when the owner is YouTube Music itself. */
internal fun collectionArtistName(detail: BrowseDetail): String {
    return if (detail.kind == CatalogKind.PLAYLIST) {
        detail.artist.takeIf { it.isNotBlank() && !it.equals("YouTube Music", ignoreCase = true) }
            ?: detail.tracks.map { it.artist }.filter { it.isNotBlank() && !it.equals(uiText("Unknown artist", "Artista desconocido"), true) }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: detail.artist.ifBlank { "YouTube Music" }
    } else {
        detail.artist.ifBlank {
            detail.tracks.firstOrNull { it.artist.isNotBlank() }?.artist.orEmpty()
        }
    }
}

/** Release type for the hero info line: "EP", uiText("Single", "Individual"), uiText("Album", "Álbum") or "Playlist". */
internal fun collectionKindLabel(detail: BrowseDetail): String {
    if (detail.kind == CatalogKind.PLAYLIST) return "Playlist"
    val clean = detail.subtitle.trim()
    val visibilityOnly = clean.equals("unlisted", true) || clean.equals("public", true) || clean.equals("private", true)
    return when {
        clean.isNotBlank() && !visibilityOnly -> clean
        detail.kind == CatalogKind.ALBUM -> uiText("Album", "Álbum")
        else -> "Playlist"
    }
}

/** Best Mix sort state shared by the hero button and the chrome's overflow menu. */
internal class BestMixLauncher(
    private val start: (BestMixLauncher) -> Unit,
    val undownloadedCount: Int,
    val estimatedMb: Int,
    private val needsPrompt: Boolean,
    private val job: State<BestMixJob?>,
    private val key: String,
) {
    // Derived from the launcher-owned run, so leaving and re-entering the screen keeps the state.
    val isSorting: Boolean get() = job.value?.key == key
    val statusText: String get() = if (isSorting) job.value?.status.orEmpty() else ""
    var showPrompt by mutableStateOf(false)

    fun trigger() {
        if (isSorting) { job.value?.cancel?.invoke(); return }
        if (job.value != null) return
        if (needsPrompt) showPrompt = true else run()
    }

    fun run() {
        if (job.value != null) return
        start(this)
    }
}

@Composable
internal fun rememberBestMixLauncher(
    detail: BrowseDetail,
    downloadedTrackIds: Set<String>,
    onPlayAll: (List<Track>, String) -> Unit,
    onPlayBestMix: ((List<Track>, String, (String) -> Unit, () -> Unit) -> Unit)?,
): BestMixLauncher {
    val undownloaded = remember(detail.tracks, downloadedTrackIds) { detail.tracks.filter { it.id !in downloadedTrackIds } }
    // Unknown durations count as 3.5 minutes; 20 KB/s approximates the cached stream bitrate.
    val estimatedMb = remember(undownloaded) {
        val ms = undownloaded.sumOf { if (it.durationMs > 0) it.durationMs else 210_000L }
        (ms / 1000.0 * 20.0 / 1024.0).roundToInt().coerceAtLeast(1)
    }
    val playAll by rememberUpdatedState(onPlayAll)
    val playBestMix by rememberUpdatedState(onPlayBestMix)
    val job = rememberUpdatedState(LocalBestMixJob.current)
    return remember(detail, undownloaded.size, estimatedMb) {
        BestMixLauncher(
            start = { launcher ->
                val external = playBestMix
                if (external != null) {
                    external(detail.tracks, detail.title, {}, {})
                } else {
                    // No Best Mix host here: play in order rather than sort on half the evidence.
                    playAll(detail.tracks, detail.title)
                }
            },
            undownloadedCount = undownloaded.size,
            estimatedMb = estimatedMb,
            needsPrompt = undownloaded.isNotEmpty(),
            job = job,
            key = BestMixJob.collectionKey(detail.title),
        )
    }
}

/** Shares through the app handler when present, else a plain text intent. */
internal fun shareCollection(context: Context, detail: BrowseDetail, onShare: ((BrowseDetail) -> Unit)?) {
    if (onShare != null) {
        onShare(detail)
        return
    }
    val artistName = collectionArtistName(detail)
    val target = if (artistName.isNotBlank()) "${detail.title} by $artistName" else detail.title
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, uiText("Listen to $target on NÜMA", "Escucha $target en NÜMA"))
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(intent, "Share ${detail.title}"))
}

@Composable
internal fun BestMixButton(isSorting: Boolean, statusText: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
            contentColor = CanopyColors.Text, disabledContainerColor = Color.Transparent,
            disabledContentColor = CanopyColors.Muted),
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .height(44.dp).desktopButtonSurface(shape),
    ) {
        if (isSorting) CircularProgressIndicator(Modifier.size(18.dp),
            color = LocalAccent.current, strokeWidth = 2.dp)
        else Icon(Icons.Rounded.AutoAwesome, null, tint = LocalAccent.current, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (isSorting) "Cancelar · " + statusText.ifBlank { uiText("Preparing mix…", "Preparando mezcla…") }
            else uiText("Best mix", "Mejor mezcla"),
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
internal fun BestMixDownloadPrompt(
    trackCount: Int,
    estimatedMb: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = LocalAccent.current,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(uiText("Best Mix Offline Analysis", "Análisis sin conexión de Mejor mezcla"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    uiText("Best Mix analyzes harmonic keys, tempo, and cue points locally to arrange your music seamlessly.", "Mejor mezcla analiza localmente las tonalidades, el ritmo y los puntos de entrada para ordenar la música con transiciones suaves."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    uiText("Downloading $trackCount song${if (trackCount == 1) "" else "s"} could take up to ~$estimatedMb MB of storage.", "Descargar $trackCount canciones puede ocupar aproximadamente $estimatedMb MB."),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = LocalAccent.current,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = LocalAccent.current),
            ) {
                Text(uiText("Download & sort", "Descargar y ordenar"), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText("Cancel", "Cancelar"), color = Color.White.copy(alpha = 0.7f))
            }
        },
        shape = RoundedCornerShape(20.dp),
    )
}
