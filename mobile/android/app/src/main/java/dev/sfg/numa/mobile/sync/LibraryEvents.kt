/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.sync

import dev.sfg.numa.mobile.auth.NumaAccountService
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Account-scoped invalidations; library data always goes through the authenticated merge API. */
internal class LibraryEvents(http: OkHttpClient, private val account: NumaAccountService) {
    private val client = http.newBuilder().pingInterval(25, TimeUnit.SECONDS).build()

    suspend fun observe(owner: String, changed: () -> Unit) {
        var backoff = 2_000L
        while (currentCoroutineContext().isActive && account.state.value.userId == owner) {
            var socket: WebSocket? = null
            try {
                val token = account.accessToken()
                if (token.isNullOrBlank()) throw java.io.IOException("Account token unavailable")
                if (account.state.value.userId != owner) return
                val ended = CompletableDeferred<Unit>()
                val connected = java.util.concurrent.atomic.AtomicBoolean(false)
                val request = Request.Builder().url(account.serviceUrl + "/sync/events")
                    .header("Sec-WebSocket-Protocol", "numa-library.1, bearer.$token").build()
                socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { connected.set(true); changed() }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (runCatching { JSONObject(text).optString("type") }.getOrNull() == "library.changed" &&
                            account.state.value.userId == owner) changed()
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { ended.complete(Unit) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { ended.complete(Unit) }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                })
                // Keep the existing connection; token checks normally reuse the cached token.
                var currentToken = token
                while (currentCoroutineContext().isActive && !ended.isCompleted) {
                    withTimeoutOrNull(30_000L) { ended.await() }
                    if (connected.get() && !ended.isCompleted) {
                        val refreshed = account.accessToken()
                        if (!refreshed.isNullOrBlank() && refreshed != currentToken) {
                            currentToken = refreshed
                            socket?.send(JSONObject().put("type", "refresh").put("token", refreshed).toString())
                        }
                    }
                }
                backoff = if (connected.get()) 2_000L else (backoff * 2).coerceAtMost(15 * 60_000L)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { backoff = (backoff * 2).coerceAtMost(15 * 60_000L) }
            finally { socket?.cancel() }
            delay(backoff)
        }
    }
}
