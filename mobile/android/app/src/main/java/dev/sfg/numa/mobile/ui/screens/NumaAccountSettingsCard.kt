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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.numa.mobile.auth.NumaAccountService
import java.text.DateFormat
import java.util.Date

@Composable
fun NumaAccountSettingsCard() {
    val context = LocalContext.current
    val account = remember { NumaAccountService.get(context) }
    val state by account.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) account.refreshDevices()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            if (state.signedIn) state.name.ifBlank { state.email } else
                uiText("Sign in with Google to use your NÜMA account on this device.", "Inicia sesión con Google para usar tu cuenta de NÜMA en este dispositivo."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.signedIn) {
            Text(state.email, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(state.error, color = Color(0xFFFF7777), style = MaterialTheme.typography.bodySmall)
            if (state.signedIn) SettingsPill(uiText("Retry connection", "Reintentar conexión"),
                onClick = { if (!state.loadingDevices) account.refreshDevices() }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.signingIn -> {
                Text(uiText("Finish signing in in your browser.", "Termina de iniciar sesión en el navegador."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                SettingsPill(uiText("Cancel", "Cancelar"), onClick = account::cancelSignIn, modifier = Modifier.fillMaxWidth())
            }
            state.signedIn -> {
                SettingsPill(uiText("Sign out", "Cerrar sesión"), onClick = account::signOut,
                    modifier = Modifier.fillMaxWidth(), destructive = true)
                Spacer(Modifier.height(20.dp))
                Text(uiText("Devices", "Dispositivos"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (state.loadingDevices && state.devices.isEmpty()) {
                    Text(uiText("Loading devices…", "Cargando dispositivos…"), style = MaterialTheme.typography.bodySmall)
                }
                state.devices.forEach { device ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name + if (device.current) uiText(" · This device", " · Este dispositivo") else "")
                            val lastSeen = DateFormat.getDateInstance().format(Date(device.lastSeenAt * 1000))
                            Text(uiText("${device.platform} · Last seen $lastSeen", "${device.platform} · Última conexión: $lastSeen"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!device.current) {
                            SettingsPill(uiText("Remove", "Eliminar"), onClick = { account.removeDevice(device) })
                        }
                    }
                }
            }
            else -> SettingsPill(uiText("Sign in with Google", "Iniciar sesión con Google"), onClick = account::signIn,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
