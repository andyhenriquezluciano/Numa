package dev.sfg.numa.mobile.local

import dev.sfg.numa.mobile.lyrics.LyricsRepository
import dev.sfg.numa.mobile.model.LyricLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import dev.sfg.numa.mobile.ui.text.uiText
import java.util.concurrent.atomic.AtomicInteger

internal const val AUTOMATIC_LYRICS_VERSION = 3

internal fun automaticLyricsText(lines: List<LyricLine>): String = lines.joinToString("\n") { line ->
    val stamp = line.startMs?.coerceAtLeast(0)?.let {
        String.format(Locale.ROOT, "[%02d:%02d.%03d]", it / 60000, it / 1000 % 60, it % 1000)
    }.orEmpty()
    val body = if (line.words.isNotEmpty()) line.words.joinToString(" ") { word ->
        val ms = word.startMs.coerceAtLeast(0)
        String.format(Locale.ROOT, "<%02d:%02d.%03d>%s", ms / 60000, ms / 1000 % 60, ms % 1000,
            word.text.replace('\n', ' ').replace('\r', ' '))
    } + line.words.last().endMs?.let { ms ->
        String.format(Locale.ROOT, "<%02d:%02d.%03d>", ms / 60000, ms / 1000 % 60, ms % 1000)
    }.orEmpty() else line.text.replace('\n', ' ').replace('\r', ' ')
    stamp + body
}

class AutomaticPlaylistLyrics(
    private val scope: CoroutineScope,
    private val local: LocalLibraryRepository,
    private val lyrics: LyricsRepository,
) {
    private val mutableProgress = MutableStateFlow<String?>(null)
    val progress = mutableProgress.asStateFlow()
    private val mutableResult = MutableStateFlow("")
    val result = mutableResult.asStateFlow()
    private var resultJob: kotlinx.coroutines.Job? = null
    fun dismissResult() { resultJob?.cancel(); mutableResult.value = "" }
    private val running = java.util.concurrent.atomic.AtomicBoolean()

    fun start(playlistId: String) {
        if (!running.compareAndSet(false, true)) return
        val snapshot = local.library.value
        val songs = snapshot.playlists.firstOrNull { it.id == playlistId }?.trackIds
            .orEmpty().distinct().mapNotNull(snapshot.songsById::get)
        mutableProgress.value = uiText("Finding lyrics: 0/${songs.size}", "Buscando letras: 0/${songs.size}")
        scope.launch(Dispatchers.IO) {
            val added = AtomicInteger()
            val missing = AtomicInteger()
            val existing = AtomicInteger()
            val errors = AtomicInteger()
            val completed = AtomicInteger()
            val permits = Semaphore(2)
            try {
                coroutineScope {
                    songs.map { song -> async {
                        permits.withPermit {
                            try {
                                val track = song.toTrack()
                                if (local.lyricsFor(track).isNotEmpty() && !local.automaticLyricsNeedRefresh(track)) existing.incrementAndGet()
                                else {
                                    val found = lyrics.lyrics(track)
                                    if (found.none { it.text.isNotBlank() }) missing.incrementAndGet()
                                    else if (local.saveAutomaticLyrics(song.id, found, replaceAutomatic = true)) added.incrementAndGet()
                                    else existing.incrementAndGet()
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { errors.incrementAndGet() }
                            mutableProgress.value = uiText("Finding lyrics", "Buscando letras") + ": ${completed.incrementAndGet()}/${songs.size}"
                        }
                    } }.awaitAll()
                }
                mutableResult.value = automaticLyricsSummary(added.get(), missing.get(), existing.get(), errors.get())
                resultJob?.cancel()
                resultJob = scope.launch { kotlinx.coroutines.delay(5_000); mutableResult.value = "" }
            } finally {
                mutableProgress.value = null
                running.set(false)
            }
        }
    }
}

internal fun automaticLyricsSummary(added: Int, missing: Int, existing: Int, errors: Int): String = buildString {
    append(if (added == 1) uiText("1 lyric added.", "Se añadió 1 letra.") else uiText("$added lyrics added.", "Se añadieron $added letras."))
    if (missing > 0) append(if (missing == 1) uiText(" No lyrics found for 1 song.", " No se encontró letra para 1 canción.") else uiText(" No lyrics found for $missing songs.", " No se encontraron letras para $missing canciones."))
    if (existing > 0) append(if (existing == 1) uiText(" 1 already had lyrics.", " 1 ya tenía letra.") else uiText(" $existing already had lyrics.", " $existing ya tenían letra."))
    if (errors > 0) append(if (errors == 1) uiText(" 1 could not be checked.", " 1 no se pudo consultar.") else uiText(" $errors could not be checked.", " $errors no se pudieron consultar."))
}
