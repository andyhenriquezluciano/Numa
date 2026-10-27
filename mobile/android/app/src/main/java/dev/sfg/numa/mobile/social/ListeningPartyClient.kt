/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */
package dev.sfg.numa.mobile.social
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
class ListeningPartyClient(
    private val http: OkHttpClient,
    private val serviceUrl: String = DEFAULT_SERVICE_URL,
    private val displayName: String = "Listener",
) {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(PartyState())
    val state: StateFlow<PartyState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<PartyEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PartyEvent> = mutableEvents.asSharedFlow()
    private val baseUrl = serviceUrl.trimEnd('/')
    private var socket: WebSocket? = null
    private val peers = LinkedHashMap<String, PeerLink>()
    private var iceServers = "[]"
    private inner class PeerLink(
        val id: String,
        var name: String = "",
        var role: PartyRole = PartyRole.GUEST,
        offerer: Boolean,
    ) : PartyPeerNative.Listener {
        private val handle = PartyPeerNative.create(this, iceServers, offerer)
        var open: Boolean = false
        var connectionState: String = "connecting"
        fun start() = PartyPeerNative.start(handle)
        fun applySignal(json: String) = PartyPeerNative.signal(handle, json)
        fun send(text: String): Boolean = PartyPeerNative.sendText(handle, text)
        fun dispose() = PartyPeerNative.destroy(handle)
        override fun onSignal(json: String) {
            scope.launch { relaySignal(this@PeerLink, JSONObject(json)) }
        }
        override fun onOpen() {
            scope.launch {
                if (peers[id] !== this@PeerLink) return@launch
                open = true
                connectionState = "connected"
                publishPeers()
            }
        }
        override fun onText(text: String) {
            scope.launch { handleChannelMessage(this@PeerLink, text) }
        }
        override fun onClosed(reason: String) {
            scope.launch {
                if (peers[id] !== this@PeerLink) return@launch
                connectionState = "closed"
                closePeer(id)
                publishPeers()
            }
        }
    }
    suspend fun createRoom(name: String = displayName): PartyState {
        val body = JSONObject().put("hostName", name)
        return start(post("/rooms", body))
    }
    suspend fun joinRoom(code: String, name: String = displayName): PartyState {
        val cleaned = cleanRoomCode(code)
        if (cleaned.isBlank()) throw PartyException("Enter a room code.")
        val body = JSONObject().put("name", name)
        return start(post("/rooms/$cleaned/join", body))
    }
    private suspend fun start(data: JSONObject): PartyState {
        val roomJson = data.optJSONObject("room") ?: JSONObject()
        val participantJson = data.optJSONObject("participant") ?: JSONObject()
        val room = PartyRoom(
            id = roomJson.optString("id"),
            hostId = roomJson.optString("hostId"),
            createdAt = roomJson.optLong("createdAt"),
            expiresAt = roomJson.optLong("expiresAt"),
            closed = roomJson.optBoolean("closed"),
            maxParticipants = roomJson.optInt("maxParticipants"),
            participantCount = roomJson.optInt("participantCount"),
            socketUrl = roomJson.optString("socketUrl"),
            joinUrl = roomJson.optString("joinUrl"),
            shareUrl = roomJson.optString("shareUrl"),
        )
        val participant = PartyParticipant(
            id = participantJson.optString("id"),
            name = participantJson.optString("name"),
            role = PartyRole.of(participantJson.optString("role")),
            token = participantJson.optString("token"),
            joinedAt = participantJson.optLong("joinedAt"),
        )
        connect(room, participant)
        return mutableState.value
    }
    private suspend fun connect(room: PartyRoom, participant: PartyParticipant) = withContext(dispatcher) {
        teardown()
        mutableState.value = PartyState(
            status = PartyStatus.CONNECTING,
            room = room,
            participant = participant,
        )
        val url = socketUrl(room, participant)
        val opened = CompletableDeferred<Result<Unit>>()
        val request = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    if (socket !== webSocket) {
                        opened.complete(Result.failure(PartyException("Listening party connection was cancelled.")))
                        return@launch
                    }
                    patch { it.copy(status = PartyStatus.CONNECTED) }
                    opened.complete(Result.success(Unit))
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { if (socket === webSocket) handleSocketMessage(text) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    opened.complete(Result.failure(PartyException("Listening party connection failed.")))
                    if (socket !== webSocket) return@launch
                    Log.w(TAG, "Listening party socket failed", t)
                    teardown()
                    emit(PartyEvent.Failed("Listening party connection failed."))
                    emit(PartyEvent.Closed("disconnected"))
                    mutableState.value = PartyState()
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { if (socket === webSocket) webSocket.close(code, reason) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch {
                    opened.complete(Result.failure(PartyException("Listening party connection closed before it was ready.")))
                    if (socket !== webSocket) return@launch
                    teardown()
                    emit(PartyEvent.Closed("disconnected"))
                    mutableState.value = PartyState()
                }
            }
        }
        val webSocket = http.newWebSocket(request, listener)
        socket = webSocket
        val outcome = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) { opened.await() }
        val message = when {
            outcome == null -> "Listening party connection timed out."
            outcome.isFailure -> outcome.exceptionOrNull()?.message ?: "Listening party connection failed."
            else -> null
        }
        if (message != null) {
            if (socket !== webSocket) throw PartyException(message)
            if (socket === webSocket) {
                socket = null
                webSocket.close(1000, "client_disconnect")
            }
            patch { it.copy(status = PartyStatus.IDLE, error = message) }
            emit(PartyEvent.Failed(message))
            throw PartyException(message)
        }
    }
    private fun socketUrl(room: PartyRoom, participant: PartyParticipant): String {
        val base = room.socketUrl.ifBlank {
            "${baseUrl.replaceFirst("http", "ws")}/rooms/${room.id}/socket"
        }
        val separator = if (base.contains('?')) "&" else "?"
        return base +
            separator +
            "participantId=" + participant.id +
            "&token=" + participant.token
    }
    fun disconnect() {
        scope.launch {
            teardown()
            mutableState.value = PartyState()
        }
    }
    fun release(closeRoom: Boolean = false) {
        if (closeRoom) closeRoom()
        scope.launch {
            if (closeRoom) withTimeoutOrNull(3_000) { state.first { it.status == PartyStatus.IDLE } }
            teardown()
        }.invokeOnCompletion { scope.cancel() }
    }
    private fun teardown() {
        socket?.close(1000, "client_disconnect")
        socket = null
        peers.values.forEach(PeerLink::dispose)
        peers.clear()
    }
    private fun disconnectPeers(offline: Boolean) {
        peers.values.forEach(PeerLink::dispose)
        peers.clear()
        patch { it.copy(peers = emptyList(), status = if (offline) PartyStatus.OFFLINE else it.status) }
    }
    fun broadcast(type: String, payload: JSONObject) {
        scope.launch {
            if (type == PARTY_STATE) {
                if (!state.value.isHost) return@launch
                sendSocket(JSONObject().put("type", "party:update").put("payload", payload))
                return@launch
            }
            val message = JSONObject()
                .put("type", type)
                .put("payload", payload)
                .put("sentAt", System.currentTimeMillis())
                .toString()
            peers.values.forEach { peer ->
                if (peer.open) {
                    if (!peer.send(message)) Log.w(TAG, "Failed to send to peer ${peer.id}")
                }
            }
        }
    }
    fun requestHost(payload: JSONObject) {
        scope.launch { sendSocket(JSONObject().put("type", "party:request").put("payload", payload)) }
    }
    fun transferHost(participantId: String) {
        scope.launch {
            sendSocket(JSONObject().put("type", "party:host-transfer").put("participantId", participantId))
        }
    }
    fun closeRoom() {
        scope.launch { if (state.value.isHost) sendSocket(JSONObject().put("type", "party:close")) }
    }
    private fun sendSocket(payload: JSONObject) {
        socket?.let { runCatching { it.send(partySocketMessage(payload)) } }
    }
    private fun sendSignal(to: String, kind: String, data: Any) {
        sendSocket(
            JSONObject()
                .put("type", "signal")
                .put("to", to)
                .put("kind", kind)
                .put("data", data),
        )
    }
    private suspend fun handleSocketMessage(raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (message.optString("type")) {
            "party:welcome" -> handleWelcome(message)
            "peer:joined" -> {
                val peer = message.optJSONObject("peer") ?: return
                val id = peer.optString("id")
                ensurePeer(id, shouldInitiate(id), peer)
                publishPeers()
            }
            "peer:left" -> {
                val id = message.optString("participantId")
                if (!state.value.isHost && roomHostDeparted(id, state.value.room?.hostId.orEmpty())) {
                    emit(PartyEvent.Closed("host_left"))
                    teardown()
                    mutableState.value = PartyState()
                    return
                }
                closePeer(id)
                publishPeers()
            }
            "signal" -> handleSignal(message)
            "party:update" -> {
                patch { it.copy(error = "") }
                emitState(message.optJSONObject("payload"))
            }
            "party:request" -> {
                val payload = message.optJSONObject("payload") ?: JSONObject()
                emit(
                    PartyEvent.Request(
                        from = message.optString("from"),
                        action = payload.optString("action"),
                        payload = payload,
                    ),
                )
            }
            "party:host-changed" -> handleHostChanged(message.optString("hostId"))
            "party:closed" -> {
                emit(PartyEvent.Closed(message.optString("reason")))
                disconnectPeers(offline = false)
                mutableState.value = PartyState()
            }
            "error" -> {
                val error = message.optString("error", "Listening party failed.")
                patch { it.copy(error = error) }
                emit(PartyEvent.Failed(error))
            }
        }
    }
    private suspend fun handleWelcome(message: JSONObject) {
        iceServers = message.optJSONArray("iceServers")?.toString() ?: "[]"
        val roomJson = message.optJSONObject("room")
        patch { current ->
            val room = current.room?.let { existing ->
                if (roomJson == null) existing else existing.copy(
                    hostId = roomJson.optString("hostId", existing.hostId),
                    closed = roomJson.optBoolean("closed"),
                    participantCount = roomJson.optInt("participantCount", existing.participantCount),
                    maxParticipants = roomJson.optInt("maxParticipants", existing.maxParticipants),
                )
            }
            val role = message.optString("role").takeIf { it.isNotBlank() }
            current.copy(
                room = room,
                participant = role?.let { current.participant?.copy(role = PartyRole.of(it)) }
                    ?: current.participant,
            )
        }
        val existing = message.optJSONArray("peers")
        for (index in 0 until (existing?.length() ?: 0)) {
            val peer = existing?.optJSONObject(index) ?: continue
            val id = peer.optString("id")
            ensurePeer(id, shouldInitiate(id), peer)
        }
        publishPeers()
        message.optJSONObject("lastState")?.optJSONObject("payload")?.let { emitState(it) }
    }
    private suspend fun handleHostChanged(hostId: String) {
        if (hostId.isBlank()) return
        patch { current ->
            val participant = current.participant?.let {
                when {
                    it.id == hostId -> it.copy(role = PartyRole.HOST)
                    it.isHost -> it.copy(role = PartyRole.GUEST)
                    else -> it
                }
            }
            current.copy(participant = participant, room = current.room?.copy(hostId = hostId))
        }
        peers.values.forEach { peer ->
            peer.role = when {
                peer.id == hostId -> PartyRole.HOST
                peer.role == PartyRole.HOST -> PartyRole.GUEST
                else -> peer.role
            }
        }
        publishPeers()
        emit(PartyEvent.HostChanged(hostId))
    }
    private suspend fun emitState(payload: JSONObject?) {
        if (payload == null) return
        payload.optJSONObject("clockProbe")?.takeIf {
            it.optString("participantId") == mutableState.value.participant?.id
        }?.let { emit(PartyEvent.ClockSample(it.optLong("clientSentAt"), payload.optLong("sentAt"))) }
        emit(PartyEvent.State(PartyStateJson.decode(payload)))
    }
    private fun shouldInitiate(peerId: String): Boolean =
        (mutableState.value.participant?.id.orEmpty()) < peerId
    private fun ensurePeer(peerId: String, initiate: Boolean, info: JSONObject?): PeerLink? {
        if (peerId.isBlank() || peerId == mutableState.value.participant?.id) return null
        peers[peerId]?.let { existing ->
            info?.optString("name")?.takeIf(String::isNotBlank)?.let { existing.name = it }
            info?.optString("role")?.takeIf(String::isNotBlank)?.let { existing.role = PartyRole.of(it) }
            return existing
        }
        val link = PeerLink(
            id = peerId,
            name = info?.optString("name").orEmpty(),
            role = PartyRole.of(info?.optString("role")),
            offerer = initiate,
        )
        peers[peerId] = link
        link.start()
        return link
    }
    private fun relaySignal(peer: PeerLink, signal: JSONObject) {
        if (peers[peer.id] !== peer) return
        when (signal.optString("kind")) {
            "description" -> {
                val type = signal.optString("type")
                sendSignal(peer.id, type, JSONObject().put("type", type).put("sdp", signal.optString("sdp")))
            }
            "candidate" -> sendSignal(
                peer.id,
                "ice",
                JSONObject()
                    .put("candidate", signal.optString("candidate"))
                    .put("sdpMid", signal.optString("mid"))
                    .put("sdpMLineIndex", 0),
            )
        }
    }
    private suspend fun handleSignal(message: JSONObject) {
        val from = message.optString("from")
        val peer = ensurePeer(from, initiate = false, info = null) ?: return
        val data = message.optJSONObject("data") ?: return
        when (val kind = message.optString("kind")) {
            "offer", "answer" -> {
                val sdp = data.optString("sdp")
                if (sdp.isBlank()) return
                peer.applySignal(
                    JSONObject().put("kind", "description").put("type", kind).put("sdp", sdp).toString(),
                )
            }
            "ice" -> {
                val candidate = data.optString("candidate")
                if (candidate.isBlank()) return
                peer.applySignal(
                    JSONObject()
                        .put("kind", "candidate")
                        .put("candidate", candidate)
                        .put("mid", data.optString("sdpMid"))
                        .toString(),
                )
            }
        }
    }
    private suspend fun handleChannelMessage(peer: PeerLink, raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (message.optString("type")) {
            PARTY_STATE -> if (roomPeerCanPublishState(peer.id, state.value.room?.hostId.orEmpty(), state.value.isHost))
                emitState(message.optJSONObject("payload"))
            "party:request" -> {
                val payload = message.optJSONObject("payload") ?: JSONObject()
                emit(PartyEvent.Request(peer.id, payload.optString("action"), payload))
            }
        }
    }
    private fun closePeer(peerId: String) {
        peers.remove(peerId)?.dispose()
    }
    private fun publishPeers() {
        val snapshot = peers.values.map { peer ->
            PartyPeer(
                id = peer.id,
                name = peer.name,
                role = peer.role,
                open = peer.open,
                state = peer.connectionState,
            )
        }
        patch { it.copy(peers = snapshot) }
    }
    private fun patch(transform: (PartyState) -> PartyState) {
        mutableState.update(transform)
    }
    private suspend fun emit(event: PartyEvent) {
        mutableEvents.emit(event)
    }
    private suspend fun post(path: String, body: JSONObject): JSONObject =
        postRoomRequest(http, baseUrl, path, body)
    private companion object {
        const val TAG = "ListeningParty"
        const val DEFAULT_SERVICE_URL = "https://numa-party.peluchito281.workers.dev"
        const val PARTY_STATE = "party:state"
        const val CONNECTION_TIMEOUT_MS = 10_000L
    }
}
const val DEFAULT_PARTY_SERVICE_URL = "https://numa-party.peluchito281.workers.dev"
class PartyException(message: String) : Exception(message)
