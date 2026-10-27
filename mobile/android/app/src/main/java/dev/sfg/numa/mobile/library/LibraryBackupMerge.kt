package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Track

internal fun LibrarySnapshot.mergeBackup(other: LibrarySnapshot): LibrarySnapshot = copy(
    listeningTotalMs = maxOf(listeningTotalMs, other.listeningTotalMs),
    likedTracks = (likedTracks + other.likedTracks).distinctBy(Track::id),
    savedAlbums = (savedAlbums + other.savedAlbums).distinctBy { it.id },
    savedArtists = (savedArtists + other.savedArtists).distinctBy { it.id },
    savedPlaylists = (savedPlaylists + other.savedPlaylists).distinctBy { it.id }
        .filterNot { libraryPlaylistId(it.id) in hiddenPlaylistIds + other.hiddenPlaylistIds },
    recentlyPlayed = (recentlyPlayed + other.recentlyPlayed).distinctBy(Track::id).take(50),
    playedTracks = (playedTracks + other.playedTracks).distinctBy(Track::id),
    playCounts = (playCounts.keys + other.playCounts.keys).associateWith { maxOf(playCounts[it] ?: 0, other.playCounts[it] ?: 0) },
    hiddenPlaylistIds = hiddenPlaylistIds + other.hiddenPlaylistIds,
    listeningDays = (listeningDays + other.listeningDays).groupBy { it.date to it.trackId }.values.map { rows ->
        rows.first().copy(listens = rows.maxOf { it.listens }, heardMs = rows.maxOf { it.heardMs })
    },
)
