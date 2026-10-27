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

package dev.sfg.numa.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.sfg.numa.mobile.model.PlaybackSnapshot

@Composable
fun CanopyReadout(
    playback: PlaybackSnapshot,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit = {},
    transition: dev.sfg.numa.mobile.model.TransitionMarker? = null,
    mixProgress: Float? = null,
    onArtworkBounds: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onClear: () -> Unit = {},
    animatedArtworkEnabled: Boolean = false,
    dismissEnabled: Boolean = true,
) {
    // Keeps the last track on screen while the pill animates out after the queue clears.
    val shown = remember { arrayOfNulls<PlaybackSnapshot>(1) }
    val lifetime = remember { ReadoutLifetime() }
    val generation = lifetime.observe(playback.currentTrack?.id)
    if (playback.currentTrack != null) shown[0] = playback
    AnimatedVisibility(
        visible = playback.currentTrack != null,
        enter = slideInVertically(spring(dampingRatio = 0.62f, stiffness = 380f)) { it } +
            fadeIn() + scaleIn(initialScale = 0.85f),
        exit = slideOutVertically { it / 2 } + fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        key(generation) {
            ReadoutBody(
                playback = shown[0] ?: playback,
                onOpen = onOpen,
                onToggle = onToggle,
                onNext = onNext,
                modifier = modifier,
                onPrevious = onPrevious,
                transition = transition,
                mixProgress = mixProgress,
                onArtworkBounds = onArtworkBounds,
                onClear = { if (lifetime.isCurrent(generation)) onClear() },
                animatedArtworkEnabled = animatedArtworkEnabled,
                dismissEnabled = dismissEnabled,
            )
        }
    }
}

@Composable
private fun ReadoutBody(
    playback: PlaybackSnapshot,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
    onPrevious: () -> Unit,
    transition: dev.sfg.numa.mobile.model.TransitionMarker?,
    mixProgress: Float?,
    onArtworkBounds: ((androidx.compose.ui.geometry.Rect) -> Unit)?,
    onClear: () -> Unit,
    animatedArtworkEnabled: Boolean,
    dismissEnabled: Boolean,
) {
    MiniPlayer(
        playback = playback,
        onTogglePlay = onToggle,
        onNext = onNext,
        onPrevious = onPrevious,
        onClick = onOpen,
        modifier = modifier,
        transition = transition,
        mixProgress = mixProgress,
        onArtworkBounds = onArtworkBounds,
        onClear = onClear,
        animatedArtworkEnabled = animatedArtworkEnabled,
        dismissEnabled = dismissEnabled,
    )
}
