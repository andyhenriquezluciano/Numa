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

package dev.sfg.numa.mobile

import android.content.Context
import dev.sfg.numa.mobile.model.AudioQuality
import kotlinx.coroutines.flow.stateIn
import dev.sfg.numa.mobile.artwork.ArtistImageRepository
import dev.sfg.numa.mobile.artwork.ArtworkRepository
import dev.sfg.numa.mobile.auth.NativeYouTubeAuthRepository
import dev.sfg.numa.mobile.auth.SecureYouTubeSessionStore
import dev.sfg.numa.mobile.catalog.CatalogRepository
import dev.sfg.numa.mobile.catalog.VideoVersionResolver
import dev.sfg.numa.mobile.catalog.PlaylistActions
import dev.sfg.numa.mobile.library.LibraryCache
import dev.sfg.numa.mobile.library.LibraryRepository
import dev.sfg.numa.mobile.lyrics.LyricsRepository
import dev.sfg.numa.mobile.lastfm.LastfmRepository
import dev.sfg.numa.mobile.listenbrainz.ListenBrainzRepository
import dev.sfg.numa.mobile.settings.SettingsRepository
import dev.sfg.numa.mobile.download.DownloadManager
import dev.sfg.numa.mobile.download.DownloadStore
import dev.sfg.numa.mobile.playback.YouTubeStreamResolver
import dev.sfg.numa.mobile.playback.smart.BestMixFeatureStore
import dev.sfg.numa.mobile.songlinks.SongLinksRepository
import dev.sfg.numa.mobile.youtube.YouTubeProvider
import dev.sfg.numa.mobile.youtube.providerJson
import dev.sfg.numa.mobile.youtube.text
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Explicit process graph; presentation code receives repositories, not Android
 * singletons or transport objects. The graph owns only application-lifetime work.
 */
