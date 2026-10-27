/*
 * Copyright (C) 2026 SFG545
 * Copyright (C) 2026 Convx Project contributors
 *
 * This file is part of Numa.
 *
 * Adapted from the floating back/share chrome and its scroll scrim in AlbumScreen
 * (app/src/main/kotlin/com/convx/music/ui/screens/AlbumScreen.kt) and ArtistScreen
 * (app/src/main/kotlin/com/convx/music/ui/screens/artist/ArtistScreen.kt) in Convx
 * v1.5.2, https://github.com/cosmictaserdev-creator/Convx, licensed under the GNU
 * General Public License version 3. It is combined with Numa under section 13 of
 * the GNU GPL v3 and GNU AGPL v3.
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

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.ui.glass.LocalGlass
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.theme.CanopyColors

/** 0 over the hero art, 1 once the list has scrolled 300dp past it. */
@Composable
fun rememberHeroScrimProgress(listState: LazyListState): State<Float> {
    val rangePx = with(LocalDensity.current) { 300.dp.toPx() }
    return remember(listState, rangePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / rangePx).coerceIn(0f, 1f)
        }
    }
}

/**
 * Floating detail chrome: a frosted back circle and an action pill over the hero,
 * backed by a scrim that darkens as [scrimProgress] rises so it stays legible over rows.
 */
@Composable
fun DetailFloatingChrome(
    onBack: () -> Unit,
    scrimProgress: State<Float>,
    modifier: Modifier = Modifier,
    palette: ArtworkPalette? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val topBrightness = palette?.top?.luminance()
    val isLightBackground = topBrightness != null && topBrightness >= 0.48f
    val targetShade = if (isLightBackground) 1f else 0f
    val shade by animateFloatAsState(targetShade, label = "TopChromeContrast")

    Box(modifier.fillMaxWidth()) {
        // Alpha is read in the layer block so scrolling never recomposes the chrome.
        // White icons on a white album cover: the scrim's whole reason to exist.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = scrimProgress.value }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.5f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .desktopButtonSurface(CircleShape)
                    .topChromeButtonContrast(CircleShape, shade)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = ripple(bounded = true),
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = uiText("Back", "Volver"),
                    tint = CanopyColors.Text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (actions != null) {
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .desktopButtonSurface(CircleShape)
                        .topChromeButtonContrast(CircleShape, shade)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    content = actions,
                )
            }
        }
    }
}

private fun Modifier.topChromeButtonContrast(
    shape: Shape,
    shade: Float,
): Modifier = drawWithContent {
    if (shade > 0.01f) {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline,
            Color(0xFF555E68).copy(alpha = shade * 0.45f),
        )
    }
    drawContent()
}

/** One icon inside the [DetailFloatingChrome] pill. */
@Composable
fun ChromeAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = CanopyColors.Text,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}
