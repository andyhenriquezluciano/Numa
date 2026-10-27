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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.discord.DiscordPresenceStatus
import dev.sfg.numa.mobile.lastfm.LastfmState
import dev.sfg.numa.mobile.listenbrainz.ListenBrainzState
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.qobuz.QobuzQuality
import dev.sfg.numa.mobile.qobuz.QobuzStatus

/** Services listed under Connections; each opens its own detail page. */
internal enum class ConnectionService(val title: String, val blurb: String) {
    Discord("Discord", uiText("Share what you’re listening to", "Comparte lo que estás escuchando")),
    Lastfm("Last.fm", uiText("Send now-playing updates and completed listens", "Envía la canción actual y las reproducciones completadas")),
    ListenBrainz("ListenBrainz", uiText("Send listens with your user token", "Envía reproducciones con tu token de usuario")),
    Spotify("Spotify", uiText("Canvas videos in the player", "Vídeos Canvas en el reproductor")),
    Qobuz("Qobuz", uiText("Lossless and Hi-Res audio", "Audio sin pérdida y de alta resolución")),
    Numa(uiText("NÜMA Account", "Cuenta de NÜMA"), uiText("Use your account across devices", "Usa tu cuenta en varios dispositivos")),
}

/** One-line state for a service; [summary] doubles as the row subtitle. */
internal data class ConnectionStatus(val connected: Boolean, val summary: String)

internal fun connectionStatuses(
    settings: NumaSettings,
    discordStatus: DiscordPresenceStatus,
    lastfmState: LastfmState,
    listenBrainzState: ListenBrainzState,
    qobuzStatus: QobuzStatus,
    numaEmail: String,
): Map<ConnectionService, ConnectionStatus> = mapOf(
    ConnectionService.Discord to when {
        !settings.discordPresenceEnabled -> ConnectionStatus(false, uiText("Presence off", "Presencia desactivada"))
        discordStatus is DiscordPresenceStatus.Sharing -> ConnectionStatus(true, uiText("Sharing presence", "Compartiendo actividad"))
        discordStatus is DiscordPresenceStatus.Ready -> ConnectionStatus(true, uiText("Ready", "Listo"))
        discordStatus is DiscordPresenceStatus.Error -> ConnectionStatus(false, discordStatus.message)
        else -> ConnectionStatus(false, uiText("Discord app not installed", "Discord no está instalado"))
    },
    ConnectionService.Lastfm to when (lastfmState) {
        is LastfmState.Connected -> ConnectionStatus(true, uiText("Scrobbling on", "Registro de reproducciones activado"))
        LastfmState.Connecting -> ConnectionStatus(false, uiText("Starting browser authorization…", "Iniciando autorización en el navegador…"))
        is LastfmState.Pending -> ConnectionStatus(false, uiText("Approve NÜMA in your browser, then finish", "Autoriza NÜMA en el navegador y termina"))
        is LastfmState.Error -> ConnectionStatus(false, lastfmState.message)
        LastfmState.SignedOut -> ConnectionStatus(false, ConnectionService.Lastfm.blurb)
    },
    ConnectionService.ListenBrainz to when (listenBrainzState) {
        is ListenBrainzState.Connected -> ConnectionStatus(true, uiText("Connected as ${listenBrainzState.user}", "Conectado como ${listenBrainzState.user}"))
        ListenBrainzState.Validating -> ConnectionStatus(false, uiText("Checking your token…", "Comprobando tu token…"))
        is ListenBrainzState.Error -> ConnectionStatus(false, listenBrainzState.message)
        ListenBrainzState.SignedOut -> ConnectionStatus(false, ConnectionService.ListenBrainz.blurb)
    },
    ConnectionService.Spotify to if (settings.spotifySpdc.isNotBlank()) {
        ConnectionStatus(true, if (settings.spotifyCanvasEnabled) uiText("Canvas on", "Canvas activado") else uiText("Canvas off", "Canvas desactivado"))
    } else {
        ConnectionStatus(false, ConnectionService.Spotify.blurb)
    },
    ConnectionService.Qobuz to if (qobuzStatus.isConnected) {
        ConnectionStatus(true, if (qobuzStatus.enabled) qobuzStatus.quality.label else uiText("Disabled", "Desactivado"))
    } else {
        ConnectionStatus(false, ConnectionService.Qobuz.blurb)
    },
    ConnectionService.Numa to if (numaEmail.isNotBlank()) {
        ConnectionStatus(true, uiText("Signed in as $numaEmail", "Sesión iniciada como $numaEmail"))
    } else {
        ConnectionStatus(false, ConnectionService.Numa.blurb)
    },
)

