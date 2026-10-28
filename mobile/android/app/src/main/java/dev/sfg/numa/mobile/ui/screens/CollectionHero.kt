/*
 * Copyright (C) 2026 SFG545
 * Copyright (C) 2026 Convx Project contributors
 *
 * This file is part of Numa.
 *
 * Layout adapted from the album header in AlbumScreen
 * (app/src/main/kotlin/com/convx/music/ui/screens/AlbumScreen.kt) and the playlist
 * header in OnlinePlaylistScreen
 * (app/src/main/kotlin/com/convx/music/ui/screens/playlist/OnlinePlaylistScreen.kt) in
 * Convx v1.5.2, https://github.com/cosmictaserdev-creator/Convx, licensed under the
 * GNU General Public License version 3. It is combined with Numa under section 13
 * of the GNU GPL v3 and GNU AGPL v3.
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

package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.glass.glassArtworkSource



import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.model.LocalQobuzAlbumQuality
import dev.sfg.numa.mobile.model.QobuzAlbumQuality
import dev.sfg.numa.mobile.ui.components.QobuzAlbumQualityChip
import dev.sfg.numa.mobile.model.BrowseDetail
import dev.sfg.numa.mobile.model.CatalogKind
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.components.AnimatedArtworkVideo
import dev.sfg.numa.mobile.ui.components.ArtworkPalette
import dev.sfg.numa.mobile.ui.components.ArtworkTile
import dev.sfg.numa.mobile.ui.components.CollectionTopBar
import dev.sfg.numa.mobile.ui.components.DetailHeroFrame
import dev.sfg.numa.mobile.ui.components.ExplicitBadge
import dev.sfg.numa.mobile.ui.components.HeroAbout
import dev.sfg.numa.mobile.ui.components.HeroCircleButton
import dev.sfg.numa.mobile.ui.components.HeroGutter
import dev.sfg.numa.mobile.ui.components.HeroPlayRow
import dev.sfg.numa.mobile.ui.components.collectionDownloadAction
import dev.sfg.numa.mobile.ui.components.heroInfoLine
import dev.sfg.numa.mobile.ui.components.rememberArtworkPalette
import dev.sfg.numa.mobile.ui.foldable.isFoldableOrWideLayout
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import dev.sfg.numa.mobile.ui.theme.legibleOnDarkChrome

private fun BrowseDetail.supportsBestMix() =
    (kind == CatalogKind.PLAYLIST || kind == CatalogKind.ALBUM) && tracks.size > 1

/**
 * Album and playlist hero: faded full-bleed cover on phones (a shadowed card on wide
 * layouts), centred title, artist chip, info line, then shuffle / play / save.
 */
