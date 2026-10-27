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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.lastfm.LastfmState
import dev.sfg.numa.mobile.ui.theme.CanopyColors

@Composable
fun LastfmSettingsCard(
    state: LastfmState,
    onConnect: () -> Unit,
    onComplete: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(color = Color.Transparent, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                lastfmSubtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is LastfmState.Error) CanopyColors.Danger else CanopyColors.Muted,
            )

            Spacer(Modifier.height(12.dp))
            when (state) {
                LastfmState.SignedOut,
                is LastfmState.Error -> IntegrationAction(uiText("Connect Last.fm", "Conectar Last.fm"), onClick = onConnect)
                LastfmState.Connecting -> IntegrationAction("Connecting…", onClick = {})
                is LastfmState.Pending ->
                    IntegrationAction(uiText("Finish connection", "Completar conexión"), onClick = onComplete)
                is LastfmState.Connected ->
                    IntegrationAction(uiText("Sign out of Last.fm", "Cerrar sesión en Last.fm"), destructive = true, onClick = onDisconnect)
            }
        }
    }
}

private fun lastfmSubtitle(state: LastfmState): String =
    when (state) {
        LastfmState.SignedOut -> uiText("Send now-playing updates and completed listens", "Envía la canción actual y las reproducciones completadas")
        LastfmState.Connecting -> uiText("Starting browser authorization…", "Iniciando autorización en el navegador…")
        is LastfmState.Pending -> uiText("Approve NÜMA in your browser, then finish here", "Autoriza NÜMA en el navegador y termina aquí")
        is LastfmState.Connected -> uiText("Connected as ${state.user}", "Conectado como ${state.user}")
        is LastfmState.Error -> state.message
    }
