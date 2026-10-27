package dev.sfg.numa.mobile.model

import java.util.Locale

data class MetricsCollection(val key: String, val name: String, val artworkUrl: String,
    val browseId: String, val songCount: Int, val listens: Int, val artist: Boolean)

fun ListeningReport.collections(library: LibrarySnapshot, artists: Boolean): List<MetricsCollection> {
    fun normalized(value: String) = value.trim().lowercase(Locale.ROOT)
    val grouped = linkedMapOf<String, Pair<MetricsCollection, MutableSet<String>>>()
    for (track in tracks) {
        val rows = if (artists) {
            val credits = track.artists.ifEmpty {
                listOf(Artist(track.artistId, track.artist))
            }.distinctBy { it.id.ifBlank { normalized(it.name) } }
            credits.filter { it.name.isNotBlank() }.map { credit ->
                val saved = library.savedArtists.firstOrNull {
                    (credit.id.isNotBlank() && it.id == credit.id) || normalized(it.name) == normalized(credit.name)
                }
                MetricsCollection("artist:${normalized(credit.name)}", credit.name,
                    credit.artworkUrl.ifBlank { saved?.artworkUrl.orEmpty() },
                    credit.id.ifBlank { saved?.id.orEmpty() }, 0, 0, true)
            }
        } else {
            if (track.album.isBlank()) emptyList() else {
                val saved = library.savedAlbums.firstOrNull {
                    (track.albumId.isNotBlank() && it.id == track.albumId) ||
                        (normalized(it.title) == normalized(track.album) && normalized(it.artist) == normalized(track.artist))
                }
                val id = track.albumId.ifBlank { saved?.id.orEmpty() }
                listOf(MetricsCollection("album:" + id.ifBlank { "${normalized(track.artist)}:${normalized(track.album)}" },
                    track.album, saved?.artworkUrl?.takeIf { it.isNotBlank() } ?: track.artworkUrl, id, 0, 0, false))
            }
        }
        for (row in rows) {
            val (old, songs) = grouped[row.key] ?: (row to mutableSetOf())
            if (!songs.add(track.id)) continue
            grouped[row.key] = old.copy(
                artworkUrl = old.artworkUrl.ifBlank { row.artworkUrl }, browseId = old.browseId.ifBlank { row.browseId },
                songCount = songs.size,
                listens = (old.listens.toLong() + (counts[track.id] ?: 0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) to songs
        }
    }
    return grouped.values.map { it.first }.sortedWith(compareByDescending<MetricsCollection> { it.listens }
        .thenBy { normalized(it.name) }.thenBy { it.key })
}
