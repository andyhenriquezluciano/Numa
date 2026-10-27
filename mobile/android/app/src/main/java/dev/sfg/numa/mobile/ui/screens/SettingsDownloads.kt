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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.download.DownloadStore
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.settings.CacheManager
import dev.sfg.numa.mobile.ui.theme.CanopyColors

/** Toggle for placing new downloads on the SD card; disabled while no card is mounted. */
@Composable
internal fun SdCardDownloadsRow(settings: NumaSettings, onSettings: (NumaSettings) -> Unit) {
    val context = LocalContext.current
    val available = remember { DownloadStore.sdCardDownloadDir(context) != null }
    ToggleRow(
        title = uiText("Download to SD card", "Descargar en la tarjeta SD"),
        subtitle = if (available) {
            uiText("Save new downloads to removable storage. Existing downloads stay where they are", "Guarda las nuevas descargas en el almacenamiento extraíble. Las existentes no se mueven")
        } else {
            uiText("No SD card detected", "No se detectó una tarjeta SD")
        },
        icon = Icons.Rounded.SdCard,
        checked = settings.downloadToSdCard && available,
        onChecked = { onSettings(settings.copy(downloadToSdCard = it)) },
        enabled = available,
    )
}

/** Deletes every offline track and its saved motion cover after a confirmation. */
@Composable
internal fun DeleteDownloadsRow(downloadedBytes: Long, onDelete: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val formattedSize = remember(downloadedBytes) { CacheManager.formatStorageSize(downloadedBytes) }

    SettingsRow(
        title = uiText("Delete all downloads", "Eliminar todas las descargas"),
        subtitle = uiText("Removes offline songs and motion covers ($formattedSize used)", "Elimina las canciones sin conexión y las carátulas animadas ($formattedSize utilizados)"),
        icon = Icons.Rounded.DeleteForever,
        titleColor = CanopyColors.Danger,
        iconTint = CanopyColors.Danger,
        modifier = Modifier.clickable { showConfirmDialog = true },
    )

    if (showConfirmDialog) {
        GlassAlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(uiText("Delete all downloads?", "¿Eliminar todas las descargas?"), color = CanopyColors.Text, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        uiText("This frees $formattedSize and cancels downloads in progress.", "Libera $formattedSize y cancela las descargas en curso."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CanopyColors.Text,
                    )
                    Text(
                        uiText("Songs remain in your library and can be downloaded again.", "Las canciones permanecen en tu biblioteca y puedes descargarlas de nuevo."),
                        style = MaterialTheme.typography.bodySmall,
                        color = CanopyColors.Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onDelete()
                    },
                ) {
                    Text(uiText("Delete", "Eliminar"), color = CanopyColors.Danger, fontWeight = FontWeight.Bold)
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
