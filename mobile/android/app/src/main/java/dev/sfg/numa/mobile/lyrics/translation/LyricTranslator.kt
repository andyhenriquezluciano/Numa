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

package dev.sfg.numa.mobile.lyrics.translation

import android.content.Context
import dev.sfg.numa.mobile.download.DownloadStore
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.model.LyricTranslationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** What the lyrics view shows; [translations] is index-aligned with [lines] when they match. */
data class LyricTranslationState(
    val status: Status = Status.OFF,
    val message: String = "",
    val sourceName: String = "",
    val progress: Float = 0f,
    val lines: List<LyricLine>? = null,
    val translations: List<String?> = emptyList(),
    val targetLanguage: String = dev.sfg.numa.mobile.settings.NumaLanguage.language.value,
) {
    enum class Status { OFF, IDLE, UNSUPPORTED, DOWNLOADING, TRANSLATING, READY, FAILED, WAITING }

    /** Translations for [current], or null when they belong to another song. */
    fun translationsFor(current: List<LyricLine>): List<String?>? =
        translations.takeIf { targetLanguage == dev.sfg.numa.mobile.settings.NumaLanguage.language.value && (lines === current || lines == current) }
}

/**
 * Translates lyrics while a viewer is on screen. Local models remain the default; an API is opt-in.
 */
