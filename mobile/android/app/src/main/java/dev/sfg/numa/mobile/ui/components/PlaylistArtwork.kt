/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.request.crossfade
import dev.sfg.numa.mobile.local.isLocalPlaylistId

@Composable
internal fun PlaylistArtwork(id: String, title: String, url: String, modifier: Modifier = Modifier, fullView: Boolean = false) {
    val local = isLocalPlaylistId(id)
    val context = androidx.compose.ui.platform.LocalContext.current
    val graph = runCatching { dev.sfg.numa.mobile.NumaGraph.from(context) }.getOrNull()
    val localCover: String? = if (local && graph != null) {
        val snapshot by graph.localLibrary.library.collectAsState()
        snapshot.playlists.firstOrNull { it.id == id }?.artworkPath
            ?.let { dev.sfg.numa.mobile.local.formatLocalFileUri(it) }
    } else null
    val customCover: String? = if (!local && graph != null) {
        val covers by graph.playlistCovers.covers.collectAsState()
        graph.playlistCovers.cover(id, covers)
    } else null
    val pixels = if (fullView) 1200 else 600
    val automaticCover = if (graph != null) {
        val automatic by graph.automaticPlaylistCovers.state.collectAsState()
        val key = dev.sfg.numa.mobile.artwork.coverPlaylistKey(id)
        automatic.artwork[key]?.takeIf { key in automatic.enabled }
            ?.let { dev.sfg.numa.mobile.download.OfflineArtwork.usableArtworkUrl(it, pixels) }
            .orEmpty()
    } else ""
    val artwork = automaticCover.ifBlank { customCover ?: localCover ?: url }
    val identity = id.removePrefix("VL")
    val liked = identity == "LM" || identity == "FEmusic_liked_videos"
    val episodes = identity == "SE"
    val showArtwork = automaticCover.isNotBlank() || !customCover.isNullOrBlank() || artwork.isNotBlank() && (local || !isGeneratedPlaylistPlaceholder(artwork)) && !liked && !episodes
    val shape = if (fullView) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp)
    var displayedArtwork by remember(id) { mutableStateOf<Painter?>(null) }
    if (identity in setOf("offline_downloads", "downloads", "local_downloads")) {
        NumaMark(modifier.clip(shape).background(Color(0xFF7474FF)), title)
        return
    }
    if (showArtwork) {
        val source = dev.sfg.numa.mobile.download.OfflineArtwork.usableArtworkUrl(artwork, pixels)
        val offlineSource = rememberOfflinePlaylistArtwork(source)
        val request = coil3.request.ImageRequest.Builder(context).data(offlineSource).crossfade(150).apply {
            if (automaticCover.isNotBlank()) {
                decoderFactory(coil3.decode.BitmapFactoryDecoder.Factory())
                memoryCacheKey("playlist-still:$fullView:$offlineSource")
            }
        }.build()
        Box(modifier.clip(shape).background(Color(0xFF14171A)), contentAlignment = Alignment.Center) {
            DefaultPlaylistArtwork(local, liked, episodes, title, Modifier.fillMaxSize())
            coil3.compose.AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = displayedArtwork,
                error = displayedArtwork,
                onSuccess = { displayedArtwork = it.painter },
            )
        }
    } else {
        DefaultPlaylistArtwork(local, liked, episodes, title, modifier.clip(shape))
    }
}

@Composable
private fun DefaultPlaylistArtwork(local: Boolean, liked: Boolean, episodes: Boolean, title: String, modifier: Modifier) {
        val colors = when {
            local -> listOf(Color(0xFF35C8ED), Color(0xFF7950DC))
            episodes -> listOf(Color(0xFFE4AFC4), Color(0xFF8970FF))
            else -> listOf(Color(0xFF9464EE), Color(0xFFF54FA1))
        }
        Box(modifier.background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Icon(
                when { liked -> Icons.Rounded.Favorite; episodes -> Icons.Rounded.Podcasts; else -> Icons.AutoMirrored.Rounded.QueueMusic },
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.fillMaxSize(0.46f),
            )
        }
}

private fun isGeneratedPlaylistPlaceholder(url: String): Boolean =
    url.contains("music.youtube.com/img/", ignoreCase = true) ||
        url.contains("default_playlist", ignoreCase = true) ||
        url.contains("no_thumbnail", ignoreCase = true) ||
        url.contains("gstatic.com/youtube/media/ytm/images", ignoreCase = true) ||
        url.contains("placeholder", ignoreCase = true) ||
        url.contains("empty_playlist", ignoreCase = true) ||
        url.contains("yt_empty", ignoreCase = true) ||
        url.startsWith("data:", ignoreCase = true)

