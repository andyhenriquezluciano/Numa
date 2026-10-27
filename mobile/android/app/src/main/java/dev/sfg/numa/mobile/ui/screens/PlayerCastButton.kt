package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import androidx.mediarouter.app.MediaRouteButton

@Composable
internal fun PlayerCastButton() {
    val device = rememberCurrentCastDeviceName()
    var supported by remember { mutableStateOf(true) }
    var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }
    val label = if (device == null) uiText("Connect with Chromecast", "Conectar con Chromecast")
        else uiText("Chromecast: $device", "Chromecast: $device")
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        ChromecastMediaRouteButton(Modifier.size(1.dp).alpha(0f)) { button ->
            button.setAlwaysVisible(true)
            button.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            routeButton = button
            supported = button.isEnabled
        }
        IconButton(onClick = { routeButton?.showDialog() }, enabled = supported && routeButton != null, modifier = Modifier.fillMaxSize()) {
            Icon(if (device == null) Icons.Rounded.Cast else Icons.Rounded.CastConnected,
                contentDescription = label, modifier = Modifier.size(24.dp),
                tint = if (!supported) CanopyColors.Muted else if (device != null) LocalAccent.current else CanopyColors.Text)
        }
    }
}
