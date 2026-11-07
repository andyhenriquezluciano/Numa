package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sfg.numa.connect.BuildConfig
import dev.sfg.numa.mobile.UpdateState
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun SettingsUpdateRow(state: UpdateState, onCheck: () -> Unit) {
    val checking = state == UpdateState.Checking
    val busy = checking || state is UpdateState.Downloading || state is UpdateState.ReadyToInstall
    val enabled = BuildConfig.UPDATER_ENABLED && !busy
    val subtitle = if (!BuildConfig.UPDATER_ENABLED) {
        uiText("Updates are disabled in this build", "Las actualizaciones están desactivadas en esta variante")
    } else when (state) {
        UpdateState.Checking -> uiText("Checking for a new version…", "Buscando una nueva versión…")
        UpdateState.UpToDate -> uiText("You have the latest version", "Tienes la versión más reciente")
        UpdateState.CheckFailed -> uiText("Could not check. Tap to retry", "No se pudo comprobar. Toca para reintentar")
        is UpdateState.Available -> uiText("Version ${state.metadata.version} available", "Versión ${state.metadata.version} disponible")
        is UpdateState.Downloading -> uiText("Downloading version ${state.version}", "Descargando la versión ${state.version}")
        is UpdateState.ReadyToInstall -> uiText("Update ready to install", "Actualización lista para instalar")
        else -> uiText("Installed version: ${BuildConfig.VERSION_NAME}", "Versión instalada: ${BuildConfig.VERSION_NAME}")
    }
    SettingsRow(
        title = uiText("Check for updates", "Buscar actualizaciones"),
        subtitle = subtitle,
        icon = Icons.Rounded.SystemUpdate,
        enabled = enabled,
        help = null,
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onCheck),
    ) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(20.dp), color = SettingsStyle.SageSoft, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = SettingsStyle.Chevron,
                modifier = Modifier.size(22.dp))
        }
    }
}
