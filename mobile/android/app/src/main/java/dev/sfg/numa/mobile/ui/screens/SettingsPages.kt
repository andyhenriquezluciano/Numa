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

import dev.sfg.numa.mobile.ui.text.uiText

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.connect.BuildConfig
import dev.sfg.numa.mobile.MobileUpdateMetadata
import dev.sfg.numa.mobile.UpdateState
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.settings.CacheManager

@Composable
internal fun AudioPage(
    settings: NumaSettings,
    onSettings: (NumaSettings) -> Unit,
    onAutoplayEnabled: ((Boolean) -> Unit)?,
) {
    SectionLabel(uiText("Quality", "Calidad"))
    SettingsPanel(index = 1) {
        QualityRow(settings.audioQuality, { onSettings(settings.copy(audioQuality = it)) })
        PanelDivider()
        QualityRow(
            settings.cellularAudioQuality,
            { onSettings(settings.copy(cellularAudioQuality = it)) },
            title = uiText("Mobile data quality", "Calidad con datos móviles"),
            icon = Icons.Rounded.SignalCellularAlt,
        )
    }

    SectionLabel(uiText("Volume", "Volumen"), 2)
    SettingsPanel(index = 3) {
        EqualizerRow(settings, onSettings)
        PanelDivider()
        ToggleRow(
            title = uiText("Exponential volume", "Volumen exponencial"),
            subtitle = uiText("Finer control at low volume", "Control más preciso al escuchar a volumen bajo"),
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            checked = settings.exponentialVolumeEnabled,
            onChecked = { onSettings(settings.copy(exponentialVolumeEnabled = it)) },
        )
        PanelDivider()
        ToggleRow(
            title = uiText("Volume normalization", "Normalización de volumen"),
            subtitle = uiText("Balance volume between songs", "Equilibra el volumen entre canciones"),
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            checked = settings.volumeNormalizationEnabled,
            onChecked = { onSettings(settings.copy(volumeNormalizationEnabled = it)) },
        )
    }

    SectionLabel(uiText("Transitions", "Transiciones"), 4)
    SettingsPanel(index = 5) { CrossfadeRow(settings, onSettings) }

    SectionLabel(uiText("Queue", "Cola"), 6)
    SettingsPanel(index = 7) {
        PlaybackOrderRow(settings, onSettings)
        PanelDivider()
        ToggleRow(
            title = uiText("Autoplay", "Reproducción automática"),
            subtitle = uiText("Add similar songs when the queue ends", "Añade canciones similares al terminar la cola"),
            icon = Icons.Rounded.Loop,
            checked = settings.autoplayEnabled,
            onChecked = { enabled ->
                onAutoplayEnabled?.invoke(enabled) ?: onSettings(settings.copy(autoplayEnabled = enabled))
            },
        )
    }

    SectionLabel(uiText("Non-music parts", "Partes sin música"), 8)
    SettingsPanel(index = 7) {
        NonMusicSkipRow(settings.nonMusicSkip) { onSettings(settings.copy(nonMusicSkip = it)) }
    }


    PlayerInteractionSettings(settings, onSettings)

}

