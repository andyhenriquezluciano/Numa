package dev.sfg.numa.mobile.playback

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.sfg.numa.mobile.NumaGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal const val LOCAL_PLAYLIST_SOURCE_KEY = "numa.localPlaylistId"

/** Clearing a deleted source also stops the current item and discards its saved queue. */
@UnstableApi
internal fun observeLocalPlaylistPlayback(graph: NumaGraph, scope: CoroutineScope,
    player: () -> Player?, clear: () -> Unit) {
    scope.launch(Dispatchers.Main.immediate) {
        graph.localLibrary.library.collect { library ->
            val active = player() ?: return@collect
            if (active.mediaItemCount == 0) return@collect
            val metadata = active.playlistMetadata
            val source = metadata.extras?.getString(LOCAL_PLAYLIST_SOURCE_KEY).orEmpty()
            if (source.isNotBlank()) {
                if (library.playlists.none { it.id == source }) clear()
            } else {
                // Migrate saved queues only when both their title and current song identify one list.
                val title = metadata.title?.toString().orEmpty().removeSuffix(" • Best Mix")
                val current = active.currentMediaItem?.mediaId
                val match = library.playlists.filter { it.title == title && current in it.trackIds }.singleOrNull()
                if (match != null) active.setPlaylistMetadata(metadata.buildUpon().setExtras(
                    Bundle(metadata.extras ?: Bundle()).apply { putString(LOCAL_PLAYLIST_SOURCE_KEY, match.id) }
                ).build())
            }
        }
    }
}
