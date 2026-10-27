package dev.sfg.numa.mobile.lyrics.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull

/** A null request revokes preparation when the current song, connection or battery needs priority. */
internal suspend fun <T : Any> prepareNextTranslation(requests: Flow<T?>, prepare: suspend (T) -> Unit) {
    requests.distinctUntilChanged().collectLatest { next ->
        if (next == null) return@collectLatest
        delay(500)
        try { withTimeoutOrNull(60_000L) { prepare(next) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A failed preparation never interrupts the current song. */ }
    }
}
