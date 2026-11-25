// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.sfg.numa.mobile.ui.foldable.isFoldableOrWideLayout
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.currentRosePine
import kotlinx.coroutines.delay

internal enum class SkeletonLayout { Text, Tracks, NumberedTracks, Playlist, Album, Lyrics }

@Composable
internal fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    layout: SkeletonLayout = SkeletonLayout.Text,
    message: String = uiText("Loading…", "Cargando…"),
    lyricLineHeight: Dp = 31.dp,
    collectionTitle: String = "",
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    if (!visible) return

    val rose = currentRosePine()
    val ink = rose?.Text ?: MaterialTheme.colorScheme.onSurface
    val fill = ink.copy(alpha = if (rose == null) 0.13f else 0.12f)
    val detailFill = ink.copy(alpha = if (rose == null) 0.20f else 0.19f)
    val spacing = when (layout) {
        SkeletonLayout.Text -> 9.dp
        else -> 0.dp
    }
    Column(
        modifier.semantics { contentDescription = message },
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        when (layout) {
            SkeletonLayout.Tracks, SkeletonLayout.NumberedTracks, SkeletonLayout.Playlist, SkeletonLayout.Album -> {
                if (layout == SkeletonLayout.Playlist || layout == SkeletonLayout.Album) {
                    CollectionHeroSkeleton(collectionTitle, fill, detailFill)
                }
                for (fraction in trackWidths) {
                    val artwork = layout == SkeletonLayout.Tracks || layout == SkeletonLayout.Playlist
                    val gutter = if (layout == SkeletonLayout.Playlist || layout == SkeletonLayout.Album) 24.dp else 16.dp
                    TrackSkeleton(fraction, artwork, fill, detailFill, gutter)
                }
            }
            SkeletonLayout.Lyrics -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val activeLine = maxHeight * 0.40f
                Column(Modifier.fillMaxWidth().padding(top = (activeLine - lyricLineHeight * 3).coerceAtLeast(0.dp))) {
                    for ((index, widths) in lyricWidths.withIndex()) {
                        Column(Modifier.padding(vertical = 11.dp)) {
                            for (fraction in widths) {
                                Box(Modifier.fillMaxWidth().height(lyricLineHeight), contentAlignment = Alignment.CenterStart) {
                                    Spacer(
                                        Modifier.fillMaxWidth(fraction).height(lyricLineHeight * 0.66f)
                                            .background(if (index == 1) detailFill else fill, RoundedCornerShape(5.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SkeletonLayout.Text -> {
                for (fraction in textWidths) {
                    Box(
                        Modifier.fillMaxWidth().height(36.dp).background(fill, RoundedCornerShape(9.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Spacer(Modifier.fillMaxWidth(fraction).height(8.dp).background(detailFill, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionHeroSkeleton(title: String, fill: Color, detailFill: Color) {
    var titleLayout by remember(title) { mutableStateOf<TextLayoutResult?>(null) }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = HeroGutter), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title.ifBlank { uiText("Playlist title", "Título de la lista") },
                style = collectionTitleStyle(),
                color = Color.Transparent,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { titleLayout = it },
                modifier = Modifier.clearAndSetSemantics {}.drawBehind {
                    val result = titleLayout ?: return@drawBehind
                    for (line in 0 until result.lineCount) {
                        val left = result.getLineLeft(line)
                        val right = result.getLineRight(line)
                        val top = result.getLineTop(line)
                        val lineHeight = result.getLineBottom(line) - top
                        drawRoundRect(
                            color = detailFill,
                            topLeft = Offset(left, top + lineHeight * 0.16f),
                            size = Size((right - left).coerceAtLeast(0f), lineHeight * 0.68f),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.fillMaxWidth(0.26f).height(15.dp).background(fill, RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.fillMaxWidth(0.56f).height(12.dp).background(fill, RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(48.dp).background(fill, MaterialTheme.shapes.small))
                Spacer(Modifier.size(64.dp).background(detailFill, MaterialTheme.shapes.medium))
                Spacer(Modifier.size(48.dp).background(fill, MaterialTheme.shapes.small))
            }
        }
    }
    if (isFoldableOrWideLayout()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val size = minOf(240.dp, LocalConfiguration.current.screenHeightDp.dp * 0.55f)
            Spacer(Modifier.size(size).background(fill, RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(24.dp))
            Box(Modifier.weight(1f)) { body() }
        }
    } else {
        DetailHeroFrame(
            artworkUrl = "",
            description = "",
            artworkPlaceholder = { Box(Modifier.fillMaxSize().background(fill)) },
        ) { body() }
    }
}

@Composable
private fun TrackSkeleton(fraction: Float, artwork: Boolean, fill: Color, detailFill: Color, gutter: Dp) {
    Row(
        Modifier.fillMaxWidth().height(if (artwork) 68.dp else 58.dp).padding(horizontal = gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artwork) {
            Spacer(Modifier.size(46.dp).background(fill, RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
        } else {
            Box(Modifier.width(40.dp)) {
                Spacer(Modifier.size(10.dp, 6.dp).background(fill, RoundedCornerShape(3.dp)))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.widthIn(max = 310.dp).fillMaxWidth(fraction).height(10.dp).background(detailFill, RoundedCornerShape(4.dp)))
            Spacer(Modifier.widthIn(max = 180.dp).fillMaxWidth(fraction * 0.59f).height(7.dp).background(fill, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.width(24.dp).height(7.dp).background(fill, RoundedCornerShape(3.dp)))
        Row(Modifier.width(36.dp), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
            repeat(3) { Spacer(Modifier.size(3.dp).background(fill, RoundedCornerShape(2.dp))) }
        }
    }
}

private val trackWidths = floatArrayOf(0.58f, 0.74f, 0.46f)
private val lyricWidths = listOf(
    floatArrayOf(0.89f, 0.61f),
    floatArrayOf(0.77f),
    floatArrayOf(0.94f, 0.78f, 0.51f),
    floatArrayOf(0.84f),
)
private val textWidths = floatArrayOf(0.60f, 0.76f, 0.50f)
