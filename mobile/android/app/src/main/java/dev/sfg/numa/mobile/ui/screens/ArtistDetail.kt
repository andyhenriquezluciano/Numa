/*
 * Copyright (C) 2026 SFG545
 * Copyright (C) 2026 Convx Project contributors
 *
 * This file is part of Numa.
 *
 * Layout adapted from the artist header and sections in ArtistScreen
 * (app/src/main/kotlin/com/convx/music/ui/screens/artist/ArtistScreen.kt) in
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

import dev.sfg.numa.mobile.ui.glass.GlassContentLayer
import dev.sfg.numa.mobile.ui.glass.FloatingGlassControls
import dev.sfg.numa.mobile.ui.glass.rememberFloatingGlassScene

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import dev.sfg.numa.mobile.ui.scroll.NumaLazyColumn as LazyColumn
import dev.sfg.numa.mobile.ui.scroll.NumaLazyRow as LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.BrowseDetail
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.motion.riseIn
import dev.sfg.numa.mobile.ui.components.ArtistHero
import dev.sfg.numa.mobile.ui.components.ArtistSectionBottomSheet
import dev.sfg.numa.mobile.ui.components.CatalogCard
import dev.sfg.numa.mobile.ui.components.ChromeAction
import dev.sfg.numa.mobile.ui.components.DetailFloatingChrome
import dev.sfg.numa.mobile.ui.components.FeaturedReleaseCard
import dev.sfg.numa.mobile.ui.components.TrackRow
import dev.sfg.numa.mobile.ui.components.rememberArtworkPalette
import dev.sfg.numa.mobile.ui.components.rememberHeroScrimProgress
import dev.sfg.numa.mobile.ui.theme.CanopyColors

/** Rows shown under uiText("Top songs", "Canciones destacadas") before it is expanded. */
private const val PopularPreviewCount = 5

