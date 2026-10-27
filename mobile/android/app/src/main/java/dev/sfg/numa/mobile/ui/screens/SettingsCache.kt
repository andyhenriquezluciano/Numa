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

package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.glass.GlassAlertDialog

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.settings.CacheManager
import dev.sfg.numa.mobile.ui.theme.CanopyColors

/** Renders a megabyte count the way a listener thinks about storage. */
internal fun formatCacheSize(megabytes: Int): String =
    if (megabytes >= 1024) {
        val gigabytes = megabytes / 1024f
        if (gigabytes == gigabytes.toInt().toFloat()) "${gigabytes.toInt()} GB" else "%.1f GB".format(gigabytes)
    } else {
        "$megabytes MB"
    }

/**
 * Ceiling on the on-disk stream cache.
 *
 * Numa keeps whole tracks rather than only what is ahead of the playhead, so this is worth
 * exposing: it decides how much of a listening session survives to be replayed instantly, and it
 * is the difference between a few albums and a library.
 */
@Composable
internal fun CacheSizeRow(settings: NumaSettings, onSettings: (NumaSettings) -> Unit) {
    val steps = NumaSettings.CACHE_SIZE_STEPS_MB
    // The slider moves between stops rather than over megabytes, so the value is always a round
    // size and the control has somewhere obvious to land.
    val index = steps.indexOfFirst { it >= settings.cacheSizeMb }.takeIf { it >= 0 } ?: steps.lastIndex

    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RowIcon(Icons.Rounded.Inventory2)
            Column(Modifier.weight(1f)) {
                RowTitle(uiText("Cached audio", "Audio en caché"))
                RowSubtitle(uiText("Keep up to ${formatCacheSize(steps[index])} of played tracks for instant replay", "Conserva hasta ${formatCacheSize(steps[index])} de música reproducida para escucharla de nuevo al instante"))
            }
            Text(
                formatCacheSize(steps[index]),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = SettingsStyle.SageSoft,
            )
        }
        Column(Modifier.padding(top = 18.dp)) {
            CacheSizeSlider(index, steps) { onSettings(settings.copy(cacheSizeMb = steps[it])) }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatCacheSize(steps.first()),
                    color = SettingsStyle.Caption,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatCacheSize(steps.last()),
                    color = SettingsStyle.Caption,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                uiText("A new limit applies next time playback starts", "El nuevo límite se aplica al iniciar la próxima reproducción"),
                color = SettingsStyle.Caption,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Row displaying current cache size with an action to clear all temporary caches.
 * Prompts with an alert dialog before wiping stream cache, Coil image cache, and temporary files.
 */
@Composable
internal fun ClearCacheRow(
    cacheSizeBytes: Long,
    isClearing: Boolean,
    onClear: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val formattedSize = remember(cacheSizeBytes) { CacheManager.formatStorageSize(cacheSizeBytes) }

    SettingsRow(
        title = uiText("Clear cache", "Borrar caché"),
        subtitle = uiText("Downloads and your library stay intact ($formattedSize in cache)", "Tus descargas y tu biblioteca se conservan ($formattedSize en caché)"),
        icon = Icons.Rounded.Delete,
        titleColor = CanopyColors.Danger,
        iconTint = CanopyColors.Danger,
        enabled = !isClearing,
        modifier = Modifier.clickable(enabled = !isClearing) { showConfirmDialog = true },
    ) {
        if (isClearing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = SettingsStyle.Sage,
            )
        }
    }

    if (showConfirmDialog) {
        GlassAlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    uiText("Clear cache?", "¿Borrar la caché?"),
                    color = CanopyColors.Text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        uiText("This will free up $formattedSize of cached audio streams, album artwork, and temporary files.", "Se liberarán $formattedSize de audio en caché, carátulas y archivos temporales."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CanopyColors.Text,
                    )
                    Text(
                        uiText("Your downloaded offline songs and library will remain intact.", "Tus canciones descargadas y tu biblioteca se conservarán."),
                        style = MaterialTheme.typography.bodySmall,
                        color = CanopyColors.Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onClear()
                    },
                ) {
                    Text(
                        uiText("Clear", "Borrar"),
                        color = CanopyColors.Danger,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(uiText("Cancel", "Cancelar"), color = CanopyColors.Muted)
                }
            },
            shape = RoundedCornerShape(16.dp),
        )
    }
}
