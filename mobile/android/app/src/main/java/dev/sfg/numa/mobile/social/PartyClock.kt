package dev.sfg.numa.mobile.social

import kotlin.math.min

/** Estimate host clock offset using the lowest round-trip sample in this session. */
class PartyClock {
    private var bestRoundTrip = Long.MAX_VALUE
    private var offsetMs = 0L
    fun reset() { bestRoundTrip = Long.MAX_VALUE; offsetMs = 0 }
    fun sample(clientSentAt: Long, hostSentAt: Long, receivedAt: Long) {
        val rtt = receivedAt - clientSentAt
        if (clientSentAt <= 0 || hostSentAt <= 0 || rtt !in 0..10_000 || rtt > bestRoundTrip) return
        bestRoundTrip = rtt
        offsetMs = hostSentAt - (clientSentAt + rtt / 2)
    }
    fun positionMs(state: PartyPlaybackState, now: Long): Long {
        val elapsed = if (state.isPlaying) (now + offsetMs - state.sentAt).coerceIn(0, 10_000) else 0
        val target = (state.currentTime * 1000).toLong().coerceAtLeast(0) + elapsed
        return if (state.duration > 0) min(target, (state.duration * 1000).toLong()) else target
    }
}

data class PartyNotice(val message: String, val warning: Boolean = false)

fun partyCorrectionSpeed(errorMs: Long, playing: Boolean): Float =
    if (!playing || kotlin.math.abs(errorMs) <= 10 || kotlin.math.abs(errorMs) > 550) 1f
    else (1f + errorMs / 2_000f).coerceIn(0.98f, 1.02f)