class NumaGraph(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val providerAsset = { name: String ->
        runCatching { context.assets.open("providers/$name").use { it.readBytes() } }.getOrNull()
    }
    /** The desktop YouTube provider in QuickJS: catalog, library, lyrics and stream URLs. */
    val youtube = YouTubeProvider(http, context.cacheDir, providerAsset)
    val auth = NativeYouTubeAuthRepository(
        store = SecureYouTubeSessionStore(context),
        scope = applicationScope,
        profileLoader = { session ->
            val profile = youtube.invoke("account.profile", JSONObject().put("session", session.providerJson()))
            profile.text("name") to profile.text("avatarUrl")
        },
        switchValidator = { session ->
            youtube.invokeArray("catalog.playlists", JSONObject().put("session", session.providerJson()))
        },
    )
    val settings = SettingsRepository(context, applicationScope)
    val bestMixFeatures = BestMixFeatureStore(context)
    val networkMonitor = dev.sfg.numa.mobile.network.NetworkMonitor(context)
    val translationConditions = dev.sfg.numa.mobile.lyrics.translation.observeTranslationConditions(context, networkMonitor, applicationScope)
    val spotifyCanvas = dev.sfg.numa.mobile.spotify.SpotifyCanvasRepository(context, http, settings)
    val artwork = ArtworkRepository(http, spotifyCanvas) { settings.settings.value.artworkSourceOrder }
    private val downloadStore = DownloadStore(context) { settings.settings.value.downloadToSdCard }
    val lyrics: LyricsRepository = LyricsRepository(http, store = downloadStore,
        isOnlineProvider = { networkMonitor.isOnline.value }, sourceTrack = { track ->
            val saved = downloads.downloads.value[track.id]?.takeIf { it.isFinished }
            if (saved != null) {
                if (saved.sourceVideoId.isNotBlank()) track.copy(id = saved.sourceVideoId, durationMs = saved.track.durationMs) else track
            }
            else streams.sources.value[track.id]?.let { source ->
                track.copy(id = source.videoId, durationMs = (source.durationSeconds * 1_000).toLong().takeIf { it > 0 } ?: track.durationMs)
            } ?: track
        }) { videoId ->
        youtube.invoke("lyrics.youtube", JSONObject().put("session", auth.session().providerJson()).put("videoId", videoId))
            .text("text")
    }
    val lyricTranslation = dev.sfg.numa.mobile.lyrics.translation.LyricTranslator(
        context, http, settings.settings, applicationScope,
        conditions = translationConditions,
        isOnlineProvider = { networkMonitor.isOnline.value },
        downloadStore = downloadStore,
    )
    val downloads: DownloadManager = DownloadManager(
        context = context,
        http = http,
        scope = applicationScope,
        streams = { streams },
        artworkResolver = artwork::artwork,
        downloadAnimatedArtworkProvider = { settings.settings.value.downloadAnimatedArtwork },
        downloadQualityProvider = { settings.settings.value.downloadQuality },
        useSdCardProvider = { settings.settings.value.downloadToSdCard },
        downloadStore = downloadStore,
        lyricsResolver = { track -> lyrics.lyrics(track) },
        lyricIdentity = lyrics::requestKey,
        lyricTranslationResolver = { track, lines -> lyricTranslation.translateForDownload(lines, track.id) },
    )
    val artistImages = ArtistImageRepository(http)
    /** Shared by playback and downloads, so both reuse one URL cache and one player. */
    val streams: YouTubeStreamResolver by lazy {
        YouTubeStreamResolver(youtube, auth, ::audioQuality, { downloads })
    }
    val catalog = CatalogRepository(youtube, auth)
    val playlistActions = PlaylistActions(youtube, auth)
    val videoVersions = VideoVersionResolver(catalog)
    val library = LibraryRepository(LibraryCache(context), catalog, applicationScope)
    /** Songs and playlists kept on this phone; no account or network needed. */
    val successEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 16)
    val playlistCovers = dev.sfg.numa.mobile.artwork.PlaylistCoverRepository(context, applicationScope, ::postWarning)
    val automaticPlaylistCovers = dev.sfg.numa.mobile.artwork.AutomaticPlaylistCovers(context)
    val localLibrary = dev.sfg.numa.mobile.local.LocalLibraryRepository(context, applicationScope,
        notify = { postWarning(it) }, notifySuccess = { successEvent.tryEmit(it) })
    val automaticPlaylistLyrics = dev.sfg.numa.mobile.local.AutomaticPlaylistLyrics(
        applicationScope, localLibrary, lyrics)
    val songLinks = SongLinksRepository()
    val lastfm = LastfmRepository(context, http, applicationScope)
    val listenBrainz = ListenBrainzRepository(context, http, applicationScope)

    /**
     * The transition the playback service has planned, or null when there is none.
     *
     * Held on the graph rather than sent through the media session because both live in this
     * process: the session carries what a remote controller needs, and a planned-but-not-started
     * transition is not that. The
     * marker describes local playback.
     */
    /**
     * Clear hook for the active playback service's [StreamCache].
     * Null before the playback service starts or after it is destroyed.
     */
    @Volatile
    var onClearStreamCache: (() -> Unit)? = null



    val transitionMarker = kotlinx.coroutines.flow.MutableStateFlow<dev.sfg.numa.mobile.model.TransitionMarker?>(null)
    val warningEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 16)
    val activeBitrate = kotlinx.coroutines.flow.MutableStateFlow(0)
    val activeTrackIsQobuz = kotlinx.coroutines.flow.MutableStateFlow(false)
    val activeStreamDetail = kotlinx.coroutines.flow.MutableStateFlow(dev.sfg.numa.mobile.model.StreamDetail())
    val qobuzTiers = dev.sfg.numa.mobile.qobuz.QobuzTierMemory(context)
    val qobuz = dev.sfg.numa.mobile.qobuz.QobuzRepository(context, applicationScope)
    val qobuzResolver = dev.sfg.numa.mobile.qobuz.QobuzResolver(qobuz, http, providerAsset)

    /** The tier for the current network: cellular on metered connections, otherwise the Wi-Fi tier. */
    fun audioQuality(s: dev.sfg.numa.mobile.model.NumaSettings = settings.settings.value): AudioQuality =
        if (networkMonitor.isMetered.value) s.cellularAudioQuality else s.audioQuality

    /** Active Qobuz playback leaves adaptive mix and the equalizer off. */
    val maxActive: kotlinx.coroutines.flow.StateFlow<Boolean> by lazy {
        kotlinx.coroutines.flow.combine(settings.settings, qobuz.status, networkMonitor.isMetered) { s, _, _ ->
            audioQuality(s) == AudioQuality.MAX && qobuzResolver.isAvailable()
        }.stateIn(
            applicationScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            audioQuality() == AudioQuality.MAX && qobuzResolver.isAvailable(),
        )
    }

    /** Names where MAX audio comes from; cached streams and bytes from another source must not be reused. */
    fun streamVariant(): String {
        val quality = audioQuality()
        val qobuzEnabled = quality == AudioQuality.MAX && qobuzResolver.isAvailable()
        return audioCacheVariant(quality, qobuzEnabled, qobuz.status.value.quality.id)
    }

    val discordPresence = dev.sfg.numa.mobile.discord.DiscordPresenceCoordinator(
        context = context,
        songLinks = songLinks,
        artistImages = artistImages,
        catalog = catalog,
        scope = applicationScope,
    )

    fun postWarning(message: String) {
        warningEvent.tryEmit(message)
    }

    val updates = UpdateManager(context) { settings.settings.value.betaChannelEnabled }
    val accountSync = dev.sfg.numa.mobile.sync.AccountLibrarySync(context, this)


    init {
        applicationScope.launch { auth.restore() }
        applicationScope.launch(Dispatchers.IO) {
            val snapshot = library.library.value
            automaticPlaylistCovers.rememberSource("FEmusic_liked_videos", snapshot.likedTracks)
            snapshot.savedPlaylists.forEach { automaticPlaylistCovers.rememberSource(it.id, it.tracks) }
            downloads.refreshTrackArtwork(
                snapshot.likedTracks +
                    snapshot.savedPlaylists.flatMap { it.tracks } +
                    snapshot.savedAlbums.flatMap { it.tracks } +
                    snapshot.recentlyPlayed,
            )
            downloads.refreshTrackArtwork()
        }
        updates.checkForUpdates()
    }

    companion object {
        fun from(context: Context): NumaGraph =
            (context.applicationContext as NumaApplication).graph
    }
}