/** Connected services first, the rest under Available with a Connect hint. */
@Composable
internal fun ConnectionsList(
    statuses: Map<ConnectionService, ConnectionStatus>,
    onOpen: (ConnectionService) -> Unit,
) {
    val (connected, available) = ConnectionService.entries.filterNot { it == ConnectionService.Numa ||
        (it == ConnectionService.Lastfm && dev.sfg.numa.connect.BuildConfig.NUMA_LASTFM_URL.isBlank()) }.partition { statuses[it]?.connected == true }
    if (connected.isNotEmpty()) {
        SectionLabel(uiText("Connected", "Conectado"))
        SettingsPanel(index = 1) {
            connected.forEachIndexed { index, service ->
                if (index > 0) PanelDivider()
                ConnectionRow(service, statuses.getValue(service), onOpen)
            }
        }
    }
    if (available.isNotEmpty()) {
        SectionLabel(uiText("Available", "Disponible"), 2)
        SettingsPanel(index = 3) {
            available.forEachIndexed { index, service ->
                if (index > 0) PanelDivider()
                ConnectionRow(service, statuses.getValue(service), onOpen)
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    service: ConnectionService,
    status: ConnectionStatus,
    onOpen: (ConnectionService) -> Unit,
) {
    SettingsRow(
        title = service.title,
        subtitle = status.summary,
        modifier = Modifier.clickable { onOpen(service) },
    ) {
        if (status.connected) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = SettingsStyle.Chevron,
            )
        } else {
            // Display-only: the whole row opens the detail page.
            Surface(
                shape = CircleShape,
                color = SettingsStyle.ButtonFill,
                border = BorderStroke(1.dp, SettingsStyle.ButtonBorder),
            ) {
                Text(
                    uiText("Connect", "Conectar"),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = SettingsStyle.Title,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Status pill shown under a detail page title. */
@Composable
internal fun ConnectionChip(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val lit = status.connected
    Surface(
        shape = CircleShape,
        color = if (lit) SettingsStyle.Sage.copy(alpha = 0.18f) else SettingsStyle.ButtonFill,
        border = BorderStroke(1.dp, if (lit) SettingsStyle.Sage.copy(alpha = 0.3f) else SettingsStyle.ButtonBorder),
        modifier = modifier,
    ) {
        Box(Modifier.defaultMinSize(minHeight = 28.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                if (lit) uiText("Connected", "Conectado") else "Not connected",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = if (lit) SettingsStyle.SageSoft else SettingsStyle.Description,
            )
        }
    }
}

/** Detail body: the service's own card inside one panel. */
@Composable
internal fun ConnectionDetail(
    service: ConnectionService,
    settings: NumaSettings,
    discordStatus: DiscordPresenceStatus,
    lastfmState: LastfmState,
    listenBrainzState: ListenBrainzState,
    qobuzStatus: QobuzStatus,
    onSettings: (NumaSettings) -> Unit,
    onConnectLastfm: () -> Unit,
    onCompleteLastfm: () -> Unit,
    onDisconnectLastfm: () -> Unit,
    onConnectListenBrainz: (String) -> Unit,
    onDisconnectListenBrainz: () -> Unit,
    onConnectSpotify: () -> Unit,
    onConnectQobuz: () -> Unit,
    onDisconnectQobuz: () -> Unit,
    onQobuzEnabledChange: (Boolean) -> Unit,
    onQobuzQualityChange: (QobuzQuality) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        SettingsPanel(index = 1) {
            when (service) {
                ConnectionService.Discord -> DiscordSettingsCard(
                    settings = settings,
                    discordStatus = discordStatus,
                    onSettings = onSettings,
                )
                ConnectionService.Lastfm -> LastfmSettingsCard(
                    state = lastfmState,
                    onConnect = onConnectLastfm,
                    onComplete = onCompleteLastfm,
                    onDisconnect = onDisconnectLastfm,
                )
                ConnectionService.ListenBrainz -> ListenBrainzSettingsCard(
                    state = listenBrainzState,
                    onConnect = onConnectListenBrainz,
                    onDisconnect = onDisconnectListenBrainz,
                )
                ConnectionService.Spotify -> SpotifySettingsCard(
                    settings = settings,
                    onSettings = onSettings,
                    onConnectSpotify = onConnectSpotify,
                )
                ConnectionService.Qobuz -> QobuzSettingsCard(
                    status = qobuzStatus,
                    onConnect = onConnectQobuz,
                    onDisconnect = onDisconnectQobuz,
                    onEnabledChange = onQobuzEnabledChange,
                    onQualityChange = onQobuzQualityChange,
                )
                ConnectionService.Numa -> NumaAccountSettingsCard()
            }
        }
    }
}
