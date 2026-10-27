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

import dev.sfg.numa.mobile.ui.glass.glassPane

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.discord.DiscordPresenceStatus
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent


@Composable
fun DiscordSettingsCard(
    settings: NumaSettings,
    discordStatus: DiscordPresenceStatus,
    onSettings: (NumaSettings) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = Color.Transparent,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            when (discordStatus) {
                DiscordPresenceStatus.Unavailable -> Text(
                    uiText("Install and sign in to the Discord app to share what you’re listening to.", "Instala Discord e inicia sesión para compartir lo que estás escuchando."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CanopyColors.Muted,
                )
                is DiscordPresenceStatus.Error -> Text(
                    discordStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = CanopyColors.Danger,
                )
                else -> Text(
                    uiText("Uses the Discord app on this device.", "Usa la aplicación de Discord de este dispositivo."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CanopyColors.Muted,
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CanopyColors.Rule)
            )
            Spacer(Modifier.height(12.dp))

            DiscordToggleRow(
                icon = Icons.Rounded.Podcasts,
                title = uiText("Share presence", "Compartir actividad"),
                subtitle = uiText("Display currently playing track on Discord", "Muestra en Discord la canción que estás escuchando"),
                checked = settings.discordPresenceEnabled,
                onChecked = { onSettings(settings.copy(discordPresenceEnabled = it)) },
            )

        }
    }
}

@Composable
private fun DiscordToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .glassPane(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) LocalAccent.current else CanopyColors.Muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) CanopyColors.Text else CanopyColors.Muted,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CanopyColors.Muted,
            )
        }
        SettingsSwitch(
            checked = checked && enabled,
            enabled = enabled,
            onCheckedChange = onChecked,
        )
    }
}
