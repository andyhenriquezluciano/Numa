package dev.sfg.numa.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.artwork.coverPlaylistKey
import dev.sfg.numa.mobile.download.OfflineArtwork
import dev.sfg.numa.mobile.local.formatLocalFileUri
import dev.sfg.numa.mobile.local.isLocalPlaylistId

@Composable
internal fun rememberPlaylistBackdropSource(id: String, fallback: String): String {
    val context = LocalContext.current
    val graph = NumaGraph.from(context)
    val automatic by graph.automaticPlaylistCovers.state.collectAsState()
    val key = coverPlaylistKey(id)
    val auto = automatic.artwork[key]?.takeIf { key in automatic.enabled }
        ?.let { OfflineArtwork.usableArtworkUrl(it, 1200) }
        .orEmpty()
    if (auto.isNotBlank()) return auto
    if (isLocalPlaylistId(id)) {
        val local by graph.localLibrary.library.collectAsState()
        return local.playlists.firstOrNull { it.id == id }?.artworkPath?.let(::formatLocalFileUri) ?: fallback
    }
    val covers by graph.playlistCovers.covers.collectAsState()
    return playlistBackdropSource(id, graph.playlistCovers.cover(id, covers), fallback,
        "android.resource://${context.packageName}/${dev.sfg.numa.connect.R.drawable.playlist_liked_backdrop}")
}

internal fun playlistBackdropSource(id: String, custom: String?, fallback: String, likedDefault: String): String =
    custom?.takeIf { it.isNotBlank() }
        ?: if (coverPlaylistKey(id) == "FEmusic_liked_videos") likedDefault else fallback
