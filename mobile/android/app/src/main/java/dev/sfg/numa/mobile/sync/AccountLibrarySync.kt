package dev.sfg.numa.mobile.sync

import android.content.Context
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.auth.NumaAccountService
import dev.sfg.numa.mobile.library.LibraryBackupCodec
import dev.sfg.numa.mobile.network.connectionFailureMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject

data class AccountSyncState(val busy: Boolean = false, val message: String = "Inicia sesión en NÜMA para guardar tu biblioteca.",
    val lastSynced: Long = 0, val failed: Boolean = false)

class AccountLibrarySync(context: Context, private val graph: NumaGraph) {
    private val account = NumaAccountService.get(context)
    private val journalResult = runCatching { SyncJournal(context) }
    private val journal get() = journalResult.getOrThrow()
    private val transport = CloudTransport(graph.http, account)
    private val covers = CloudCoverStore(context, graph, transport)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val mutable = MutableStateFlow(AccountSyncState())
    private val metricWake = Channel<Unit>(Channel.CONFLATED)
    private val manualRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    val state = mutable.asStateFlow()
    fun retry() { wake.trySend(Unit) }
    fun syncNow() {
        if (!account.state.value.signedIn || mutable.value.busy) return
        mutable.update { it.copy(busy = true, failed = false, message = "Sincronización solicitada…") }
        manualRequested.set(true)
        retry()
    }

    init {
        if (journalResult.isSuccess) start() else mutable.value = AccountSyncState(
            message = "No se pudo abrir el registro de sincronización. La biblioteca local se conserva.", failed = true)
    }

    private fun start() {
        graph.library.onManualRestore = { runCatching { journal.migrate() }; retry() }
        graph.library.onLocalListen = { track, ms, listen ->
            runCatching { journal.record(track.id, ms, listen) }.onFailure {
                mutable.value = AccountSyncState(message = "No se pudo guardar el registro de sincronización.", failed = true)
            }
            metricWake.trySend(Unit)
        }
        graph.applicationScope.launch {
            for (ignored in metricWake) {
                delay(5 * 60_000L)
                while (metricWake.tryReceive().isSuccess) { /* Include all samples in this batch. */ }
                retry()
            }
        }
        graph.applicationScope.launch {
            graph.library.library.map { it.copy(playCounts = emptyMap(), listeningTotalMs = 0,
                listeningDays = emptyList(), recentlyPlayed = emptyList(), playedTracks = emptyList()) }
                .distinctUntilChanged().collect { retry() }
        }
        graph.applicationScope.launch { graph.playlistCovers.covers.collect { retry() } }
        graph.applicationScope.launch { graph.automaticPlaylistCovers.state.collect { retry() } }
        graph.applicationScope.launch {
            graph.networkMonitor.isOnline.drop(1).collect { online ->
                if (online) manualRequested.set(true)
                retry()
            }
        }
        graph.applicationScope.launch(Dispatchers.IO) {
            account.state.map { it.userId }.distinctUntilChanged().collectLatest { owner ->
                if (owner.isNotBlank()) graph.networkMonitor.isOnline.collectLatest { online ->
                    if (online) LibraryEvents(graph.http, account).observe(owner, ::retry)
                }
            }
        }
        graph.applicationScope.launch(Dispatchers.IO) {
            account.state.map { it.userId }.distinctUntilChanged().collectLatest { owner ->
                if (owner.isBlank()) { mutable.value = AccountSyncState(); return@collectLatest }
                transport.owner = owner
                val previous = journal.owner
                try {
                    graph.library.transformSync { local ->
                        if (account.state.value.userId != owner) local else journal.bind(owner, local)
                    }
                    covers.bind(previous.takeIf { it.isNotBlank() }?.let(journal::accountDirectory), journal.accountDirectory(owner),
                        previous.isNotBlank() && previous != owner)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    mutable.value = AccountSyncState(message = "No se pudo abrir la copia de esta cuenta. Tus datos locales se conservan.", failed = true)
                    return@collectLatest
                }
                var waitMs = 0L
                var failures = 0
                var retryAt = 0L
                while (currentCoroutineContext().isActive && account.state.value.userId == owner) {
                    if (waitMs > 0) withTimeoutOrNull(waitMs) { wake.receive() }
                    val manual = manualRequested.getAndSet(false)
                    val remaining = retryAt - android.os.SystemClock.elapsedRealtime()
                    if (!manual && remaining > 0) { waitMs = remaining; continue }
                    if (!manual) delay(1_500)
                    while (wake.tryReceive().isSuccess) { /* Reconcile one snapshot for coalesced changes. */ }
                    if (!graph.networkMonitor.isOnline.value) {
                        mutable.update { it.copy(busy = false, message = "Sin conexión. Los cambios se guardarán al reconectar.", failed = false) }
                        waitMs = 15 * 60_000L; continue
                    }
                    mutable.update { it.copy(busy = true, message = "Sincronizando biblioteca…", failed = false) }
                    try {
                        synchronize(owner)
                        mutable.value = AccountSyncState(message = if (covers.incomplete)
                            "Biblioteca sincronizada. No se pudo enviar una carátula; vuelve a elegirla."
                            else "Biblioteca sincronizada con tu cuenta NÜMA.", lastSynced = System.currentTimeMillis(), failed = covers.incomplete)
                        failures = 0; retryAt = 0L
                        waitMs = 15 * 60_000L
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        mutable.update { it.copy(busy = false, failed = true, message = connectionFailureMessage(error) ?: error.message ?: "No se pudo sincronizar. Tus datos siguen en este dispositivo.") }
                        waitMs = (30_000L shl failures.coerceAtMost(5)).coerceAtMost(15 * 60_000L)
                        failures++
                        retryAt = android.os.SystemClock.elapsedRealtime() + waitMs
                    }
                }
            }
        }
    }

    private suspend fun synchronize(owner: String) {
        var counters = SyncCounters()
        var migration = 0L
        val sent = graph.library.transformSync { current -> counters = journal.counters(); migration = journal.migrationVersion(); current }
        val base = journal.base()
        val local = LibraryBackupCodec.encode(sent).apply { remove("version") }
        val sentStyles = covers.styles(base.optJSONObject("coverStyles") ?: JSONObject()); val sentAutomatic = covers.automaticJson()
        local.put("coverStyles", sentStyles).put("automaticCovers", sentAutomatic)
        val response = if (canonicalSyncJson(local) == canonicalSyncJson(base) && counters == journal.acknowledged() && !journal.migrationPending()) {
            JSONObject(transport.exchange("/sync/library").toString(Charsets.UTF_8)).getJSONObject("library")
        } else {
            val request = JSONObject().put("client", journal.client).put("seq", journal.sequence()).put("base", base)
                .put("local", local).put("counters", counters.json())
                .put("migrateLegacy", journal.migrationPending())
            JSONObject(transport.exchange("/sync/library", request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)).getJSONObject("library")
        }
        currentCoroutineContext().ensureActive()
        if (account.state.value.userId != owner) return
        val remote = LibraryBackupCodec.decode(response)
        covers.apply(sentStyles, response.optJSONObject("coverStyles") ?: JSONObject(), sentAutomatic,
            response.optJSONObject("automaticCovers") ?: JSONObject())
        currentCoroutineContext().ensureActive()
        graph.library.transformSync { current ->
            if (account.state.value.userId != owner || journal.owner != owner) current else {
                journal.counters().delta(counters).applyTo(reconcileLibrary(sent, current, remote)).also {
                    journal.complete(response, it, counters, migration)
                }
            }
        }
    }
}