@Composable
internal fun CollectionHero(
    detail: BrowseDetail,
    palette: ArtworkPalette,
    shuffleAvailable: Boolean,
    onPlayAll: (List<Track>, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    onSave: (BrowseDetail) -> Unit,
    bestMix: BestMixLauncher,
    isSaved: Boolean = false,
    animatedArtworkUrl: String = "",
    artistPortraitUrl: String = "",
    onOpenArtist: (() -> Unit)? = null,
    smartCrossfadeEnabled: Boolean = false,
    playback: dev.sfg.numa.mobile.model.PlaybackSnapshot = dev.sfg.numa.mobile.model.PlaybackSnapshot(),
    onTogglePlayback: (() -> Unit)? = null,
) {
    val isAlbum = detail.kind == CatalogKind.ALBUM
    val trackIds = remember(detail.tracks) { detail.tracks.map { it.id }.toSet() }
    val ownsPlayback = dev.sfg.numa.mobile.playback.collectionOwnsPlayback(detail.id, trackIds, playback)
    val automatic by dev.sfg.numa.mobile.NumaGraph.from(LocalContext.current).automaticPlaylistCovers.state.collectAsState()
    val coverAnimation = animatedArtworkUrl.takeUnless { dev.sfg.numa.mobile.artwork.coverPlaylistKey(detail.id) in automatic.enabled }.orEmpty()
    val albumAccent = remember(palette.accent) { palette.accent.legibleOnDarkChrome() }
    // The artist's name takes a colour from their own photograph, so it reads as theirs.
    val artistPalette = rememberArtworkPalette(artistPortraitUrl)
    val artistAccent = remember(artistPalette.accent, artistPortraitUrl, albumAccent) {
        if (artistPortraitUrl.isBlank()) albumAccent else artistPalette.accent.legibleOnDarkChrome()
    }
    val artistName = remember(detail) { collectionArtistName(detail) }
    val infoLine = remember(detail) {
        val kind = collectionKindLabel(detail)
        // Some release types already carry the year ("Single • 2021").
        val year = if (kind.contains(detail.year)) "" else detail.year
        heroInfoLine(kind, year, detail.tracks.size, detail.tracks.sumOf { it.durationMs })
    }

    val body: @Composable ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = HeroGutter),
        ) {
            Text(
                text = if (detail.kind == CatalogKind.PLAYLIST) dev.sfg.numa.mobile.ui.text.playlistDisplayTitle(detail.id, detail.title) else detail.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = CanopyColors.Text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (detail.tracks.any { it.explicit }) {
                Spacer(Modifier.width(8.dp))
                ExplicitBadge()
            }
        }
        if (artistName.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ArtistChip(artistName, artistPortraitUrl, artistAccent, onOpenArtist)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = infoLine,
            style = MaterialTheme.typography.bodyMedium,
            color = CanopyColors.Text.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = HeroGutter),
        )
        // Only while MAX is on, as on desktop; the lookup is cached per album.
        val loadAlbumQuality = LocalQobuzAlbumQuality.current
        val albumQuality by produceState<QobuzAlbumQuality?>(null, detail.id, detail.tracks.size, loadAlbumQuality) {
            value = if (isAlbum && detail.tracks.isNotEmpty()) loadAlbumQuality?.invoke(detail) else null
        }
        albumQuality?.let {
            Spacer(Modifier.height(8.dp))
            QobuzAlbumQualityChip(it)
        }

        Spacer(Modifier.height(24.dp))
        HeroPlayRow(
            onPlay = { if (ownsPlayback && onTogglePlayback != null) onTogglePlayback() else onPlayAll(detail.tracks, detail.title) },
            playing = ownsPlayback && playback.isPlaying,
            playEnabled = detail.tracks.isNotEmpty(),
            // Albums keep their cover's colour on the disc; playlists stay neutral.
            playFill = if (isAlbum) albumAccent else Color.White,
            playIconTint = palette.deep,
            leading = {
                if (!dev.sfg.numa.mobile.local.isLocalPlaylistId(detail.id)) HeroCircleButton(
                    onClick = { onShuffle(detail.tracks, detail.title) },
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = uiText("Shuffle", "Aleatorio"),
                    enabled = detail.tracks.isNotEmpty() && shuffleAvailable,
                )
            },
            trailing = {
                // A playlist on this phone is already in the library; there is nothing to add.
                if (!dev.sfg.numa.mobile.local.isLocalPlaylistId(detail.id)) {
                    HeroCircleButton(
                        onClick = { onSave(detail) },
                        icon = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = if (isSaved) uiText("Saved to library", "Guardado en la biblioteca") else uiText("Add to library", "Añadir a la biblioteca"),
                        tint = if (isSaved) CanopyColors.Accent else CanopyColors.Text,
                    )
                }
            },
        )

        if (detail.supportsBestMix()) {
            Spacer(Modifier.height(12.dp))
            BestMixButton(isSorting = bestMix.isSorting, statusText = bestMix.statusText, onClick = bestMix::trigger)
        }

        if (isAlbum && detail.description.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            HeroAbout(
                title = if (isAlbum) uiText("About this album", "Acerca de este álbum") else uiText("About this playlist", "Acerca de esta lista"),
                text = detail.description,
            )
        }
    }

    if (isFoldableOrWideLayout()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A full-bleed square would fill the whole fold, so wide layouts get a card.
            Box(
                modifier = Modifier
                    .size(minOf(240.dp, androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.55f))
                    .glassArtworkSource()
                    .shadow(28.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.7f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E)),
            ) {
                if (isAlbum) ArtworkTile(detail.artworkUrl, uiText("Artwork for ${detail.title}", "Carátula de ${detail.title}"), Modifier.fillMaxSize(), radius = 16)
                else dev.sfg.numa.mobile.ui.components.PlaylistArtwork(detail.id, detail.title, detail.artworkUrl, Modifier.fillMaxSize(), fullView = true)
                if (coverAnimation.isNotBlank()) {
                    AnimatedArtworkVideo(url = coverAnimation, active = true, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { body() }
        }
    } else {
        DetailHeroFrame(
            artworkUrl = detail.artworkUrl,
            description = uiText("Artwork for ${detail.title}", "Carátula de ${detail.title}"),
            playlistId = if (isAlbum) "" else detail.id,
            animatedArtworkUrl = coverAnimation,
            content = body,
        )
    }

    if (bestMix.showPrompt) {
        BestMixDownloadPrompt(
            trackCount = bestMix.undownloadedCount,
            estimatedMb = bestMix.estimatedMb,
            onConfirm = {
                bestMix.showPrompt = false
                bestMix.run()
            },
            onDismiss = { bestMix.showPrompt = false },
        )
    }
}

