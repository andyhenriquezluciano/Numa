package dev.sfg.numa.mobile.playback

import dev.sfg.numa.mobile.model.PlaybackSnapshot

internal fun collectionOwnsPlayback(id: String, trackIds: Set<String>, playback: PlaybackSnapshot): Boolean {
    val currentId = playback.currentTrack?.id ?: return false
    val source = playback.sourcePlaylistId
    if (source.isNotBlank()) return source.removePrefix("VL") == id.removePrefix("VL")
    return currentId in trackIds
}
