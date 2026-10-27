package dev.sfg.numa.mobile.app

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

internal suspend fun <T, A : Any> prepareBestMixTracks(
    tracks: List<T>,
    fetch: suspend (T) -> A?,
    analyze: suspend (T, A) -> Unit,
    progress: (Int) -> Unit,
) {
    val done = AtomicInteger(0)
    val slots = Semaphore(2)
    withTimeoutOrNull(120_000L) {
        coroutineScope {
            tracks.map { track ->
                async {
                    slots.withPermit {
                        withTimeoutOrNull(20_000L) { fetch(track) }?.let { analyze(track, it) }
                        progress(done.incrementAndGet())
                    }
                }
            }.awaitAll()
        }
    }
}
