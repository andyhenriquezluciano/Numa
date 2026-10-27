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

package dev.sfg.numa.mobile.playback

import android.content.Context
import android.util.Log
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.social.DEFAULT_PARTY_SERVICE_URL
import dev.sfg.numa.mobile.social.ListeningPartyClient
import dev.sfg.numa.mobile.social.PartyEvent
import dev.sfg.numa.mobile.social.PartyPlaybackState
import dev.sfg.numa.mobile.social.PartyState
import dev.sfg.numa.mobile.social.PartyStateJson
import dev.sfg.numa.mobile.social.PartyStatus
import dev.sfg.numa.mobile.social.PartyTrackJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import kotlin.math.abs
import dev.sfg.numa.mobile.social.PartyClock
import dev.sfg.numa.mobile.social.PartyNotice
import dev.sfg.numa.mobile.social.partyCorrectionSpeed

/**
 * Binds a [ListeningPartyClient] to local playback.
 *
 * The host is the only device that broadcasts. Everyone else applies what arrives and, when the
 * listener touches a transport control, asks the host to make the change rather than making it
 * locally — a guest that simply pressed play would drift until the host's next tick dragged it
 * back, which reads as the button not working.
 */
class ListeningPartyManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val player: LocalPlaybackController,
    private val serviceUrl: String = DEFAULT_PARTY_SERVICE_URL,
    private var displayName: String,
) {
    fun updateDisplayName(name: String) {
        if (name.isNotBlank()) displayName = name
    }
    private val mutableState = MutableStateFlow(PartyState())
    val state: StateFlow<PartyState> = mutableState.asStateFlow()

    private val mutableMessages = MutableSharedFlow<PartyNotice>(extraBufferCapacity = 8)
    /** Human-readable notices for the UI to surface, matching the web's share-message toasts. */
    val messages: SharedFlow<PartyNotice> = mutableMessages.asSharedFlow()
    private val syncClock = PartyClock()
    private var latestState: PartyPlaybackState? = null
    private var lastProbeAt = 0L
    private var lastBroadcastAt = 0L
    private var lastCorrectionAt = 0L
    private var ownsGuestPlayback = false

    private var client: ListeningPartyClient? = null
    private var clientJobs = mutableListOf<Job>()

    /**
     * When the echo guard lapses, as epoch milliseconds.
     *
     * Applying a host snapshot moves the local player, which the host-side watcher would otherwise
     * read as a local change worth broadcasting — a loop the two devices would keep feeding. A
     * deadline rather than a flag-and-timer because it needs no cancellation and cannot be left
     * stuck on if the applying coroutine is cancelled midway.
     */
    @Volatile
    private var applyingUntil = 0L

    private val isApplying: Boolean get() = System.currentTimeMillis() < applyingUntil

    // ---------------------------------------------------------------- lifecycle

    suspend fun createParty() {
        runClient { it.createRoom(displayName) }
        mutableMessages.tryEmit(PartyNotice("Sala creada. Comparte el código ${mutableState.value.code}."))
    }

    suspend fun joinParty(code: String) {
        runClient { it.joinRoom(code, displayName) }
        mutableMessages.tryEmit(PartyNotice("Te has unido a la sala compartida."))
    }

    private suspend fun runClient(start: suspend (ListeningPartyClient) -> PartyState) {
        leaveParty(closeRoom = true)
        val created = ListeningPartyClient(http, serviceUrl, displayName)
        client = created
        // Set before binding: creating or joining a room is an HTTP round trip, and the client
        // stays idle for its whole duration. Without this the panel would show its join form again
        // for as long as the request took, which is exactly when a second tap would be tempting.
        mutableState.value = PartyState(status = PartyStatus.CONNECTING)
        bind(created)
        try {
            start(created)
        } catch (error: Throwable) {
            // Only tear down the client this call created. A second attempt may already have
            // replaced it, and disposing that one would kill a party that is connecting fine.
            if (client === created) leaveParty(closeRoom = false)
            mutableState.value = PartyState(error = error.message.orEmpty())
            throw error
        }
    }

    /** Leaves the party. A host closes the room behind it unless [closeRoom] says otherwise. */
    fun leaveParty(closeRoom: Boolean = true) {
        val active = client ?: return
        val wasHost = active.state.value.isHost
        clientJobs.forEach(Job::cancel)
        clientJobs = mutableListOf()
        active.release(closeRoom = closeRoom && wasHost)
        if (!wasHost && ownsGuestPlayback) player.clearQueue()
        ownsGuestPlayback = false
        client = null
        applyingUntil = 0L
        latestState = null
        player.setPartySpeed(1f)
        lastProbeAt = 0L
        lastBroadcastAt = 0L
        lastCorrectionAt = 0L
        syncClock.reset()
        mutableState.value = PartyState()
    }

    private fun bind(active: ListeningPartyClient) {
        clientJobs += scope.launch {
            active.state.collect { partyState ->
                // A fresh client publishes idle until its first real transition. Letting that
                // through would undo the connecting state set just above; a genuine failure
                // arrives as a thrown error instead, and is handled where the party is started.
                if (partyState.status == PartyStatus.IDLE &&
                    mutableState.value.status == PartyStatus.CONNECTING
                ) {
                    return@collect
                }
                val wasHost = mutableState.value.isHost
                val previousPeers = mutableState.value.peers.map { it.id }.toSet()
                partyState.peers.filter { it.id !in previousPeers }.forEach {
                    mutableMessages.tryEmit(PartyNotice("${it.name.ifBlank { "Un participante" }} se ha unido a la sala."))
                }
                mutableState.value = partyState
                // A device promoted mid-party has to publish immediately; the others are waiting on
                // a snapshot that would otherwise not arrive until playback next changed.
                if (!wasHost && partyState.isHost && partyState.status == PartyStatus.CONNECTED) {
                    player.setPartySpeed(1f)
                    broadcast("host-transfer")
                }
            }
        }
        clientJobs += scope.launch {
            active.events.collect { event ->
                // Read the role off the client rather than the mirror above. The two are updated by
                // separate coroutines, so on a welcome the mirror can still be a step behind, and a
                // host would briefly look like a guest and apply the snapshot it is meant to own.
                val self = active.state.value
                when (event) {
                    is PartyEvent.ClockSample -> syncClock.sample(event.clientSentAt, event.hostSentAt, System.currentTimeMillis())
                    is PartyEvent.State -> if (!self.isHost) { latestState = event.payload; applyState(event.payload) }
                    is PartyEvent.Request -> if (self.isHost) handleRequest(event)
                    is PartyEvent.HostChanged ->
                        if (event.hostId == self.participant?.id) {
                            mutableMessages.tryEmit(PartyNotice("Ahora eres el anfitrión de la sala."))
                        }
                    is PartyEvent.Closed -> {
                        mutableMessages.tryEmit(PartyNotice("La sala compartida ha finalizado."))
                        leaveParty(closeRoom = false)
                    }
                    is PartyEvent.Failed -> mutableMessages.tryEmit(PartyNotice(event.message, warning = true))
                }
            }
        }
        clientJobs += scope.launch { watchPlayback() }
        clientJobs += scope.launch { runSyncClock() }
    }

    // ---------------------------------------------------------------- host side

    /**
     * Broadcasts whenever the *shape* of playback changes.
     *
     * Position is deliberately not part of the signature: it advances every half second, and
     * sending a snapshot each time would put a hundred messages through the room per track for
     * information the drift correction already reconstructs from [PartyPlaybackState.sentAt].
     */
    private suspend fun watchPlayback() {
        player.snapshot
            .map { snapshot ->
                listOf(
                    snapshot.currentTrack?.id.orEmpty(),
                    snapshot.isPlaying.toString(),
                    snapshot.currentIndex.toString(),
                    snapshot.queue,
                )
            }
            .distinctUntilChanged()
            .collect { broadcast("playback") }
    }

    private suspend fun runSyncClock() {
        while (true) {
            delay(SYNC_INTERVAL_MS)
            val clockNow = System.currentTimeMillis()
            if (clockNow - lastBroadcastAt >= 2_500) { lastBroadcastAt = clockNow; broadcast("clock") }
            val active = client ?: continue
            if (!active.state.value.isHost && active.state.value.status == PartyStatus.CONNECTED) {
                val now = System.currentTimeMillis()
                if (now - lastProbeAt > 5_000) {
                    lastProbeAt = now
                    active.requestHost(JSONObject().put("action", "sync-clock").put("clientSentAt", now))
                }
                latestState?.let(::applyState)
            }
        }
    }

    private fun broadcast(reason: String) {
        val active = client ?: return
        if (!active.state.value.isHost || isApplying) return
        active.broadcast(PARTY_STATE, PartyStateJson.encode(snapshotState(reason)))
    }

    /**
     * Splits the local playlist into the history/current/queue triple the web models separately.
     *
     * Media3 keeps one list with a cursor, so what precedes the cursor is the history and what
     * follows is the queue. Sending the whole list as `queue` would replay everything already
     * played on every guest.
     */
    private fun snapshotState(reason: String): PartyPlaybackState {
        val snapshot = player.snapshot.value
        return PartyPlaybackState(
            reason = reason,
            track = snapshot.currentTrack,
            queue = snapshot.upcoming.take(PartyStateJson.MAX_QUEUE),
            history = snapshot.history.takeLast(PartyStateJson.MAX_HISTORY),
            mediaKind = "audio",
            isPlaying = snapshot.isPlaying,
            currentTime = player.currentPositionMs() / 1000.0,
            duration = snapshot.durationMs / 1000.0,
            sentAt = System.currentTimeMillis(),
        )
    }

    private fun handleRequest(event: PartyEvent.Request) {
        if (!dev.sfg.numa.mobile.social.guestRoomRequestAllowed(event.action)) return
        client?.broadcast(PARTY_STATE, PartyStateJson.encode(snapshotState("sync-clock"))
            .put("clockProbe", JSONObject().put("participantId", event.from)
                .put("clientSentAt", event.payload.optLong("clientSentAt"))))
    }

    // ---------------------------------------------------------------- guest side

    /**
     * Applies a host snapshot to the local player.
     *
     * The elapsed time since the host sent it is added back so a guest lands where the host is
     * *now* rather than where it was when the packet left, and the correction is only applied to a
     * playing room — adding drift to a paused one would walk the position forward on every tick.
     */
    private fun applyState(state: PartyPlaybackState) {
        val track = state.track ?: return
        if (client?.state?.value?.isHost == false) ownsGuestPlayback = true
        applyingUntil = System.currentTimeMillis() + ECHO_GUARD_MS

        val targetMs = syncClock.positionMs(state, System.currentTimeMillis())
        val snapshot = player.snapshot.value

        if (snapshot.currentTrack?.id != track.id) {
            player.setPartySpeed(1f)
            val queued = snapshot.queue.indexOfFirst { it.id == track.id }
            if (queued >= 0) {
                player.syncQueuedTrack(queued, targetMs, state.isPlaying)
                player.replaceUpcoming(state.queue)
                return
            }
            // Rebuilt as history + current + queue so the guest's "previous" walks back through the
            // same tracks the host's does, rather than dead-ending at the song it joined on.
            player.replaceQueue(
                tracks = state.history + track + state.queue,
                startIndex = state.history.size,
                positionMs = targetMs,
                play = state.isPlaying,
                contextTitle = PARTY_CONTEXT_TITLE,
            )
            return
        }

        if (!sameTrackOrder(snapshot.upcoming, state.queue)) player.replaceUpcoming(state.queue)
        if (snapshot.status in setOf(dev.sfg.numa.mobile.model.PlaybackStatus.LOADING,
                dev.sfg.numa.mobile.model.PlaybackStatus.BUFFERING)) return
        val now = System.currentTimeMillis()
        val error = targetMs - player.currentPositionMs()
        player.setPartySpeed(partyCorrectionSpeed(error, state.isPlaying))
        if (abs(error) > (if (state.isPlaying) 550 else 40) && now - lastCorrectionAt >= 1_000) {
            lastCorrectionAt = now
            player.seek(targetMs)
        }
        if (state.isPlaying && !snapshot.isPlaying) player.play()
        if (!state.isPlaying && snapshot.isPlaying) player.pause()
    }

    /**
     * Forwards a transport action to the host when this device is a guest.
     *
     * Returns true when the party consumed it, so the caller leaves the local player alone. A host,
     * or an idle device returns false and acts locally as usual.
     */
    fun interceptTransport(action: String, payload: JSONObject = JSONObject()): Boolean {
        val active = client ?: return false
        val current = active.state.value
        if (!current.isActive || current.isHost) return false
        mutableMessages.tryEmit(PartyNotice("Solo el anfitrión puede controlar la reproducción. Pídele que te ceda el control.", warning = true))
        return true
    }

    fun requestSeek(positionMs: Long): Boolean =
        interceptTransport("seek", JSONObject().put("currentTime", positionMs / 1000.0))

    fun requestPlayTrack(track: Track): Boolean =
        interceptTransport("play-track", JSONObject().put("track", PartyTrackJson.encode(track)))

    /** Hands the host seat to another participant. Ignored unless this device is the host. */
    fun transferHost(participantId: String) {
        val active = client ?: return
        if (!active.state.value.isHost) return
        active.transferHost(participantId)
    }

    private companion object {
        const val TAG = "ListeningPartyManager"
        const val PARTY_STATE = "party:state"
        const val PARTY_CONTEXT_TITLE = "Salas Compartidas"

        /** Matches the web's `PARTY_SYNC_INTERVAL_MS`, so both hosts tick at the same rate. */
        const val SYNC_INTERVAL_MS = 250L


        /** How long an applied snapshot suppresses re-broadcasting, matching the web's 250ms. */
        const val ECHO_GUARD_MS = 250L
    }
}

private fun sameTrackOrder(left: List<Track>, right: List<Track>): Boolean =
    left.size == right.size && left.indices.all { left[it].id == right[it].id }
