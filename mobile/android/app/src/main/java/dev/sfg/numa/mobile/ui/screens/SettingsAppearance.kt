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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.model.GlassTheme
import dev.sfg.numa.mobile.model.NumaSettings

@Composable
internal fun ThemeStyleSection(
    settings: NumaSettings,
    onSettings: (NumaSettings) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        AppearanceOptionTitle(uiText("Themes", "Temas"), Icons.Rounded.Palette)
        val themeSubtitle = when (settings.glassTheme) {
            GlassTheme.CRISTAL -> uiText("Light, translucent glass.", "Cristales claros y translúcidos.")
            GlassTheme.CRISTAL_OSCURO -> uiText("Dark glass with higher contrast.", "Cristales oscuros con mayor contraste.")
            GlassTheme.ROSE_PINE -> uiText("Rosé Pine colors with less transparency.", "Colores Rosé Pine y menor transparencia.")
            GlassTheme.ROSE_PINE_DAWN -> uiText("Warm, light Rosé Pine colors.", "Colores claros y cálidos de Rosé Pine.")
        }
        RowSubtitle(themeSubtitle)
        SettingsDropdownField(
            options = listOf(
                GlassTheme.CRISTAL to uiText("Glass", "Cristal"),
                GlassTheme.CRISTAL_OSCURO to uiText("Dark glass", "Cristal Oscuro"),
                GlassTheme.ROSE_PINE to "Rosé Pine",
                GlassTheme.ROSE_PINE_DAWN to "Rosé Pine Dawn",
            ),
            selected = settings.glassTheme,
            iconFor = { when (it) { GlassTheme.CRISTAL -> Icons.Rounded.AutoAwesome; GlassTheme.CRISTAL_OSCURO -> Icons.Rounded.DarkMode; else -> Icons.Rounded.Palette } },
            onSelect = { onSettings(settings.copy(glassTheme = it)) },
        )
    }

    PanelDivider()

    val bordersOn = settings.glassBorders == GlassBorders.PREDETERMINADO
    ToggleRow(
        title = uiText("Borders", "Bordes"),
        subtitle = if (bordersOn) uiText("Subtle borders on glass and buttons.", "Bordes sutiles en cristales y botones.")
            else uiText("Borderless glass and buttons.", "Cristales y botones sin bordes."),
        icon = Icons.Rounded.BorderStyle,
        checked = bordersOn,
        onChecked = { enabled ->
            onSettings(settings.copy(glassBorders = if (enabled) GlassBorders.PREDETERMINADO else GlassBorders.LIMPIO))
        },
    )
}

@Composable
private fun AppearanceOptionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = SettingsStyle.TileIcon)
        RowTitle(title)
    }
}
