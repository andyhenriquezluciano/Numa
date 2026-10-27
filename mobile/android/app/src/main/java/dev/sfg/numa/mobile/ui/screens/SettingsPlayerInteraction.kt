package dev.sfg.numa.mobile.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TouchApp
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.lockscreen.LockScreenPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun PlayerInteractionSettings(settings: NumaSettings, onSettings: (NumaSettings) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permission by remember(context) { mutableStateOf(LockScreenPermission.granted(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = LockScreenPermission.granted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun requestPermission() {
        if (!LockScreenPermission.open(context)) {
            android.widget.Toast.makeText(context,
                uiText("Open Android settings and allow NÜMA to appear on top.",
                    "Abre los ajustes de Android y permite que NÜMA aparezca encima."), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    SectionLabel("Controles y bloqueo", 8)
    SettingsPanel(index = 7) {
        ToggleRow(
            title = uiText("NÜMA lock-screen player", "Reproductor de NÜMA al bloquear"),
            subtitle = uiText("Artwork and controls when locked.", "Carátula y controles al bloquear."),
            icon = Icons.Rounded.Image,
            checked = settings.lockScreenPlayer && permission,
            onChecked = { enabled ->
                onSettings(settings.copy(lockScreenPlayer = enabled))
                permission = LockScreenPermission.granted(context)
                if (enabled && !permission) requestPermission()
            },
        )
        if (settings.lockScreenPlayer && !permission) {
            ActionRow(
                title = uiText("Enable Android permission", "Activar permiso en Android"),
                subtitle = uiText("Select NÜMA and allow it to appear on top of other apps.",
                    "Selecciona NÜMA y permite aparecer encima de otras aplicaciones."),
                icon = Icons.Rounded.Image,
                onClick = ::requestPermission,
            )
        }
        PanelDivider()
        ToggleRow(
            title = "Gestos del reproductor",
            subtitle = "Desliza para cambiar de canción; toca para dar Me gusta",
            icon = Icons.Rounded.TouchApp,
            checked = settings.playerGesturesEnabled,
            onChecked = { onSettings(settings.copy(playerGesturesEnabled = it)) },
        )
    }
}
