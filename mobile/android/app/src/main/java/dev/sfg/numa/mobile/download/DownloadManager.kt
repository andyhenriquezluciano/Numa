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

package dev.sfg.numa.mobile.download

import android.content.Context
import android.util.Log
import dev.sfg.numa.mobile.artwork.TrackArtwork
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal fun DownloadItem.completedFileOrNull(): File? {
    if (status != DownloadStatus.COMPLETED || filePath.isBlank()) return null
    return File(filePath).takeIf { it.exists() && it.length() > 0L }
}

/**
 * Application-scoped download queue controller supporting concurrent background downloads,
 * cancellation, deletion, and reactive state flows.
 */
class DownloadManager(
    context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    /** Supplied lazily: the resolver also reads finished downloads back from this manager. */
    streams: () -> dev.sfg.numa.mobile.playback.YouTubeStreamResolver,
    private val artworkResolver: suspend (Track) -> TrackArtwork? = { null },
    private val downloadAnimatedArtworkProvider: () -> Boolean = { false },
    useSdCardProvider: () -> Boolean = { false },
    downloadStore: DownloadStore? = null,
    private val lyricsResolver: suspend (Track) -> List<LyricLine>? = { null },
    private val lyricIdentity: (Track) -> String = { dev.sfg.numa.mobile.lyrics.lyricCacheIdentity(it) },
    private val lyricTranslationResolver: suspend (Track, List<LyricLine>) -> List<String?>? = { _, _ -> null },
    downloadQualityProvider: () -> dev.sfg.numa.mobile.model.AudioQuality = { dev.sfg.numa.mobile.model.AudioQuality.HIGH },
) {
    private val context = context.applicationContext
    val store: DownloadStore = downloadStore ?: DownloadStore(context, useSdCard = useSdCardProvider)
    private val downloader: TrackDownloader =
        TrackDownloader(http, store, streams, downloadQualityProvider)

    private val mutableDownloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = mutableDownloads.asStateFlow()

    private val mutableDownloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = mutableDownloadedIds.asStateFlow()

    private val mutableDownloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<String>> = mutableDownloadingIds.asStateFlow()

    private val mutableTotalBytesUsed = MutableStateFlow(0L)
    val totalBytesUsedFlow: StateFlow<Long> = mutableTotalBytesUsed.asStateFlow()

    private val downloadQueue = Channel<DownloadItem>(Channel.UNLIMITED)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val stateLock = Any()
    private val batchIds = mutableSetOf<String>()
    private val mutableBatchProgress = MutableStateFlow(DownloadBatchProgress())
    val batchProgress: StateFlow<DownloadBatchProgress> = mutableBatchProgress.asStateFlow()

    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val initialized = scope.async(Dispatchers.IO) {
        val initial = store.loadAll()
        synchronized(stateLock) {
            mutableDownloads.value = initial
            updateDerivedStates(initial)
        }
        refreshTotalBytesUsed()
    }

    private fun command(work: suspend () -> Unit) {
        commands.trySend(work).getOrThrow()
    }

    init {
        scope.launch(Dispatchers.IO) {
            initialized.await()
            for (work in commands) {
                try { work() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w(TAG, "Download operation failed", error) }
            }
        }
        // Start worker coroutines for queued downloads
        repeat(MAX_CONCURRENT_DOWNLOADS) {
            scope.launch(Dispatchers.IO) {
                initialized.await()
                for (item in downloadQueue) {
                    coroutineScope {
                        val videoId = item.track.id
                        val job = launch(start = CoroutineStart.LAZY) { processDownload(item) }
                        synchronized(stateLock) {
                            if (mutableDownloads.value[videoId]?.status != DownloadStatus.QUEUED || activeJobs.containsKey(videoId)) {
                                job.cancel()
                                return@coroutineScope
                            }
                            activeJobs[videoId] = job
                        }
                        try {
                            job.start()
                            job.join()
                        } finally {
                            activeJobs.remove(videoId, job)
                        }
                    }
                }
            }
        }
    }

    /** Enqueue a track for downloading. */
    fun downloadTrack(track: Track) = command { enqueueTrack(track) }

    private fun enqueueTrack(track: Track) {
        // Already on the phone.
        if (track.id.isBlank() || track.isLocal) return
        val current = mutableDownloads.value[track.id]
        if (current?.status == DownloadStatus.COMPLETED && current.completedFileOrNull() != null) {
            Log.d(TAG, "Track ${track.id} already downloaded or queued")
            return
        }
        if (current?.isDownloading == true) {
            Log.d(TAG, "Track ${track.id} already downloaded or queued")
            return
        }

        // Android or the user may remove an external-files entry while the process is alive.
        // A COMPLETED row is only a download while its non-empty file still exists; otherwise a
        // Best Mix request sees the missing file, asks us to fetch it, and this method used to
        // reject that fetch as "already downloaded". Besides making the sort wait on a download
        // that never starts, that disagreement produced the apparently random one-song download
        // stage reported from collection Best Mix.
        if (current?.status == DownloadStatus.COMPLETED) {
            synchronized(stateLock) {
                val updated = mutableDownloads.value.toMutableMap().apply { remove(track.id) }
                mutableDownloads.value = updated
                updateDerivedStates(updated)
            }
        }

        val item = DownloadItem(
            track = track,
            status = DownloadStatus.QUEUED,
            progress = 0f,
        )
        val startService = synchronized(stateLock) {
            if (mutableDownloads.value[track.id]?.isDownloading == true) return
            val start = !mutableBatchProgress.value.active
            if (start) batchIds.clear()
            batchIds.add(track.id)
            updateItemState(item, allowNew = true)
            start
        }
        if (startService) runCatching {
            androidx.core.content.ContextCompat.startForegroundService(context,
                android.content.Intent(context, DownloadForegroundService::class.java))
        }.onFailure { Log.w(TAG, "Could not start download foreground service", it) }
        downloadQueue.trySend(item)
    }

    /** Enqueue a list of tracks for downloading (e.g. playlist or album). */
    fun downloadTracks(tracks: List<Track>) = command { tracks.forEach(::enqueueTrack) }

    fun cancelDownload(videoId: String) = command {
        if (mutableDownloads.value[videoId]?.status != DownloadStatus.COMPLETED)
            removeItems(listOf(videoId))
    }

    fun pauseDownloads() {
        val jobs = synchronized(stateLock) {
            val running = activeJobs.values.toList()
            running.forEach { it.cancel() }
            val paused = mutableDownloads.value.mapValues { (_, item) ->
                if (item.isDownloading) item.copy(status = DownloadStatus.PAUSED) else item
            }
            mutableDownloads.value = paused
            updateDerivedStates(paused)
            running
        }
        command {
            jobs.forEach { it.join() }
            mutableDownloads.value.values.filter { it.status == DownloadStatus.PAUSED }
                .forEach { store.removeTemp(it.track.id) }
        }
    }

    fun resumeDownloads() = command {
        mutableDownloads.value.values.filter { it.status == DownloadStatus.PAUSED }
            .forEach { enqueueTrack(it.track) }
    }

    fun removeDownload(videoId: String) = command { removeItems(listOf(videoId)) }

    fun removeAllDownloads() = command {
        removeItems(mutableDownloads.value.keys.toList())
        runCatching { AnimatedArtworkCache.clear(context) }
            .onFailure { Log.w(TAG, "Could not clear animated artwork cache", it) }
        refreshTotalBytesUsed()
    }

    fun removeDownloads(videoIds: List<String>) = command { removeItems(videoIds) }

    private suspend fun removeItems(videoIds: List<String>) {
        val removed = synchronized(stateLock) {
            val updated = mutableDownloads.value.toMutableMap()
            val removed = videoIds.mapNotNull { updated.remove(it) }
            mutableDownloads.value = updated
            updateDerivedStates(updated)
            removed
        }
        val jobs = videoIds.mapNotNull { activeJobs[it] }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
        store.removeAll(videoIds)
        removed.forEach { releaseUnusedAnimatedArtwork(it, mutableDownloads.value.values) }
        refreshTotalBytesUsed()
    }

    /** Check if a track is downloaded and verified on disk. */
    fun isDownloaded(videoId: String): Boolean = downloadedTrackIds.value.contains(videoId)

    /** Check if all tracks in a list are downloaded. */
    fun areTracksDownloaded(tracks: List<Track>): Boolean {
        if (tracks.isEmpty()) return false
        val downloaded = downloadedTrackIds.value
        return tracks.all { downloaded.contains(it.id) }
    }

    /** Total storage bytes consumed by completed downloads. */
    fun totalBytesUsed(): Long = store.totalBytesUsed() + AnimatedArtworkCache.bytesUsed(context)

    /** Returns the local file for a downloaded track, or null if not downloaded. */
    fun getDownloadedFile(videoId: String): File? {
        val item = mutableDownloads.value[videoId] ?: return null
        return item.completedFileOrNull()
    }

    fun artworkUri(track: Track): String {
        OfflineArtwork.localUri(context, track.id)?.let { return it }
        val saved = mutableDownloads.value[track.id]?.track?.artworkUrl.orEmpty()
        val candidate = OfflineArtwork.usableArtworkUrl(track.artworkUrl)
            .ifBlank { OfflineArtwork.usableArtworkUrl(saved) }
        if (candidate.startsWith("http") && isDownloaded(track.id)) {
            refreshTrackArtwork(listOf(track.copy(artworkUrl = candidate)))
        }
        return candidate
    }

    fun refreshTrackArtwork(tracks: List<Track> = emptyList()) {
        val byId = tracks.filter { it.id.isNotBlank() && it.artworkUrl.isNotBlank() }.associateBy { it.id }
        scope.launch(Dispatchers.IO) {
            val snapshot = mutableDownloads.value.values.filter { it.status == DownloadStatus.COMPLETED }
            for (item in snapshot) {
                val id = item.track.id
                if (OfflineArtwork.localUri(context, id) != null) continue
                val remote = OfflineArtwork.usableArtworkUrl(byId[id]?.artworkUrl.orEmpty())
                    .ifBlank { OfflineArtwork.usableArtworkUrl(item.track.artworkUrl) }
                    .ifBlank {
                        if (byId.isEmpty()) runCatching { artworkResolver(item.track)?.staticUrl.orEmpty() }.getOrDefault("")
                        else ""
                    }
                if (remote.isBlank()) continue
                val saved = runCatching {
                    OfflineArtwork.save(context, http, item.track.copy(artworkUrl = remote))
                }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: remote
                val current = mutableDownloads.value[id] ?: continue
                if (current.status == DownloadStatus.COMPLETED && current.track.artworkUrl != saved) {
                    val updated = current.copy(track = current.track.copy(artworkUrl = saved))
                    store.save(updated)
                    updateItemState(updated)
                }
            }
        }
    }

    suspend fun artworkBytes(track: Track): ByteArray? = OfflineArtwork.bytes(context, track)

    private suspend fun processDownload(queuedItem: DownloadItem) {
        val videoId = queuedItem.track.id
        val downloadingItem = synchronized(stateLock) {
            if (mutableDownloads.value[videoId]?.status != DownloadStatus.QUEUED) return
            queuedItem.copy(status = DownloadStatus.DOWNLOADING).also { updateItemState(it) }
        }

        var result = downloadingItem
        for (attempt in 0 until MAX_DOWNLOAD_ATTEMPTS) {
            val updates = DownloadProgressGate()
            result = downloader.download(result.copy(status = DownloadStatus.DOWNLOADING)) { bytesDownloaded, totalBytes, progress ->
                if (updates.publish(System.nanoTime(), progress >= 1f)) {
                    val progressItem = downloadingItem.copy(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        progress = progress,
                    )
                    updateItemState(progressItem)
                }
            }
            if (result.status == DownloadStatus.COMPLETED) break
            if (attempt < MAX_DOWNLOAD_ATTEMPTS - 1) {
                Log.w(TAG, "Retrying download $videoId after attempt ${attempt + 1}: ${result.errorMessage}")
                delay(RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }

        if (result.status == DownloadStatus.COMPLETED) {
            result = coroutineScope {
                val motion = async {
                    if (downloadAnimatedArtworkProvider()) downloadAnimatedArtwork(result) else result
                }
                val cover = async {
                    runCatching { OfflineArtwork.save(context, http, result.track) }
                        .onFailure { if (it is CancellationException) throw it }.getOrNull()
                }
                val lyrics = async { downloadLyrics(result.track) }
                val enriched = motion.await()
                val localCover = cover.await()
                lyrics.await()
                val finalCover = localCover ?: OfflineArtwork.usableArtworkUrl(enriched.track.artworkUrl)
                enriched.copy(track = enriched.track.copy(artworkUrl = finalCover))
            }
        }

        currentCoroutineContext().ensureActive()
        if (result.status == DownloadStatus.COMPLETED) {
            store.save(result)
            refreshTotalBytesUsed()
        }
        updateItemState(result)
    }

    private suspend fun downloadLyrics(track: Track) {
        val videoId = track.id
        if (videoId.isBlank()) return
        runCatching {
            val lines = lyricsResolver(track)
            if (!lines.isNullOrEmpty()) {
                val language = dev.sfg.numa.mobile.settings.NumaLanguage.language.value
                val translations = lyricTranslationResolver(track, lines).orEmpty().takeIf { language == dev.sfg.numa.mobile.settings.NumaLanguage.language.value }.orEmpty()
                store.saveLyrics(videoId, lines, translations, timingIdentity = lyricIdentity(track), translationTarget = language)
                Log.d(TAG, "Saved offline lyrics for $videoId (${lines.size} lines)")
            } else {
                Log.d(TAG, "No lyrics found for offline track $videoId")
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to download offline lyrics for $videoId", e)
        }
    }

    /** Motion-cover failures never turn a successfully downloaded song into a failed download. */
    private suspend fun downloadAnimatedArtwork(item: DownloadItem): DownloadItem {
        val original = item.track
        val resolved = if (
            original.animatedArtworkUrl.isNotBlank() ||
            original.animatedArtworkVerticalUrl.isNotBlank()
        ) {
            TrackArtwork(
                trackId = original.id,
                videoUrl = original.animatedArtworkUrl,
                videoUrlVertical = original.animatedArtworkVerticalUrl,
            )
        } else {
            try {
                artworkResolver(original)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not resolve animated artwork for ${original.id}", error)
                null
            }
        } ?: return item

        suspend fun cache(source: String): CachedAnimatedArtwork? {
            if (source.isBlank()) return null
            return try {
                AnimatedArtworkCache.download(context, http, source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not download animated artwork for ${original.id}", error)
                null
            }
        }

        val (wide, vertical) = coroutineScope {
            val wide = async { cache(resolved.videoUrl) }
            val vertical = async {
                when {
                    resolved.videoUrlVertical.isBlank() -> null
                    resolved.videoUrlVertical == resolved.videoUrl -> wide.await()
                    else -> cache(resolved.videoUrlVertical)
                }
            }
            wide.await() to vertical.await()
        }
        if (wide == null && vertical == null) return item

        return item.copy(
            track = original.copy(
                animatedArtworkUrl = wide?.playbackUrl ?: original.animatedArtworkUrl,
                animatedArtworkVerticalUrl = vertical?.playbackUrl ?: original.animatedArtworkVerticalUrl,
            ),
            cachedAnimatedArtworkUrl = wide?.playbackUrl.orEmpty(),
            cachedAnimatedArtworkVerticalUrl = vertical?.playbackUrl.orEmpty(),
            animatedArtworkBytesDownloaded = listOfNotNull(wide, vertical)
                .distinctBy(CachedAnimatedArtwork::playbackUrl)
                .sumOf(CachedAnimatedArtwork::bytes),
        )
    }

    private fun releaseUnusedAnimatedArtwork(
        removed: DownloadItem,
        remaining: Collection<DownloadItem>,
    ) {
        val retainedUrls = remaining.flatMapTo(mutableSetOf()) {
            listOf(it.cachedAnimatedArtworkUrl, it.cachedAnimatedArtworkVerticalUrl)
        }
        val releasedUrls = listOf(
            removed.cachedAnimatedArtworkUrl,
            removed.cachedAnimatedArtworkVerticalUrl,
        ).filter(String::isNotBlank).distinct().filterNot(retainedUrls::contains)
        if (releasedUrls.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            releasedUrls.forEach { url ->
                runCatching { AnimatedArtworkCache.remove(context, http, url) }
                    .onFailure { Log.w(TAG, "Could not remove cached animated artwork", it) }
            }
            refreshTotalBytesUsed()
        }
    }

    private fun refreshTotalBytesUsed() {
        runCatching { mutableTotalBytesUsed.value = totalBytesUsed() }
            .onFailure { Log.w(TAG, "Could not calculate download storage", it) }
    }

    private fun updateItemState(item: DownloadItem, allowNew: Boolean = false) {
        synchronized(stateLock) {
            val current = mutableDownloads.value[item.track.id]
            if (!allowNew && (current == null || current.status == DownloadStatus.PAUSED)) return
            val map = mutableDownloads.value.toMutableMap()
            map[item.track.id] = item
            mutableDownloads.value = map
            updateDerivedStates(map)
        }
    }

    private fun updateDerivedStates(map: Map<String, DownloadItem>) = synchronized(stateLock) {
        mutableBatchProgress.value = downloadBatchProgress(batchIds, map)
        mutableDownloadedIds.value = map.values
            .filter { it.status == DownloadStatus.COMPLETED && it.filePath.isNotBlank() }
            .map { it.track.id }
            .toSet()
        mutableDownloadingIds.value = map.values
            .filter { it.isDownloading }
            .map { it.track.id }
            .toSet()
    }

    companion object {
        private const val TAG = "DownloadManager"
        // Enough parallelism to beat per-connection throttling without a request storm.
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val MAX_DOWNLOAD_ATTEMPTS = 6
        private const val RETRY_BASE_DELAY_MS = 750L
    }
}