@Composable
internal fun AppearancePage(
    settings: NumaSettings,
    onSettings: (NumaSettings) -> Unit,
    onHomeLayout: () -> Unit,
) {
    SectionLabel(uiText("Glass and style", "Cristales y estilo"), 1)
    SettingsPanel(index = 2) {
        ThemeStyleSection(settings, onSettings)
    }

    SectionLabel(uiText("Home", "Inicio"), 2)
    SettingsPanel(index = 3) {
        ActionRow(
            title = uiText("Home layout", "Diseño de Inicio"),
            subtitle = uiText("Reorder and hide Home sections", "Reordena y oculta las secciones de Inicio"),
            icon = Icons.Rounded.GridView,
            onClick = onHomeLayout,
        )
    }

    SectionLabel(uiText("Navigation", "Navegación"), 4)
    SettingsPanel(index = 5) {
        ToggleRow(
            title = uiText("Settings tab", "Pestaña de Ajustes"),
            subtitle = uiText("Show Settings in the bottom bar", "Muestra Ajustes en la barra inferior"),
            icon = Icons.Rounded.Settings,
            checked = settings.showSettingsTab,
            onChecked = { onSettings(settings.copy(showSettingsTab = it)) },
        )
    }

    SectionLabel(uiText("Player bar", "Barra del reproductor"), 6)
    SettingsPanel(index = 7) {
        ToggleRow(title = "Mostrar tasa de bits del audio", subtitle = uiText("Show the stream bitrate in the player.", "Muestra la tasa de transmisión en el reproductor."),
            icon = Icons.Rounded.Speed, checked = settings.showBitrate,
            onChecked = { onSettings(settings.copy(showBitrate = it)) })
    }
    SectionLabel(uiText("Animated artwork", "Carátulas animadas"), 10)
    SettingsPanel(index = 11) {
        ToggleRow(
            title = uiText("Enable animated artwork", "Activar carátulas animadas"),
            subtitle = uiText("Show animated covers when available", "Muestra portadas en movimiento cuando estén disponibles"),
            icon = Icons.Rounded.Image,
            checked = settings.animatedArtwork,
            onChecked = { onSettings(settings.copy(animatedArtwork = it)) },
        )
        SettingsDropdownField(
            options = listOf(true to uiText("Full", "Completa"), false to uiText("Normal size", "Tamaño normal")),
            selected = settings.expandedAnimatedArtwork,
            onSelect = { onSettings(settings.copy(expandedAnimatedArtwork = it)) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        PanelDivider()
        ArtworkSourceOrder(settings, onSettings)
    }

    SectionLabel(uiText("Immersive background", "Fondo inmersivo"), 12)
    SettingsPanel(index = 9) {
        ToggleRow(
            title = uiText("Use artwork background", "Usar fondo de la carátula"),
            subtitle = uiText("Animate the background with artwork colors", "Anima el fondo con los colores de la carátula"),
            icon = Icons.Rounded.AutoAwesome,
            checked = settings.animatedBackground,
            onChecked = { onSettings(settings.copy(animatedBackground = it)) },
        )
    }

}


@Composable
internal fun StoragePage(
    settings: NumaSettings,
    updateState: UpdateState,
    cacheSizeBytes: Long,
    isClearingCache: Boolean,
    onSettings: (NumaSettings) -> Unit,
    onClearCache: () -> Unit,
    downloadedBytes: Long,
    onDeleteAllDownloads: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: (MobileUpdateMetadata) -> Unit,
    onShowNotes: () -> Unit,
) {
    SectionLabel(uiText("Quality", "Calidad"))
    SettingsPanel {
        Column(Modifier.padding(20.dp)) {
            RowTitle(uiText("Download quality", "Calidad de descarga"))
            RowSubtitle(uiText("Quality for new downloads.", "Calidad de las nuevas descargas."))
            SettingsDropdownField(
                options = listOf(dev.sfg.numa.mobile.model.AudioQuality.DATA_SAVER to uiText("Data saver", "Ahorro"),
                    dev.sfg.numa.mobile.model.AudioQuality.NORMAL to "Normal", dev.sfg.numa.mobile.model.AudioQuality.HIGH to uiText("High", "Alta")),
                selected = settings.downloadQuality,
                onSelect = { onSettings(settings.copy(downloadQuality = it)) },
            )
        }
        PanelDivider()
        ToggleRow(title = uiText("Download animated artwork", "Descargar carátulas animadas"),
            subtitle = uiText("Save animated covers. Uses more storage.", "Guarda las portadas en movimiento. Ocupa más espacio."),
            icon = Icons.Rounded.Download, checked = settings.downloadAnimatedArtwork,
            onChecked = { onSettings(settings.copy(downloadAnimatedArtwork = it)) })
    }
    SectionLabel(uiText("Cache", "Caché"))
    SettingsPanel(index = 1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                CacheManager.formatStorageSize(cacheSizeBytes),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = SettingsStyle.Title,
            )
            RowSubtitle(uiText("Audio, artwork and temporary files", "Audio, carátulas y archivos temporales"))
        }
        PanelDivider()
        CacheSizeRow(settings, onSettings)
        PanelDivider()
        ClearCacheRow(cacheSizeBytes = cacheSizeBytes, isClearing = isClearingCache, onClear = onClearCache)
    }

    SectionLabel(uiText("Storage", "Almacenamiento"), 2)
    SettingsPanel(index = 3) {
        SdCardDownloadsRow(settings, onSettings)
        PanelDivider()
        DeleteDownloadsRow(downloadedBytes = downloadedBytes, onDelete = onDeleteAllDownloads)
    }

}
