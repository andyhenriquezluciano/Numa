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

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.social.PartyPeer
import dev.sfg.numa.mobile.social.PartyRole
import dev.sfg.numa.mobile.social.PartyState
import dev.sfg.numa.mobile.social.PartyStatus
import dev.sfg.numa.mobile.social.cleanRoomCode
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface

@Composable
internal fun FrostedListeningPartyPanel(
    party: PartyState,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onTransferHost: ((String) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val accent = LocalAccent.current

    Surface(shape = shape, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Groups,
                        contentDescription = null,
                        tint = if (party.isActive) accent else CanopyColors.Muted,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = uiText("Shared Rooms", "Salas Compartidas"),
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = CanopyColors.Text,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (party.isActive) {
                FrostedActiveParty(party = party, onLeave = onLeave, onTransferHost = onTransferHost)
            } else {
                FrostedInactiveParty(party = party, onCreate = onCreate, onJoin = onJoin)
            }

            if (party.error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                FrostedMessageBanner(message = party.error, isError = true)
            }
        }
    }
}

@Composable
private fun FrostedInactiveParty(
    party: PartyState,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
) {
    var codeInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val accent = LocalAccent.current

    Text(
        text = uiText("Listen to the same music together.", "Escuchen la misma música juntos."),
        style = MaterialTheme.typography.bodyMedium,
        color = CanopyColors.Muted,
    )

    Spacer(Modifier.height(14.dp))

    // 6-Character Room Code Field
    TextField(
        value = codeInput,
        onValueChange = { codeInput = cleanRoomCode(it).take(ROOM_CODE_LENGTH) },
        placeholder = { Text(uiText("Room code", "Código de sala"), color = CanopyColors.Muted) },
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (codeInput.isNotBlank()) accent else CanopyColors.Muted,
            )
        },
        trailingIcon = {
            if (codeInput.isNotBlank()) {
                IconButton(onClick = { codeInput = "" }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = uiText("Clear", "Borrar"),
                        tint = CanopyColors.Muted,
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        getClipboardText(context)?.takeIf(String::isNotBlank)?.let {
                            codeInput = cleanRoomCode(it).take(ROOM_CODE_LENGTH)
                        }
                    }
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = uiText("Paste", "Pegar"), tint = accent)
                }
            }
        },
        shape = CircleShape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = CanopyColors.Text,
                unfocusedTextColor = CanopyColors.Text,
            ),
        modifier = Modifier.fillMaxWidth().desktopButtonSurface(),
    )

    Spacer(Modifier.height(14.dp))

    // Action Buttons Row: Join Party & Start Party
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onJoin(codeInput) },
            enabled = codeInput.length == ROOM_CODE_LENGTH && party.status != PartyStatus.CONNECTING,
            shape = CircleShape,
            colors =
                ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CanopyColors.Text),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).desktopButtonSurface(),
        ) {
            Text(uiText("Join party", "Unirse a la sala"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Button(
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CanopyColors.Text),
            onClick = onCreate,
            enabled = party.status != PartyStatus.CONNECTING,
            shape = CircleShape,
            modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).desktopButtonSurface(),
        ) {
            Text(
                uiText("Start a party", "Crear sala"),
                fontWeight = FontWeight.Bold,
                color = CanopyColors.Text,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun FrostedActiveParty(party: PartyState, onLeave: () -> Unit, onTransferHost: ((String) -> Unit)?) {
    val context = LocalContext.current
    val accent = LocalAccent.current

    // Session Status Label
    val statusText =
        when (party.status) {
            PartyStatus.CONNECTING -> uiText("Connecting to room…", "Conectando a la sala…")
            PartyStatus.OFFLINE -> uiText("Connection lost. Leave and rejoin the room.", "Se perdió la conexión. Sal y vuelve a entrar en la sala.")
            else ->
                if (party.isHost) uiText("Hosting room • All listeners follow your queue", "Eres el anfitrión · Todos los oyentes siguen tu cola")
                else uiText("Synced • Following host's playback", "Sincronizado · Siguiendo la reproducción del anfitrión")
        }

    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = if (party.status == PartyStatus.CONNECTED) accent else CanopyColors.Muted,
    )

    if (party.code.isNotBlank()) {
        Spacer(Modifier.height(14.dp))

        // Large Room Code Display Card with Copy & Share Actions
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            modifier =
                Modifier.fillMaxWidth().border(1.dp, CanopyColors.Rule, RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiText("ROOM CODE", "CÓDIGO DE SALA"),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        ),
                    color = CanopyColors.Eyebrow,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = party.code,
                    maxLines = 1,
                    softWrap = false,
                    style =
                        MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = CanopyColors.Text,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Copy Code Button
                    Surface(
                        onClick = {
                            copyToClipboard(context, party.code, label = uiText("NÜMA Room Code", "Código de sala de NÜMA"))
                            Toast.makeText(
                                    context,
                                    uiText("Room code copied to clipboard", "Código de sala copiado al portapapeles"),
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier =
                            Modifier.weight(1f)
                                .height(38.dp)
                                .border(1.dp, CanopyColors.Rule, CircleShape),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = uiText("Copy Code", "Copiar código"),
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = CanopyColors.Text,
                            )
                        }
                    }

                    // Share Link Button
                    Surface(
                        onClick = {
                            val shareUrl =
                                party.room?.let { room -> room.shareUrl.ifBlank { room.joinUrl } }
                                    ?: "numa-party://join/${party.code}"
                            val sendIntent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        uiText("Join my NÜMA listening party! Room code: ${party.code}\n$shareUrl", "¡Únete a mi sala de NÜMA! Código de sala: ${party.code}\n$shareUrl"),
                                    )
                                }
                            context.startActivity(
                                Intent.createChooser(sendIntent, uiText("Share listening party link", "Compartir enlace de la sala"))
                            )
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier =
                            Modifier.weight(1f)
                                .height(38.dp)
                                .border(1.dp, CanopyColors.Rule, CircleShape),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = uiText("Share", "Compartir"),
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = CanopyColors.Text,
                            )
                        }
                    }
                }
            }
        }
    }

    // Connected Peer Listeners Roster
    Spacer(Modifier.height(14.dp))
    Text(
        text =
            when (party.peers.size) {
                0 -> uiText("No other listeners in the room yet", "Todavía no hay otros oyentes en la sala")
                1 -> uiText("1 other listener", "Otro oyente")
                else -> uiText("${party.peers.size} other listeners", "${party.peers.size} oyentes más")
            },
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = CanopyColors.Muted,
    )

    party.peers.forEach { peer ->
        Spacer(Modifier.height(6.dp))
        FrostedPeerRow(peer = peer)
        if (party.isHost && onTransferHost != null) {
            androidx.compose.material3.TextButton(onClick = { onTransferHost(peer.id) }) {
                Text(uiText("Give control", "Dar control"), color = accent)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Leave or End Party Button
    Button(
        onClick = onLeave,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CanopyColors.Danger),
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp).desktopButtonSurface(),
    ) {
        Text(
            text = if (party.isHost) uiText("End session", "Cerrar sala") else uiText("Leave Party", "Salir de la sala"),
            color = CanopyColors.Danger,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FrostedPeerRow(peer: PartyPeer) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier =
            Modifier.fillMaxWidth().border(1.dp, CanopyColors.Rule, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = if (peer.open) LocalAccent.current else CanopyColors.Muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = peer.name.ifBlank { uiText("Listener", "Oyente") },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CanopyColors.Text,
                modifier = Modifier.weight(1f),
            )

            // WebRTC live sync dot
            Box(
                modifier =
                    Modifier.size(8.dp)
                        .clip(CircleShape)
                        .background(if (peer.open) LocalAccent.current else CanopyColors.Muted)
            )

            if (peer.role == PartyRole.HOST) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = LocalAccent.current) {
                    Text(
                        text = uiText("HOST", "ANFITRIÓN"),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
