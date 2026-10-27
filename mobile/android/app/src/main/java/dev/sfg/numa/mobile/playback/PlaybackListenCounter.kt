package dev.sfg.numa.mobile.playback

/** Counts a listen after 15 seconds of audible wall time, excluding pauses and seeks. */
internal class PlaybackListenCounter(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var trackId = ""
    private var counted = false
    private var heardMs = 0L
    private var sampledAt: Long? = null
    private var wasPlaying = false
    var elapsedMs: Long = 0
        private set
    var elapsedTrackId: String = ""
        private set
    val remainingMs: Long get() = if (counted) 0 else (15_000L - heardMs).coerceAtLeast(0)

    fun repeatTrack() {
        counted = false
        heardMs = 0
        sampledAt = null
        wasPlaying = false
    }

    fun shouldRecord(id: String?, playing: Boolean): Boolean {
        val current = id.orEmpty()
        val now = nowMs()
        elapsedTrackId = trackId
        elapsedMs = if (wasPlaying) sampledAt?.let { (now - it).coerceAtLeast(0) } ?: 0 else 0
        if (current != trackId) { trackId = current; repeatTrack() }
        sampledAt?.let { if (wasPlaying) heardMs += (now - it).coerceAtLeast(0) }
        sampledAt = now
        wasPlaying = playing && current.isNotBlank()
        if (current.isBlank() || counted || heardMs < 15_000) return false
        counted = true
        return true
    }
}
