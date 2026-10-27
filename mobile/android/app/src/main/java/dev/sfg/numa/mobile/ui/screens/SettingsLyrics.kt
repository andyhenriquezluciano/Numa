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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.lyrics.translation.TranslationNative
import dev.sfg.numa.mobile.model.LyricTranslationQuality
import dev.sfg.numa.mobile.model.LyricTranslationProvider
import dev.sfg.numa.mobile.model.NumaSettings

/** Lyric translation source, API credentials, and the models already on this phone. */
@Composable
internal fun LyricTranslationSection(
    settings: NumaSettings,
    onSettings: (NumaSettings) -> Unit,
    labelIndex: Int,
) {
    val translator = NumaGraph.from(LocalContext.current).lyricTranslation
    val installed by translator.installed.collectAsStateWithLifecycle()
    val translationState by translator.state.collectAsStateWithLifecycle()
    val provider = settings.lyricTranslationProvider
    SectionLabel("Letras", labelIndex)
    SettingsPanel(index = labelIndex + 1) {
        ToggleRow(
            title = "Traducir letras al español",
            subtitle = if (provider == LyricTranslationProvider.LOCAL)
                if (settings.translateLyrics && translationState.message.isNotBlank()) translationState.message
                else "Traduce sin conexión con modelos descargados por Wi-Fi."
            else "Traduce en línea y envía las letras a Google.",
            icon = Icons.Rounded.Translate,
            checked = settings.translateLyrics,
            onChecked = { onSettings(settings.copy(translateLyrics = it)) },
        )
        PanelDivider()
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            RowTitle("Fuente de traducción")
            RowSubtitle(if (provider == LyricTranslationProvider.LOCAL) "Traducción en el teléfono."
                else "Google Translate · conexión a Internet.")
            SettingsDropdownField(
                options = listOf(LyricTranslationProvider.GOOGLE, LyricTranslationProvider.LOCAL).map { it to it.label },
                selected = provider,
                onSelect = { onSettings(settings.copy(lyricTranslationProvider = it)) },
            )
        }
        if (provider == LyricTranslationProvider.LOCAL && TranslationNative.available) {
            PanelDivider()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RowIcon(Icons.Rounded.Tune)
                    Column {
                        RowTitle("Calidad de traducción")
                        RowSubtitle(
                            when (settings.lyricTranslationQuality) {
                                LyricTranslationQuality.STANDARD -> "Modelo local descargable"
                                LyricTranslationQuality.HIGH -> "Modelo local de mayor calidad"
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SettingsDropdownField(
                    options = listOf(LyricTranslationQuality.STANDARD to "Estándar", LyricTranslationQuality.HIGH to "Alta"),
                    selected = settings.lyricTranslationQuality,
                    onSelect = { onSettings(settings.copy(lyricTranslationQuality = it)) },
                )
            }
            installed.forEach { pack ->
                PanelDivider()
                SettingsRow(
                    title = pack.name + if (pack.quality == "high") " (Alta)" else "",
                    subtitle = "%.0f MB · %s".format(pack.bytes / 1e6, pack.credit),
                ) {
                    SettingsPill(label = "Eliminar", destructive = true, onClick = { translator.remove(pack) })
                }
            }
        }
    }

}
