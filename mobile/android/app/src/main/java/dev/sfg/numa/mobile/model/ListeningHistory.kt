package dev.sfg.numa.mobile.model

import java.time.LocalDate

data class ListeningDay(val date: String, val trackId: String, val listens: Int = 0, val heardMs: Long = 0)
enum class MetricsPeriod { WEEK, MONTH, YEAR, ALL }
data class ListeningReport(val tracks: List<Track>, val counts: Map<String, Int>, val heardMs: Long,
    val artists: List<Pair<String, Int>>, val albums: List<Pair<String, Int>>)

fun LibrarySnapshot.withListeningTime(track: Track, elapsedMs: Long, date: LocalDate = LocalDate.now()): LibrarySnapshot =
    if (track.id.isBlank() || elapsedMs <= 0) this else copy(
        listeningTotalMs = maxOf(listeningTotalMs, listeningDays.sumOf { it.heardMs }) + elapsedMs,
        playedTracks = listOf(track) + playedTracks.filterNot { it.id == track.id },
        listeningDays = updateDay(track.id, date, elapsedMs, 0),
    )

internal fun LibrarySnapshot.updateDay(id: String, date: LocalDate, ms: Long, count: Int): List<ListeningDay> {
    val day = date.toString()
    val old = listeningDays.firstOrNull { it.date == day && it.trackId == id }
    val next = ListeningDay(day, id, ((old?.listens ?: 0).toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        ((old?.heardMs ?: 0) + ms).coerceAtMost(86_400_000L))
    return listeningDays.filterNot { it.date == day && it.trackId == id } + next
}

fun LibrarySnapshot.listeningReport(period: MetricsPeriod, today: LocalDate = LocalDate.now()): ListeningReport {
    val start = when (period) {
        MetricsPeriod.WEEK -> today.minusDays(6)
        MetricsPeriod.MONTH -> today.minusDays(29)
        MetricsPeriod.YEAR -> today.minusDays(364)
        MetricsPeriod.ALL -> LocalDate.MIN
    }
    val days = listeningDays.filter { runCatching { LocalDate.parse(it.date) in start..today }.getOrDefault(false) }
    val counts = if (period == MetricsPeriod.ALL) playCounts else days.groupBy { it.trackId }
        .mapValues { (_, rows) -> rows.sumOf { it.listens.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    val tracks = (playedTracks + recentlyPlayed + likedTracks + savedAlbums.flatMap { it.tracks } + savedPlaylists.flatMap { it.tracks })
        .distinctBy(Track::id).filter { (counts[it.id] ?: 0) > 0 }
        .sortedWith(compareByDescending<Track> { counts[it.id] ?: 0 }.thenBy { it.title }.thenBy { it.id })
    fun grouped(key: (Track) -> String) = tracks.filter { key(it).isNotBlank() }.groupBy(key)
        .map { (name, rows) -> name to rows.sumOf { (counts[it.id] ?: 0).toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    return ListeningReport(tracks, counts, if (period == MetricsPeriod.ALL) maxOf(listeningTotalMs, days.sumOf { it.heardMs }) else days.sumOf { it.heardMs }, grouped { it.artist }, grouped { it.album })
}
