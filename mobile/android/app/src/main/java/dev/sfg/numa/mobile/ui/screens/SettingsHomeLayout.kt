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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.auth.AuthState
import dev.sfg.numa.mobile.model.BuiltInHomeSection
import dev.sfg.numa.mobile.model.HomeSectionConfig
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.components.NumaChromeHeight
import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.scroll.NumaLazyColumn as LazyColumn
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
fun SettingsHomeLayout(
    settings: NumaSettings,
    auth: AuthState,
    onSettings: (NumaSettings) -> Unit,
    onBack: () -> Unit,
) {
    var online by remember { mutableStateOf(true) }
    val currentList = if (online) settings.homeLayoutOnline else settings.homeLayoutOffline
    fun save(list: List<HomeSectionConfig>) {
        onSettings(if (online) settings.copy(homeLayoutOnline = list) else settings.copy(homeLayoutOffline = list))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            SettingsHeader(
                title = uiText("Home Screen Layout", "Diseño de Inicio"),
                blurb = uiText("Reorder sections and choose what appears.", "Reordena las secciones y elige cuáles se muestran."),
                showBack = true, chip = null, onBack = onBack,
            )
            SettingsDropdownField(
                options = listOf(true to uiText("Online", "En línea"), false to uiText("Offline", "Sin conexión")),
                selected = online, onSelect = { online = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = NumaChromeHeight + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(currentList, key = { _, config -> config.section.name }) { index, config ->
                    SectionItemRow(
                        config = config, title = config.section.title(auth),
                        isFirst = index == 0, isLast = index == currentList.lastIndex,
                        onMoveUp = {
                            if (index > 0) save(currentList.toMutableList().apply {
                                java.util.Collections.swap(this, index, index - 1)
                            })
                        },
                        onMoveDown = {
                            if (index < currentList.lastIndex) save(currentList.toMutableList().apply {
                                java.util.Collections.swap(this, index, index + 1)
                            })
                        },
                        onToggle = { enabled ->
                            save(currentList.toMutableList().apply { this[index] = config.copy(enabled = enabled) })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionItemRow(
    config: HomeSectionConfig, title: String,
    isFirst: Boolean, isLast: Boolean,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit, onToggle: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = Color.Transparent, shape = shape,
        modifier = Modifier.fillMaxWidth().glassPane(shape).border(1.dp, SettingsStyle.PanelBorder, shape),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.titleMedium, color = SettingsStyle.Title)
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Box(Modifier.size(32.dp).desktopButtonSurface(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowUpward, uiText("Move up", "Mover hacia arriba"),
                        Modifier.size(18.dp), tint = SettingsStyle.Title.copy(alpha = if (isFirst) 0.3f else 1f))
                }
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Box(Modifier.size(32.dp).desktopButtonSurface(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowDownward, uiText("Move down", "Mover hacia abajo"),
                        Modifier.size(18.dp), tint = SettingsStyle.Title.copy(alpha = if (isLast) 0.3f else 1f))
                }
            }
            SettingsSwitch(config.enabled, onToggle)
        }
    }
}

private fun BuiltInHomeSection.title(auth: AuthState): String = when (this) {
        BuiltInHomeSection.YOUR_PLAYLISTS -> uiText("Your Playlists", "Tus listas")
        BuiltInHomeSection.SUBSCRIBED_ARTISTS -> uiText("Subscribed Artists", "Artistas que sigues")
        BuiltInHomeSection.TOP_SONGS -> if (auth is AuthState.SignedIn) uiText("Listening Review", "Resumen de escucha") else uiText("Top Songs", "Canciones destacadas")
        BuiltInHomeSection.RECOMMENDATIONS -> uiText("Recommendations", "Recomendaciones")
        BuiltInHomeSection.DOWNLOADED_PLAYLISTS -> uiText("Downloaded Playlists", "Listas descargadas")
        BuiltInHomeSection.DOWNLOADED_ARTISTS -> uiText("Downloaded Artists", "Artistas descargados")
        BuiltInHomeSection.DOWNLOADED_ALBUMS -> uiText("Downloaded Albums", "Álbumes descargados")
        BuiltInHomeSection.DOWNLOADED_SONGS -> uiText("Downloaded Songs", "Canciones descargadas")
    }
