package dev.sfg.numa.mobile.model

fun LibrarySnapshot.listeningMetrics(): List<Track> {
    val recency = recentlyPlayed.mapIndexed { index, track -> track.id to index }.toMap()
    return metricTracks()
        .filter { it.id.isNotBlank() && (playCounts[it.id] ?: 0) >= 1 }
        .sortedWith(compareByDescending<Track> { playCounts[it.id] ?: 0 }
            .thenBy { recency[it.id] ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase(java.util.Locale.ROOT) }.thenBy(Track::id))
}

fun LibrarySnapshot.withRecordedListen(track: Track): LibrarySnapshot {
    if (track.id.isBlank()) return this
    val count = (playCounts[track.id] ?: 0).toLong().coerceAtLeast(0) + 1
    return copy(
        recentlyPlayed = listOf(track) + recentlyPlayed.filterNot { it.id == track.id }.take(49),
        playedTracks = listOf(track) + playedTracks.filterNot { it.id == track.id },
        playCounts = playCounts + (track.id to count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
        listeningDays = updateDay(track.id, java.time.LocalDate.now(), 0, 1),
    )
}

/** Merge display metadata without losing the recorded identity, order or counters. */
internal fun LibrarySnapshot.metricTracks(): List<Track> =
    (playedTracks + recentlyPlayed + likedTracks + savedAlbums.flatMap { it.tracks } + savedPlaylists.flatMap { it.tracks })
        .groupBy(Track::id).values.map { copies ->
            val first = copies.first()
            first.copy(
                artworkUrl = copies.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty(),
                albumId = copies.firstOrNull { it.albumId.isNotBlank() }?.albumId.orEmpty(),
                artistId = copies.firstOrNull { it.artistId.isNotBlank() }?.artistId.orEmpty(),
                artists = copies.firstOrNull { it.artists.isNotEmpty() }?.artists.orEmpty(),
                album = copies.firstOrNull { it.album.isNotBlank() }?.album.orEmpty(),
            )
        }
