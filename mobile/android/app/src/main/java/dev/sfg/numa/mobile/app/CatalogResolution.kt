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

package dev.sfg.numa.mobile.app

import dev.sfg.numa.mobile.model.Track

import dev.sfg.numa.mobile.model.BrowseDetail
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.CatalogSection
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.SearchResults

internal fun findCatalogItem(
    id: String,
    homeState: LoadState<List<CatalogSection>>,
    searchState: LoadState<SearchResults>,
    libraryState: LibrarySnapshot,
    detailState: LoadState<BrowseDetail>,
): CatalogItem? {
    val homeItem = (homeState as? LoadState.Content)?.value
        ?.asSequence()?.flatMap { it.items.asSequence() }?.firstOrNull { it.stableId == id }
    if (homeItem != null) return homeItem

    val searchItem = (searchState as? LoadState.Content)?.value?.let { result ->
        buildList<CatalogItem> {
            addAll(result.albums.map(CatalogItem::Record))
            addAll(result.artists.map(CatalogItem::Performer))
            addAll(result.playlists.map(CatalogItem::Collection))
        }.firstOrNull { it.stableId == id }
    }
    if (searchItem != null) return searchItem

    return libraryState.savedAlbums.firstOrNull { it.id == id }?.let(CatalogItem::Record)
        ?: libraryState.savedArtists.firstOrNull { it.id == id }?.let(CatalogItem::Performer)
        ?: libraryState.savedPlaylists.firstOrNull { it.id == id }?.let(CatalogItem::Collection)
        ?: libraryState.recentlyPlayed.firstOrNull { it.id == id }?.let(CatalogItem::Song)
        ?: (detailState as? LoadState.Content)?.value?.related?.firstOrNull { it.stableId == id }
}

internal fun BrowseDetail.withSeed(seed: CatalogItem?): BrowseDetail {
    if (seed == null) return this
    val seedSubtitle = when (seed) {
        is CatalogItem.Record -> seed.album.artist
        is CatalogItem.Performer -> seed.artist.subtitle
        is CatalogItem.Collection -> seed.playlist.author
        is CatalogItem.Song -> seed.track.artist
        is CatalogItem.Category -> ""
    }
    val cleanSeedArtist = if (seedSubtitle.equals("Playlist", ignoreCase = true) ||
        seedSubtitle.equals("Unlisted", ignoreCase = true) ||
        seedSubtitle.equals("Public", ignoreCase = true) ||
        seedSubtitle.equals("Private", ignoreCase = true)) "" else seedSubtitle

    return copy(
        title = title.takeUnless { it.isBlank() || it.equals("Collection", true) } ?: seed.title,
        artist = artist.ifBlank { cleanSeedArtist },
        subtitle = subtitle.ifBlank { seedSubtitle },
        artworkUrl = artworkUrl.ifBlank { seed.artworkUrl },
    )
}

internal fun String.isMeaningfulPlaybackSource(): Boolean = isNotBlank() && !contains(
    Regex("\\b(?:plays?|views?|listeners?|subscribers?)\\b", RegexOption.IGNORE_CASE),
)

/** Returns the same instance when [videoId] is absent; otherwise removes one playlist row. */
internal fun BrowseDetail.withPlaylistTrackRemoved(videoId: String): BrowseDetail {
    val index = tracks.indexOfFirst { it.id == videoId }
    if (index < 0) return this
    return copy(tracks = tracks.toMutableList().apply { removeAt(index) })
}

internal fun BrowseDetail.withLikedTrack(track: Track, liked: Boolean): BrowseDetail {
    if (id.removePrefix("VL") !in setOf("LM", "FEmusic_liked_videos")) return this
    val updated = if (liked) {
        if (tracks.any { it.id == track.id }) return this
        listOf(track) + tracks
    } else tracks.filterNot { it.id == track.id }
    return if (updated == tracks) this else copy(tracks = updated)
}

/** Returns the same instance for invalid/no-op moves; otherwise relocates exactly one row. */
internal fun BrowseDetail.withPlaylistTrackMoved(fromIndex: Int, toIndex: Int): BrowseDetail {
    if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return this
    return copy(tracks = tracks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
}
