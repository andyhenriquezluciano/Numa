package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.theme.playbackAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import dev.sfg.numa.mobile.ui.components.RoundedControlButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.RepeatMode
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.currentRosePine

@Composable
internal fun CompactQueueModes(repeat: RepeatMode, onRepeat: (() -> Unit)?, autoplay: Boolean, onAutoplay: ((Boolean) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (onRepeat != null) QueueModeButton(
            if (repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            uiText("Repeat", "Repetir"),
            active = repeat != RepeatMode.OFF,
            onClick = onRepeat,
        )
        if (onAutoplay != null) QueueModeButton(
            Icons.Rounded.AllInclusive,
            uiText("Autoplay", "Reproducción automática"),
            active = autoplay,
            onClick = { onAutoplay(!autoplay) },
            automatic = true,
        )
    }
}

@Composable
private fun QueueModeButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, automatic: Boolean = false) {
    val rose = currentRosePine()
    val activeTint = if (automatic) rose?.InfoInk else rose?.AccentInk
    val tint = if (active) activeTint ?: playbackAccent() else rose?.SecondaryText ?: Color.White.copy(alpha = 0.65f)
    val fill = when {
        rose == null -> tint.copy(alpha = if (active) 0.18f else 0.08f)
        !active -> rose.HighlightLow
        automatic -> rose.InfoSurface
        else -> rose.Highlight
    }
    val tile = Modifier.size(34.dp, 38.dp).background(fill, RoundedCornerShape(9.dp))
    RoundedControlButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(tile, contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint)
        }
    }
}
