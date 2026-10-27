package dev.sfg.numa.mobile.download

/** Limits UI/index work without slowing down the transfer's read loop. */
internal class DownloadProgressGate(private val intervalNanos: Long = 150_000_000L) {
    private var last: Long? = null

    @Synchronized
    fun publish(now: Long, complete: Boolean): Boolean {
        val previous = last
        if (!complete && previous != null && now - previous < intervalNanos) return false
        last = now
        return true
    }
}
