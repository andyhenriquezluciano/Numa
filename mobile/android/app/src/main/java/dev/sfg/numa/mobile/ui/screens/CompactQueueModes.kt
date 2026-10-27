package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.RepeatMode
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun CompactQueueModes(repeat: RepeatMode, onRepeat: (() -> Unit)?, autoplay: Boolean, onAutoplay: ((Boolean) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        onRepeat?.let { IconButton(onClick = it, modifier = Modifier.size(48.dp)) {
            Icon(if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                uiText("Repeat", "Repetir"), tint = modeTint(repeat != RepeatMode.OFF))
        } }
        onAutoplay?.let { IconButton(onClick = { it(!autoplay) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.AllInclusive, uiText("Autoplay", "Reproducción automática"), tint = modeTint(autoplay))
        } }
    }
}

private fun modeTint(enabled: Boolean) = if (enabled) Color(0xFFB49AFF) else Color.White.copy(alpha = 0.65f)
