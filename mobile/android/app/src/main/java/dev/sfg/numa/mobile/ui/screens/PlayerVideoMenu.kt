package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.app.MusicVideoState
import dev.sfg.numa.mobile.model.PlaybackTarget
import dev.sfg.numa.mobile.model.PlaybackTargetState
import dev.sfg.numa.mobile.playback.playerModeAvailability
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent

internal data class PlayerVideoOptions(
    val state: MusicVideoState,
    val onToggle: () -> Unit,
    val targets: PlaybackTargetState,
    val onLyrics: () -> Unit,
    val onQueue: () -> Unit,
)

internal val LocalPlayerVideoOptions = staticCompositionLocalOf<PlayerVideoOptions?> { null }
internal val LocalPlayerModeControlAllowed = staticCompositionLocalOf { true }

@Composable
internal fun PlayerVideoMenu() {
    val options = LocalPlayerVideoOptions.current ?: return
    val state = options.state
    val casting = rememberCurrentCastDeviceName() != null
    val availability = playerModeAvailability(
        videoAvailable = state.available,
        videoPlaying = state.playing,
        checking = state.checking,
        localOutput = options.targets.selected is PlaybackTarget.LocalPhone,
        controlAllowed = LocalPlayerModeControlAllowed.current,
        casting = casting,
    )
    val blockedByRoom = !LocalPlayerModeControlAllowed.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerModeButton(
            icon = Icons.Rounded.MusicNote,
            label = uiText("Song", "Canción"),
            selected = !state.playing,
            enabled = availability.song,
            unavailable = if (blockedByRoom) uiText("Only the room host can change the mode", "Solo el anfitrión puede cambiar el modo")
                else uiText("Unavailable on this output", "No disponible en esta salida"),
            onClick = { if (state.playing) options.onToggle() },
        )
        PlayerModeButton(
            icon = Icons.Rounded.Videocam,
            label = uiText("Video", "Vídeo"),
            selected = state.playing,
            enabled = availability.video,
            checking = state.checking && !blockedByRoom && !casting,
            unavailable = when {
                blockedByRoom -> uiText("Only the room host can change the mode", "Solo el anfitrión puede cambiar el modo")
                casting -> uiText("Video is unavailable while casting", "Vídeo no disponible durante Chromecast")
                state.checking -> uiText("Looking for video", "Buscando vídeo")
                else -> uiText("No video available for this song", "Sin vídeo disponible para esta canción")
            },
            onClick = { if (!state.playing) options.onToggle() },
        )
    }
}

@Composable
private fun PlayerModeButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    unavailable: String,
    checking: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val iconTint = if (!enabled) CanopyColors.Text.copy(alpha = 0.35f) else if (selected) accent else CanopyColors.Text
    val status = if (!enabled) unavailable else if (selected) uiText("Selected", "Seleccionado") else uiText("Available", "Disponible")
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
            .semantics { this.selected = selected; stateDescription = status },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(26.dp)) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp),
                    tint = iconTint)
                if (checking) {
                    CircularProgressIndicator(Modifier.align(Alignment.BottomEnd).size(10.dp), strokeWidth = 1.5.dp, color = accent)
                } else if (!enabled) {
                    Icon(Icons.Rounded.Block, contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomEnd).size(12.dp),
                        tint = iconTint)
                }
            }
            if (selected) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp)
                .size(4.dp).background(accent, CircleShape))
        }
    }
}