/** Avatar plus name; opens the artist when the album names one. */
@Composable
private fun ArtistChip(name: String, portraitUrl: String, accent: Color, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)

            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (portraitUrl.isNotBlank()) {
            ArtworkTile(portraitUrl, name, Modifier.size(28.dp).clip(CircleShape), radius = 14)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Floating chrome for album and playlist pages, drawn over the list. */
@Composable
internal fun CollectionChrome(
    detail: BrowseDetail,
    scrimProgress: State<Float>,
    onBack: () -> Unit,
    onSave: (BrowseDetail) -> Unit,
    isSaved: Boolean,
    bestMix: BestMixLauncher,
    smartCrossfadeEnabled: Boolean,
    downloadedTrackIds: Set<String>,
    onDownloadTracks: ((List<Track>) -> Unit)?,
    onRemoveDownloadTracks: ((List<Track>) -> Unit)?,
    onShare: ((BrowseDetail) -> Unit)?,
    isSearching: Boolean,
    searchQuery: String,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    localEdit: LocalPlaylistEdit? = null,
    onDeletePlaylist: ((String) -> Unit)? = null,
    palette: ArtworkPalette? = null,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val graph = dev.sfg.numa.mobile.NumaGraph.from(context)
    val covers by graph.playlistCovers.covers.collectAsState()
    val automatic by graph.automaticPlaylistCovers.state.collectAsState()
    val automaticEnabled = dev.sfg.numa.mobile.artwork.coverPlaylistKey(detail.id) in automatic.enabled
    val automaticLabel = (if (automaticEnabled) "✓ " else "") + uiText("Automatic cover", "Carátula automática")
    val lyricsProgress by graph.automaticPlaylistLyrics.progress.collectAsState()
    val lyricsResult by graph.automaticPlaylistLyrics.result.collectAsState()
    val chooseCover = dev.sfg.numa.mobile.ui.components.rememberPlaylistCoverPicker { uri ->
        graph.automaticPlaylistCovers.setEnabled(detail.id, false)
        graph.playlistCovers.set(detail.id, uri)
    }
    var confirmingCloudDelete by remember { mutableStateOf(false) }
    val noun = if (detail.kind == CatalogKind.ALBUM) uiText("album", "álbum") else uiText("playlist", "lista")
    val candidateArt = detail.tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl
    CollectionTopBar(
        onBack = onBack,
        onShare = { shareCollection(context, detail, onShare) },
        shareVisible = detail.kind != CatalogKind.PLAYLIST,
        onSave = { onSave(detail) },
        saveVisible = localEdit == null,
        palette = palette,
        extraMenuItems = localEdit?.let { local -> buildList<Pair<String, () -> Unit>> {
            add((lyricsProgress ?: uiText("Find song lyrics", "Buscar letras de canciones")) to { graph.automaticPlaylistLyrics.start(detail.id) })
            add(uiText("Cover", "Carátula") to local.onPickCover)
            add(automaticLabel to {
                graph.automaticPlaylistCovers.rememberSource(detail.id, detail.tracks)
                graph.automaticPlaylistCovers.setEnabled(detail.id, !automaticEnabled, candidateArt)
            })
            if (dev.sfg.numa.mobile.artwork.hasRemovablePlaylistCover(local.onClearCover != null, automaticEnabled)) add(uiText("Remove cover", "Eliminar carátula") to {
                graph.automaticPlaylistCovers.setEnabled(detail.id, false)
                graph.localLibrary.clearPlaylistCover(detail.id)
            })
            add(uiText("Rename", "Cambiar nombre") to { renaming = true })
            add(uiText("Delete", "Eliminar") to { confirmingDelete = true })
        } } ?: if (detail.kind == CatalogKind.PLAYLIST) buildList {
            add(uiText("Change cover", "Cambiar carátula") to chooseCover)
            add(automaticLabel to {
                graph.automaticPlaylistCovers.rememberSource(detail.id, detail.tracks)
                graph.automaticPlaylistCovers.setEnabled(detail.id, !automaticEnabled, candidateArt)
            })
            if (dev.sfg.numa.mobile.artwork.hasRemovablePlaylistCover(!graph.playlistCovers.cover(detail.id, covers).isNullOrBlank(), automaticEnabled))
                add(uiText("Remove cover", "Eliminar carátula") to {
                    graph.automaticPlaylistCovers.setEnabled(detail.id, false)
                    graph.playlistCovers.clear(detail.id)
                })
            if (isSaved && detail.id.removePrefix("VL").startsWith("PL") && onDeletePlaylist != null)
                add(uiText("Delete playlist", "Eliminar lista") to { confirmingCloudDelete = true })
        } else emptyList(),
        isSaved = isSaved,
        scrimProgress = scrimProgress,
        // Inline About covers the description, so the menu entry is not repeated.
        onBestMix = null,
        onSearch = onSearch,
        isSearching = isSearching,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onCloseSearch = onCloseSearch,
        searchPlaceholder = uiText("Find in $noun", "Buscar en $noun"),
        aboutLabel = uiText("About this $noun", "Acerca de $noun"),
        onDownload = if (localEdit == null) collectionDownloadAction(detail.tracks, downloadedTrackIds, onDownloadTracks, onRemoveDownloadTracks) else null,
        isDownloaded = detail.tracks.isNotEmpty() && detail.tracks.all { it.id in downloadedTrackIds },
    )
    dev.sfg.numa.mobile.ui.components.WarningBanner(
        message = lyricsResult, onDismiss = graph.automaticPlaylistLyrics::dismissResult, isSuccess = true,
        modifier = Modifier.statusBarsPadding().padding(top = 76.dp),
    )
    LocalPlaylistDialogs(detail.title, localEdit, renaming, confirmingDelete,
        onRenameDismiss = { renaming = false }, onDeleteDismiss = { confirmingDelete = false })
    if (confirmingCloudDelete) dev.sfg.numa.mobile.ui.glass.GlassAlertDialog(
        onDismissRequest = { confirmingCloudDelete = false },
        title = { Text(uiText("Delete playlist?", "¿Eliminar lista?")) },
        text = { Text(detail.title) },
        confirmButton = { dev.sfg.numa.mobile.ui.components.GlassConfirmButton(uiText("Delete", "Eliminar")) {
            confirmingCloudDelete = false; onDeletePlaylist?.invoke(detail.id)
        } },
        dismissButton = { dev.sfg.numa.mobile.ui.components.GlassConfirmButton(uiText("Cancel", "Cancelar")) { confirmingCloudDelete = false } },
    )
}
