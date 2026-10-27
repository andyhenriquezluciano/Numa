package dev.sfg.numa.mobile.playback

import android.os.Handler
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Capture Media3 state on its thread, then serialize only the latest settled request. */
@UnstableApi
internal class ServicePlaybackPersistence(
    private val handler: Handler,
    private val scope: CoroutineScope,
    private val source: () -> Player?,
    private val order: () -> List<String>,
    private val store: () -> PlaybackStateStore,
) {
    private val generation = AtomicLong()
    private val writeLock = Any()
    private val pending = Runnable { capture(false) }

    fun save(sync: Boolean) {
        generation.incrementAndGet()
        handler.removeCallbacks(pending)
        if (sync) capture(true) else handler.postDelayed(pending, 250)
    }

    private fun capture(sync: Boolean) {
        val player = source() ?: return
        val version = generation.get()
        val state = savedPlayback(player, order())
        val write = {
            synchronized(writeLock) {
                if (version == generation.get()) store().save(state)
            }
        }
        if (sync) write() else scope.launch { write() }
    }
}
