package dev.sfg.numa.mobile.playback

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import dev.sfg.numa.mobile.local.isLocalPlaylistId

private const val PLAYLIST_SOURCE_KEY = "numa.playlistId"
internal const val ALPHABETICAL_QUEUE_KEY = "numa.alphabeticalQueue"

internal fun playlistSourceExtras(id: String, alphabeticalQueue: Boolean = false) = Bundle().apply {
    putString(PLAYLIST_SOURCE_KEY, id)
    putString(LOCAL_PLAYLIST_SOURCE_KEY, id.takeIf(::isLocalPlaylistId).orEmpty())
    if (alphabeticalQueue) putBoolean(ALPHABETICAL_QUEUE_KEY, true)
}

internal val MediaMetadata.isAlphabeticalQueue: Boolean
    get() = extras?.getBoolean(ALPHABETICAL_QUEUE_KEY) == true

internal val MediaMetadata.sourcePlaylistId: String
    get() = extras?.getString(PLAYLIST_SOURCE_KEY).orEmpty()
        .ifBlank { extras?.getString(LOCAL_PLAYLIST_SOURCE_KEY).orEmpty() }
