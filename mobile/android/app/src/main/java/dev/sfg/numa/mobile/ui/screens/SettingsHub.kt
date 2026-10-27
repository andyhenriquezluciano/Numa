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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.glassPane
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.sfg.numa.mobile.auth.AuthState
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.theme.CanopyColors

/** Live values the category rows summarise. Built once by the screen so the hub stays dumb. */
internal data class SettingsSummaries(
    val connectedCount: Int,
    val storage: String,
)

/** Top level of settings: search, account, quick switches and one row per page. */
@Composable
internal fun SettingsHub(
    settings: NumaSettings,
    auth: AuthState,
    summaries: SettingsSummaries,
    onSettings: (NumaSettings) -> Unit,
    onAutoplayEnabled: ((Boolean) -> Unit)?,
    onOpen: (SettingsPage) -> Unit,
    onSignIn: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    SettingsSearchField(query) { query = it }

    if (query.isNotBlank()) {
        SearchResults(query, onOpen)
        return
    }

    SettingsLanguageRow()
    SectionLabel("Configuración", 1)
    SettingsPanel(index = 5) {
        ActionRow(title = SettingsPage.Accounts.displayTitle(), subtitle = SettingsPage.Accounts.blurb,
            icon = SettingsPage.Accounts.icon, onClick = { onOpen(SettingsPage.Accounts) })
        PanelDivider()
        ActionRow(title = SettingsPage.Lyrics.displayTitle(), subtitle = SettingsPage.Lyrics.blurb,
            icon = SettingsPage.Lyrics.icon, onClick = { onOpen(SettingsPage.Lyrics) })
        PanelDivider()
        ActionRow(
            title = SettingsPage.Audio.displayTitle(),
            subtitle = "Calidad, volumen, transiciones y cola",
            icon = SettingsPage.Audio.icon,
            onClick = { onOpen(SettingsPage.Audio) },
        )
        PanelDivider()
        ActionRow(
            title = SettingsPage.Storage.displayTitle(),
            subtitle = "Carátulas y almacenamiento sin conexión",
            icon = SettingsPage.Storage.icon,
            onClick = { onOpen(SettingsPage.Storage) },
        )
        PanelDivider()
        ActionRow(
            title = SettingsPage.Appearance.displayTitle(),
            subtitle = "Temas, bordes, carátulas y fondos",
            icon = SettingsPage.Appearance.icon,
            onClick = { onOpen(SettingsPage.Appearance) },
        )
        PanelDivider()
        ActionRow(
            title = SettingsPage.Connections.displayTitle(),
            subtitle = "Discord, Last.fm, Spotify y Qobuz",
            icon = SettingsPage.Connections.icon,
            onClick = { onOpen(SettingsPage.Connections) },
        )
    }

}

@Composable
private fun SearchResults(query: String, onOpen: (SettingsPage) -> Unit) {
    val results = searchSettings(query)
    Column(Modifier.padding(top = 16.dp)) {
        if (results.isEmpty()) {
            Text(
                "No se encontraron ajustes para «${query.trim()}»",
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsStyle.Description,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            )
            return
        }
        if (results.any { it.page == SettingsPage.Home }) SettingsLanguageRow()
        val destinations = results.filter { it.page != SettingsPage.Home }
        if (destinations.isEmpty()) return
        SettingsPanel {
            destinations.forEachIndexed { index, entry ->
                if (index > 0) PanelDivider()
                ActionRow(
                    title = entry.title,
                    subtitle = "${entry.page.title} · ${entry.subtitle}",
                    icon = entry.page.icon,
                    onClick = { onOpen(entry.page) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = SettingsStyle.Title, fontSize = 15.sp),
        cursorBrush = SolidColor(SettingsStyle.SageSoft),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Buscar en ajustes" },
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(shape)
                    .glassPane(shape, GlassTone.CONTROL)
                    .border(1.dp, SettingsStyle.PanelBorder, shape)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = SettingsStyle.Description, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Buscar en ajustes", color = SettingsStyle.Description, fontSize = 15.sp)
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(48.dp).clickable(role = Role.Button) { onQuery("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Borrar búsqueda", tint = SettingsStyle.Description, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

private data class QuickSetting(val icon: ImageVector, val label: String, val on: Boolean, val onChange: (Boolean) -> Unit)

/** One touch target controls the tile and its displayed switch. */
@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .glassPane(shape, GlassTone.CONTROL)
            .background(if (on) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .border(1.dp, if (on) SettingsStyle.Sage.copy(alpha = 0.45f) else SettingsStyle.PanelBorder, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .defaultMinSize(minHeight = 116.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SettingsStyle.SageSoft, modifier = Modifier.size(22.dp))
            SettingsSwitch(checked = on, onCheckedChange = null)
        }
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = SettingsStyle.Title,
            )
            Text(
                if (on) "Activado" else "Desactivado",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = SettingsStyle.Description,
            )
        }
    }
}

/** Signed-in identity with switch and sign-out actions. */
@Composable
internal fun AccountCard(
    auth: AuthState,
    onSignIn: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    SettingsPanel(index = 1) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val avatarUrl = (auth as? AuthState.SignedIn)?.avatarUrl.orEmpty()
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SettingsStyle.Tile)
                        .border(1.dp, SettingsStyle.TileBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = SettingsStyle.TileIcon, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    when (auth) {
                        AuthState.Restoring -> AccountText("Comprobando sesión…", "Un momento")
                        AuthState.Authorizing -> AccountText("Conectando tu cuenta…", "Un momento")
                        AuthState.SignedOut -> AccountText("Tu cuenta de YouTube Music", "Sincroniza tu biblioteca y recomendaciones")
                        // displayName falls back to the literal "YouTube Music", so the subtitle must not repeat it.
                        is AuthState.SignedIn -> AccountText(auth.displayName, "Sesión iniciada · YouTube Music")
                        is AuthState.Error -> AccountText("No se pudo iniciar sesión", auth.message, CanopyColors.Danger)
                    }
                }
            }
            when (auth) {
                AuthState.SignedOut -> SettingsPill("Iniciar sesión", onSignIn, Modifier.fillMaxWidth())
                is AuthState.Error -> SettingsPill("Reintentar", onSignIn, Modifier.fillMaxWidth())
                is AuthState.SignedIn -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsPill("Cambiar cuenta", onSwitchAccount, Modifier.weight(1f))
                    SettingsPill("Cerrar sesión", onSignOut, Modifier.weight(1f), destructive = true)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun AccountText(title: String, subtitle: String, titleColor: Color = SettingsStyle.Title) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        color = titleColor,
    )
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = SettingsStyle.Description,
    )
}
