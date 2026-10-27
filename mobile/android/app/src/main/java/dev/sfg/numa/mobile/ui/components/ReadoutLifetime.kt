package dev.sfg.numa.mobile.ui.components

/** Keeps exit content alive without sharing its gesture state with a new playback. */
internal class ReadoutLifetime {
    private var trackId: String? = null
    private var generation = 0L

    fun observe(currentTrackId: String?): Long {
        if (currentTrackId != null && currentTrackId != trackId) generation++
        trackId = currentTrackId
        return generation
    }

    fun isCurrent(token: Long): Boolean = trackId != null && token == generation
}
