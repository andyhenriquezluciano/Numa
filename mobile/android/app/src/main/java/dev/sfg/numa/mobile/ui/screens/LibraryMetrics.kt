package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.model.MetricsPeriod
import dev.sfg.numa.mobile.model.listeningReport
import dev.sfg.numa.mobile.model.collections
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import dev.sfg.numa.mobile.ui.components.TrackRow
import dev.sfg.numa.mobile.ui.components.filterTracks
import dev.sfg.numa.mobile.ui.text.uiText

internal fun LazyListScope.listeningMetrics(
    library: LibrarySnapshot,
    query: String,
    onPlay: (Track) -> Unit,
    period: MetricsPeriod,
    onPeriod: (MetricsPeriod) -> Unit,
    onOpen: (String) -> Unit,
    expandedSections: List<String>,
    onToggleSection: (String) -> Unit,
) {
    val report = library.listeningReport(period)
    val ranked = report.tracks
    val rankById = ranked.mapIndexed { index, track -> track.id to index + 1 }.toMap()
    val songs = filterTracks(ranked, query)
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            MetricsPeriod.entries.forEach { value ->
                FilterChip(selected = period == value, onClick = { onPeriod(value) },
                    border = if (dev.sfg.numa.mobile.ui.glass.LocalGlass.current.borders == dev.sfg.numa.mobile.model.GlassBorders.LIMPIO)
                        null else FilterChipDefaults.filterChipBorder(enabled = true, selected = period == value),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = dev.sfg.numa.mobile.ui.theme.LocalAccent.current.copy(alpha = 0.18f),
                        selectedLabelColor = dev.sfg.numa.mobile.ui.theme.LocalAccent.current),
                    label = { Text(when (value) {
                        MetricsPeriod.WEEK -> uiText("7 days", "7 días")
                        MetricsPeriod.MONTH -> uiText("30 days", "30 días")
                        MetricsPeriod.YEAR -> uiText("365 days", "365 días")
                        MetricsPeriod.ALL -> uiText("All time", "Todo")
                    }) }, modifier = Modifier.padding(end = 8.dp))
            }
        }
        MetricsSummary(report)
        LibraryLabel(uiText("Most listened songs", "Más escuchadas"))
    }
    if (songs.isEmpty()) {
        item { LibraryEmpty(Icons.Rounded.BarChart,
            if (query.isBlank()) uiText("No listens yet", "Aún no hay escuchas") else uiText("No matching songs", "No hay coincidencias"),
            uiText("Songs appear here after you play them.", "Las canciones aparecerán aquí cuando las reproduzcas.")) }
    }
    itemsIndexed(if ("songs" in expandedSections) songs else songs.take(5), key = { _, track -> "metrics:${track.id}" }) { _, track ->
        val count = report.counts[track.id] ?: 0
        Row(Modifier.padding(horizontal = 12.dp).animateItem(), verticalAlignment = Alignment.CenterVertically) {
            MetricsRank(rankById.getValue(track.id))
            TrackRow(track, onPlay = { onPlay(track) }, modifier = Modifier.weight(1f),
                listenCount = count)
        }
    }
    metricsMore("songs", songs.size, "songs" in expandedSections, onToggleSection)
    if (query.isBlank()) {
        val artists = report.collections(library, artists = true)
        val albums = report.collections(library, artists = false)
        item { LibraryLabel(uiText("Most listened artists", "Artistas más escuchados")) }
        itemsIndexed(if ("artists" in expandedSections) artists else artists.take(5), key = { _, row -> row.key }) { index, row ->
            MetricsCollectionRow(row, index + 1, onOpen)
        }
        metricsMore("artists", artists.size, "artists" in expandedSections, onToggleSection)
        item { LibraryLabel(uiText("Most listened albums", "Álbumes más escuchados")) }
        itemsIndexed(if ("albums" in expandedSections) albums else albums.take(5), key = { _, row -> row.key }) { index, row ->
            MetricsCollectionRow(row, index + 1, onOpen)
        }
        metricsMore("albums", albums.size, "albums" in expandedSections, onToggleSection)
    }
}

private fun LazyListScope.metricsMore(section: String, count: Int, expanded: Boolean, onToggle: (String) -> Unit) {
    if (count <= 5) return
    item(key = "metrics-toggle:$section") {
        SettingsPill(
            label = if (expanded) uiText("Show less", "Mostrar menos") else uiText("Show more", "Mostrar más"),
            onClick = { onToggle(section) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