/** Artist page: faded portrait hero, featured release, top songs, discography rails. */
@Composable
internal fun ArtistDetailContent(
    detail: BrowseDetail,
    onBack: () -> Unit,
    onPlayAll: (List<Track>, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    onPlayTrack: (List<Track>, Int, String) -> Unit,
    onPlayNext: ((Track) -> Unit)?,
    shuffleAvailable: Boolean,
    onAdd: ((Track) -> Unit)?,
    onSave: (BrowseDetail) -> Unit,
    isSaved: Boolean,
    onOpen: (String) -> Unit,
    downloadedTrackIds: Set<String> = emptySet(),
    downloadingTrackIds: Set<String> = emptySet(),
    onDownloadTrack: ((Track) -> Unit)? = null,
    onRemoveDownloadTrack: ((String) -> Unit)? = null,
    onShareTrack: ((Track) -> Unit)? = null,
    onShareCollection: ((BrowseDetail) -> Unit)? = null,
    onFetchSectionItems: (suspend (String, String) -> List<CatalogItem>)? = null,
) {
    val palette = rememberArtworkPalette(detail.artworkUrl)
    val listState = rememberLazyListState()
    val scrimProgress = rememberHeroScrimProgress(listState)
    val floatingScene = rememberFloatingGlassScene()
    var activeSectionSheet by remember { mutableStateOf<SectionSheetState?>(null) }
    var showAllPopularTracks by remember { mutableStateOf(false) }

    activeSectionSheet?.let { sheet ->
        ArtistSectionBottomSheet(
            title = sheet.title,
            initialItems = sheet.initialItems,
            browseId = sheet.browseId,
            params = sheet.params,
            onFetchFullItems = onFetchSectionItems,
            onPlay = { track -> onPlayTrack(listOf(track), 0, sheet.title) },
            onOpen = onOpen,
            onDismiss = { activeSectionSheet = null },
        )
    }

    fun openSheet(title: String, items: List<CatalogItem>, browseId: String = "", params: String = "") {
        activeSectionSheet = SectionSheetState(title, items, browseId, params)
    }

    ArtistPageStyle(palette) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    // Portrait tint holds through the hero, then eases into the app chrome.
                    Brush.verticalGradient(
                        0f to palette.top.copy(alpha = 0.6f),
                        0.4f to palette.deep,
                        1f to CanopyColors.Chrome,
                    ),
                ),
        ) {
            GlassContentLayer(floatingScene, Modifier.fillMaxSize()) {
                ArtistAnimatedBackdrop(detail.artworkUrl, palette)
                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 128.dp)) {
                    item {
                        ArtistHero(
                            detail = detail,
                            palette = palette,
                            onPlayAll = onPlayAll,
                            onShuffle = onShuffle,
                            shuffleAvailable = shuffleAvailable,
                            onSave = onSave,
                            isSaved = isSaved,
                        )
                    }

                    val latestRelease = detail.sections.firstNotNullOfOrNull { section ->
                        section.items.firstOrNull { it is CatalogItem.Record } as? CatalogItem.Record
                    }
                    if (latestRelease != null) {
                        item { FeaturedReleaseCard(item = latestRelease, onClick = { onOpen(latestRelease.stableId) }) }
                    }

                    if (detail.tracks.isNotEmpty()) {
                        val hasMorePopular = detail.tracks.size > PopularPreviewCount
                        val displayedTracks = if (showAllPopularTracks || !hasMorePopular) detail.tracks
                            else detail.tracks.take(PopularPreviewCount)
                        item {
                            HomeSectionTitle(
                                title = uiText("Top songs", "Canciones destacadas"),
                                onClick = if (hasMorePopular) {{ showAllPopularTracks = !showAllPopularTracks }} else null,
                                actionLabel = if (showAllPopularTracks) uiText("Show less", "Mostrar menos") else null,
                            )
                        }
                        // A collection may legitimately list the same track twice, so the id alone is not
                        // a unique key and LazyColumn throws the moment the duplicate scrolls in.
                        itemsIndexed(displayedTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                            TrackRow(
                                track = track,
                                onPlay = { onPlayTrack(detail.tracks, index, detail.title) },
                                modifier = Modifier
                                    .animateItem(fadeInSpec = null)
                                    .padding(horizontal = 8.dp)
                                    .riseIn(index, cascadeOnScroll = true),
                                showDivider = false,
                                onPlayNext = onPlayNext?.let { action -> { action(track) } },
                                onAddToQueue = onAdd?.let { action -> { action(track) } },
                                onDownload = onDownloadTrack?.let { action -> { action(track) } },
                                onRemoveDownload = onRemoveDownloadTrack?.let { action -> { action(track.id) } },
                                isDownloaded = track.id in downloadedTrackIds,
                                isDownloading = track.id in downloadingTrackIds,
                                onShare = onShareTrack?.let { action -> { action(track) } },
                                onViewAlbum = if (track.albumId.isNotBlank()) {{ onOpen(track.albumId) }} else null,
                                onViewArtist = if (track.artistId.isNotBlank()) {{ onOpen(track.artistId) }} else null,
                                compact = true,
                            )
                        }
                    }

                    if (detail.sections.isNotEmpty()) {
                        detail.sections.forEach { section ->
                            // "See all" when the API can page further or the rail already overflows.
                            val canViewAll = section.browseId.isNotBlank() || section.items.size > 3
                            item {
                                HomeSectionTitle(
                                    title = section.title,
                                    onClick = if (canViewAll) {
                                        { openSheet(section.title, section.items, section.browseId, section.params) }
                                    } else null,
                                )
                            }
                            catalogRail(section.items, section.title, onPlayTrack, onOpen)
                        }
                    } else if (detail.related.isNotEmpty()) {
                        item {
                            HomeSectionTitle(
                                title = uiText("Fans also like", "A sus seguidores también les gusta"),
                                onClick = if (detail.related.size > 3) {{ openSheet(uiText("Fans also like", "A sus seguidores también les gusta"), detail.related) }} else null,
                            )
                        }
                        catalogRail(detail.related, detail.title, onPlayTrack, onOpen)
                    }
                }

            }
            FloatingGlassControls(floatingScene) {
                DetailFloatingChrome(onBack = onBack, scrimProgress = scrimProgress, palette = palette) {
                    if (onShareCollection != null) {
                        ChromeAction(Icons.Rounded.IosShare, uiText("Share artist", "Compartir artista"), { onShareCollection(detail) })
                    }
                }
            }
        }
    }
}

/** Horizontal card rail; songs play in place, everything else opens its page. */
private fun LazyListScope.catalogRail(
    items: List<CatalogItem>,
    source: String,
    onPlayTrack: (List<Track>, Int, String) -> Unit,
    onOpen: (String) -> Unit,
) {
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items, key = { index, it -> "${it.stableId}_$index" }) { index, item ->
                CatalogCard(item, modifier = Modifier.riseIn(index, fromScale = 0.9f, cascadeOnScroll = true), onClick = {
                    if (item is CatalogItem.Song) onPlayTrack(listOf(item.track), 0, source)
                    else onOpen(item.stableId)
                })
            }
        }
    }
}