class LyricTranslator(
    context: Context,
    http: OkHttpClient,
    private val settings: StateFlow<NumaSettings>,
    private val scope: CoroutineScope,
    private val conditions: StateFlow<TranslationConditions>,
    private val isOnlineProvider: () -> Boolean = { true },
    private val downloadStore: DownloadStore? = null,
) {
    val packs = TranslationPacks(context.applicationContext, http)
    private val store = TranslationStore(context.applicationContext)
    private val apiKeys = TranslationApiKeys(context.applicationContext)
    private val account = dev.sfg.numa.mobile.auth.NumaAccountService.get(context)
    private val accountRemote = AccountLyricTranslation(http, account)
    private val remote = RemoteLyricTranslation(http)
    private val localTranslator = OnDeviceLyricTranslation()
    data class ApiKeyState(val saved: Set<LyricTranslationProvider>, val message: String = "") {
        fun has(provider: LyricTranslationProvider) = provider in saved
    }
    private val mutableApiKeys = MutableStateFlow(ApiKeyState(
        LyricTranslationProvider.entries.filterTo(mutableSetOf()) { it != LyricTranslationProvider.LOCAL && it != LyricTranslationProvider.NUMA && apiKeys.has(it) },
    ))
    val apiKeyState: StateFlow<ApiKeyState> = mutableApiKeys.asStateFlow()
    private val generation = AtomicInteger()
    private data class Viewer(val lines: List<LyricLine>, val count: Int, val songId: String)
    private val viewers = MutableStateFlow<Viewer?>(null)
    private val playbackLyrics = MutableStateFlow<Viewer?>(null)
    private val nextLyrics = MutableStateFlow<Viewer?>(null)
    private val foregroundBusy = MutableStateFlow(false)
    private val localWork = Mutex()
    private val mutableState = MutableStateFlow(LyricTranslationState())
    val state: StateFlow<LyricTranslationState> = mutableState.asStateFlow()

    /** Installed packs, refreshed after downloads and removals, for the settings list. */
    private val mutableInstalled = MutableStateFlow<List<TranslationPack>>(emptyList())
    val installed: StateFlow<List<TranslationPack>> = mutableInstalled.asStateFlow()

    // Bumped to restart the current song after a failure or a removed model.
    private val retries = MutableStateFlow(0)
    private data class Config(
        val enabled: Boolean, val quality: String, val provider: LyricTranslationProvider,
        val model: String, val endpoint: String, val target: String,
    )

    init {
        dev.sfg.numa.mobile.settings.NumaLanguage.wrap(context)
        scope.launch(Dispatchers.IO) { mutableInstalled.value = packs.installedPacks() }
        val wanted = combine(settings, dev.sfg.numa.mobile.settings.NumaLanguage.language) { it, target ->
            Config(it.translateLyrics && (it.lyricTranslationProvider != LyricTranslationProvider.LOCAL || TranslationNative.available),
                it.lyricTranslationQuality.key, it.lyricTranslationProvider,
                it.lyricTranslationModels[it.lyricTranslationProvider.key].orEmpty(), it.lyricTranslationEndpoint, target)
        }
            .distinctUntilChanged()
        scope.launch {
            val active = combine(viewers, playbackLyrics, settings.map { it.lyricTranslationProvider == LyricTranslationProvider.LOCAL }.distinctUntilChanged()) {
                visible, playing, local -> (visible ?: playing.takeIf { local })?.let { Triple(it.lines, it.songId, visible != null) }
            }
                .distinctUntilChanged()
            combine(active, wanted, retries, account.state.map { it.email }.distinctUntilChanged(), conditions) { shown, config, _, _, _ ->
                shown to config
            }
                // Before collectLatest joins the old run, so its native decode stops at the next token.
                .onEach { TranslationNative.takeIf { it.available }?.setGeneration(generation.incrementAndGet()) }
                .collectLatest { (viewer, config) ->
                    foregroundBusy.value = true
                    try { run(viewer?.first, config, viewer?.second.orEmpty(), viewer?.third == true) }
                    finally { foregroundBusy.value = false }
                }
        }
        scope.launch {
            val requests = combine(nextLyrics, settings, conditions, foregroundBusy, state) { next, config, limits, busy, current ->
                next.takeIf { config.translateLyrics && config.lyricTranslationProvider == LyricTranslationProvider.LOCAL &&
                    limits.allowPreparation && !busy && current.status in setOf(LyricTranslationState.Status.READY, LyricTranslationState.Status.IDLE) }
            }
            val localizedRequests = combine(requests, dev.sfg.numa.mobile.settings.NumaLanguage.language) { next, target -> next?.let { it to target } }
            prepareNextTranslation(localizedRequests) { (next, target) -> resolveLocal(next.lines, next.songId, conditions.value.allowPreparation, target = target) }
        }
    }

    fun setPlaybackLyrics(lines: List<LyricLine>?, songId: String) {
        playbackLyrics.value = lines?.takeIf { it.isNotEmpty() }?.let { Viewer(it, 1, songId) }
    }

    fun setNextLyrics(lines: List<LyricLine>?, songId: String) {
        nextLyrics.value = lines?.takeIf { it.isNotEmpty() }?.let { Viewer(it, 1, songId) }
    }

    /** Called while a lyrics view shows [lines]; pair every call with [hide]. */
    fun show(lines: List<LyricLine>, songId: String) = viewers.update { current ->
        Viewer(lines, if (current?.lines === lines && current.songId == songId) current.count + 1 else 1, songId)
    }

    fun hide(lines: List<LyricLine>, songId: String) = viewers.update { current ->
        when {
            current == null || current.lines !== lines || current.songId != songId -> current
            current.count > 1 -> current.copy(count = current.count - 1)
            else -> null
        }
    }

    fun retry() {
        retries.update { it + 1 }
    }

    fun saveApiKey(provider: LyricTranslationProvider, raw: String) {
        if (provider == LyricTranslationProvider.LOCAL || provider == LyricTranslationProvider.NUMA || raw.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val result = runCatching { apiKeys.save(provider, raw) }
            mutableApiKeys.update {
                it.copy(saved = if (result.isSuccess) it.saved + provider else it.saved,
                    message = result.exceptionOrNull()?.message.orEmpty())
            }
            if (result.isSuccess) retries.update { it + 1 }
        }
    }

    fun removeApiKey(provider: LyricTranslationProvider) {
        if (provider == LyricTranslationProvider.LOCAL || provider == LyricTranslationProvider.NUMA) return
        scope.launch(Dispatchers.IO) {
            val result = runCatching { apiKeys.remove(provider) }
            mutableApiKeys.update {
                it.copy(saved = if (result.isSuccess) it.saved - provider else it.saved,
                    message = result.exceptionOrNull()?.message.orEmpty())
            }
            if (result.isSuccess) retries.update { it + 1 }
        }
    }

    fun remove(pack: TranslationPack) {
        scope.launch {
            withContext(Dispatchers.IO) { packs.remove(pack.id) }
            mutableInstalled.value = withContext(Dispatchers.IO) { packs.installedPacks() }
        }
    }

    private suspend fun run(lines: List<LyricLine>?, config: Config, songId: String, foreground: Boolean) {
        if (!config.enabled) {
            mutableState.value = LyricTranslationState()
            return
        }
        if (lines == null) {
            if (config.provider == LyricTranslationProvider.LOCAL) prepareLocalModel(config.target)
            return
        }
        if (config.provider == LyricTranslationProvider.NUMA) {
            runAccount(lines, songId, config.target)
            return
        }
        if (config.provider != LyricTranslationProvider.LOCAL) {
            if (!isOnlineProvider()) {
                mutableState.value = LyricTranslationState(
                    status = LyricTranslationState.Status.FAILED,
                    message = "Esta fuente necesita conexión.",
                    lines = lines,
                )
                return
            }
            runRemote(lines, config)
            return
        }
        runLocal(lines, songId, foreground, config.target)
    }

    suspend fun translateForDownload(lines: List<LyricLine>, songId: String): List<String?>? {
        if (!settings.value.translateLyrics) return null
        return try { resolveLocal(lines, songId, conditions.value.allowPreparation).third }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    private suspend fun prepareLocalModel(target: String) {
        try {
            localWork.withLock {
                localTranslator.prepare(if (target == "en") "spa" else "eng", conditions.value.allowPreparation, target = target) {
                    mutableState.value = LyricTranslationState(LyricTranslationState.Status.DOWNLOADING,
                        message = "Preparando el traductor local…")
                }
            }
            mutableState.value = LyricTranslationState(LyricTranslationState.Status.IDLE, message = "Traductor local preparado")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: TranslationModelPending) {
            mutableState.value = LyricTranslationState(LyricTranslationState.Status.WAITING, message = conditions.value.waitingMessage)
        } catch (error: Exception) {
            mutableState.value = LyricTranslationState(LyricTranslationState.Status.FAILED, message = error.message.orEmpty())
        }
    }

    private suspend fun runLocal(lines: List<LyricLine>, songId: String, foreground: Boolean, target: String) {
        val empty = LyricTranslationState(
            status = LyricTranslationState.Status.TRANSLATING,
            message = if (target == "en") "Translating into English" else "Traduciendo al español",
            targetLanguage = target,
            lines = lines,
            translations = List(lines.size) { null },
        )
        mutableState.value = empty
        try {
            val (sourceName, complete, translations) = resolveLocal(lines, songId, conditions.value.allowModelDownload(foreground),
                onDownload = { mutableState.value = empty.copy(status = LyricTranslationState.Status.DOWNLOADING, message = "Preparando el modelo del idioma…") },
                onReady = { mutableState.value = empty }, requireWifi = !foreground, target = target)
            mutableState.value = empty.copy(
                status = if (complete) LyricTranslationState.Status.READY else LyricTranslationState.Status.FAILED,
                message = if (complete) "Traducido de $sourceName" else "Conéctate para descargar el modelo de traducción.",
                sourceName = sourceName,
                translations = translations,
            )
        } catch (_: TranslationModelPending) {
            mutableState.value = empty.copy(status = LyricTranslationState.Status.WAITING, message = conditions.value.waitingMessage)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = empty.copy(
                status = LyricTranslationState.Status.FAILED,
                message = error.message ?: "No se pudo traducir la letra.",
            )
        }
    }

    private suspend fun resolveLocal(
        lines: List<LyricLine>,
        songId: String,
        allowModelDownload: Boolean,
        onDownload: () -> Unit = {},
        onReady: () -> Unit = {},
        requireWifi: Boolean = true,
        target: String = dev.sfg.numa.mobile.settings.NumaLanguage.language.value,
    ): Triple<String, Boolean, List<String?>> = localWork.withLock {
        val saved = withContext(Dispatchers.IO) {
            downloadStore?.takeIf { it.loadLyrics(songId) == lines }?.loadTranslatedLyrics(songId, target)
        }
        saved?.takeIf { it.size == lines.size }?.let { return@withLock Triple("la copia descargada", true, it) }
        val texts = lines.map(::lineText)
        val plan = JSONObject(TranslationNative.plan(texts.map { it.encodeToByteArray() }.toTypedArray()).decodeToString())
        val source = plan.optString("source").ifBlank { "eng" }
        val sourceName = plan.optString("name").ifBlank { if (source == "eng") "inglés" else source }
        if (source == if (target == "en") "eng" else "spa") return@withLock Triple(sourceName, true, List(lines.size) { null })

        val pending = LinkedHashMap<String, MutableList<Int>>()
        texts.forEachIndexed { index, text ->
            if (text.isNotEmpty()) pending.getOrPut(text) { mutableListOf() } += index
        }
        val results = arrayOfNulls<String>(lines.size)
        val revision = localTranslator.revision(source, target)
        withContext(Dispatchers.IO) { store.lookup(revision, pending.keys) }.forEach { (text, translation) ->
            pending.remove(text)?.forEach { results[it] = shown(text, translation) }
        }
        if (pending.isNotEmpty()) {
            val total = pending.size
            var completed = 0
            val translated = localTranslator.translate(source, pending.keys.toList(), allowModelDownload, onDownload, onReady,
                onTranslated = { text, value ->
                    withContext(Dispatchers.IO) { store.store(revision, text, value) }
                    pending[text]?.forEach { results[it] = shown(text, value) }
                    completed++
                    if (viewers.value?.songId == songId || playbackLyrics.value?.songId == songId) {
                        mutableState.update { it.copy(status = LyricTranslationState.Status.TRANSLATING,
                            progress = completed.toFloat() / total, translations = results.toList()) }
                    }
                }, requireWifi = requireWifi, target = target)
            translated.forEach { (text, value) ->
                pending.remove(text)?.forEach { results[it] = shown(text, value) }
            }
        }
        return@withLock Triple(sourceName, pending.isEmpty(), results.toList())
    }

    private suspend fun runRemote(lines: List<LyricLine>, config: Config) {
        val texts = lines.map(::lineText)
        val base = LyricTranslationState(LyricTranslationState.Status.IDLE, lines = lines, targetLanguage = config.target,
            translations = List(lines.size) { null })
        val pending = LinkedHashMap<String, MutableList<Int>>()
        texts.forEachIndexed { index, text ->
            if (text.isNotEmpty()) pending.getOrPut(text) { mutableListOf() } += index
        }
        val results = arrayOfNulls<String>(lines.size)
        try {
            if (config.provider != LyricTranslationProvider.GOOGLE && config.model.isBlank())
                error("Enter a translation model in Settings.")
            val key = withContext(Dispatchers.IO) { apiKeys.load(config.provider) }
            if (config.provider !in listOf(LyricTranslationProvider.CUSTOM, LyricTranslationProvider.GOOGLE) && key.isBlank())
                error("Add an API key in Settings.")
            RemoteLyricTranslation.endpoint(config.provider, config.endpoint, key)
            val revision = RemoteLyricTranslation.cacheRevision(config.provider, config.model, config.endpoint, config.target)
            val cached = withContext(Dispatchers.IO) { store.lookup(revision, pending.keys) }
            cached.forEach { (text, translation) -> pending.remove(text)?.forEach { results[it] = shown(text, translation) } }
            val ready = base.copy(status = LyricTranslationState.Status.READY,
                message = "Translated with ${config.provider.label}")
            if (pending.isEmpty()) {
                mutableState.value = ready.copy(translations = results.toList())
                return
            }
            mutableState.value = base.copy(status = LyricTranslationState.Status.TRANSLATING,
                message = "Translating with ${config.provider.label}", translations = results.toList())
            // Twenty-four lines fit comfortably in a response without losing chorus alignment.
            pending.keys.toList().chunked(RemoteLyricTranslation.BATCH_SIZE).forEach { batch ->
                val output = remote.translate(config.provider, config.model, config.endpoint, key, batch, config.target)
                withContext(Dispatchers.IO) {
                    batch.zip(output).forEach { (text, translated) -> store.store(revision, text, translated) }
                }
                batch.zip(output).forEach { (text, translated) ->
                    pending.remove(text)?.forEach { results[it] = shown(text, translated) }
                }
                mutableState.value = base.copy(status = LyricTranslationState.Status.TRANSLATING,
                    message = "Translating with ${config.provider.label}", translations = results.toList())
            }
            mutableState.value = ready.copy(translations = results.toList())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = base.copy(status = LyricTranslationState.Status.FAILED,
                message = error.message ?: "Translation failed", translations = results.toList())
        }
    }

    private suspend fun runAccount(lines: List<LyricLine>, songId: String, target: String) {
        val texts = lines.map(::lineText)
        val wanted = texts.filter(String::isNotEmpty).distinct()
        val base = LyricTranslationState(LyricTranslationState.Status.TRANSLATING,
            message = "Translating with Numa Account", targetLanguage = target, lines = lines, translations = List(lines.size) { null })
        mutableState.value = base
        try {
            check(account.state.value.signedIn) { "Sign in to Numa to translate lyrics." }
            val output = if (wanted.isEmpty()) emptyList() else accountRemote.translate(songId, wanted, target)
            val translations = wanted.zip(output).toMap()
            mutableState.value = base.copy(status = LyricTranslationState.Status.READY,
                message = "Translated with Numa Account",
                translations = texts.map { text -> translations[text]?.let { shown(text, it) } })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = base.copy(status = LyricTranslationState.Status.FAILED,
                message = error.message ?: "Account translation failed")
        }
    }

    private companion object {
        fun lineText(line: LyricLine): String =
            line.text.trim().ifEmpty { line.words.joinToString(" ") { it.text.trim() }.trim() }

        // "Oh oh oh" comes back as "Oh oh oh"; showing it twice is noise.
        fun shown(text: String, translation: String): String? =
            translation.takeUnless { it.trim().equals(text.trim(), ignoreCase = true) }
    }
}
