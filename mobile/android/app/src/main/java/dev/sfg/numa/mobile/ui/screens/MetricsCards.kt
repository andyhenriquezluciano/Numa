package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.ListeningReport
import dev.sfg.numa.mobile.model.MetricsCollection
import dev.sfg.numa.mobile.ui.components.ListeningCountBadge
import dev.sfg.numa.mobile.ui.components.RemoteArtwork
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.LocalAccent

@Composable
internal fun MetricsSummary(report: ListeningReport) {
    val seconds = report.heardMs / 1_000
    val time = when {
        seconds < 60 -> "$seconds s"
        seconds < 3_600 -> "${seconds / 60} min"
        else -> "${seconds / 3_600} h ${(seconds % 3_600) / 60} min"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCell(Icons.Rounded.Headphones, "${report.counts.values.sumOf { it.toLong() }}",
            uiText("Listens", "Escuchas"), Modifier.weight(1f))
        SummaryCell(Icons.Rounded.Schedule, time, uiText("Listening time", "Tiempo"), Modifier.weight(1f))
        SummaryCell(Icons.Rounded.MusicNote, "${report.tracks.size}", uiText("Songs", "Canciones"), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Column(modifier.glassPane(RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = LocalAccent.current, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = SettingsStyle.Description,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MetricsRank(number: Int) {
    Box(Modifier.size(26.dp).background(LocalAccent.current.copy(alpha = if (number <= 3) 0.18f else 0f), CircleShape),
        contentAlignment = Alignment.Center) {
        Text("$number", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = if (number <= 3) LocalAccent.current else SettingsStyle.Description)
    }
}

@Composable
internal fun MetricsCollectionRow(row: MetricsCollection, rank: Int, onOpen: (String) -> Unit) {
    val graph = NumaGraph.from(LocalContext.current)
    val url by produceState(row.artworkUrl, row.key, row.artworkUrl, row.browseId) {
        value = MetricsArtwork.resolve(row, graph.catalog)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
        .glassPane(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
        .clickable(enabled = row.browseId.isNotBlank()) { onOpen(row.browseId) }
        .padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricsRank(rank)
        val shape = if (row.artist) CircleShape else RoundedCornerShape(12.dp)
        if (url.isNotBlank()) RemoteArtwork(url, row.name, Modifier.size(54.dp).clip(shape))
        else Box(Modifier.size(54.dp).clip(shape).background(LocalAccent.current.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(if (row.artist) Icons.Rounded.Person else Icons.Rounded.Album, row.name,
                tint = LocalAccent.current, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (row.songCount == 1) uiText("1 song", "1 canción") else uiText("${row.songCount} songs", "${row.songCount} canciones"),
                color = SettingsStyle.Description, style = MaterialTheme.typography.bodySmall)
        }
        ListeningCountBadge(row.listens)
    }
}
