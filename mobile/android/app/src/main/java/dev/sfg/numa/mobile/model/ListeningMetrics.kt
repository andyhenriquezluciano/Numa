package dev.sfg.numa.mobile.model

fun LibrarySnapshot.listeningMetrics(): List<Track> {
    val recency = recentlyPlayed.mapIndexed { index, track -> track.id to index }.toMap()
    return (playedTracks + recentlyPlayed + likedTracks + savedAlbums.flatMap { it.tracks } +
        savedPlaylists.flatMap { it.tracks }).distinctBy(Track::id)
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
