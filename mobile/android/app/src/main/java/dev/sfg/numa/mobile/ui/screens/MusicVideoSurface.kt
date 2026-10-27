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

package dev.sfg.numa.mobile.ui.screens

import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The picture on a TextureView laid out here, so black bars baked into the video can be cropped
 * away. [fill] zooms until no bars remain and trims the edges of the picture.
 */
@Composable
internal fun MusicVideoSurface(player: Player?, modifier: Modifier = Modifier, fill: Boolean = false,
    onAspectRatio: ((Float) -> Unit)? = null) {
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    var videoSize by remember(player) { mutableStateOf(player?.videoSize ?: VideoSize.UNKNOWN) }
    var buffering by remember(player) { mutableStateOf(player?.playbackState == Player.STATE_BUFFERING) }
    var playWhenReady by remember(player) { mutableStateOf(player?.playWhenReady == true) }
    var showLoading by remember(player) { mutableStateOf(false) }
    var mediaId by remember(player) { mutableStateOf(player?.currentMediaItem?.mediaId) }
    var ambient by remember(mediaId) { mutableStateOf<ImageBitmap?>(null) }
    var crop by remember(player) { mutableStateOf<ContentRect?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }

            override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) {
                playWhenReady = value
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                crop = null
                mediaId = item?.mediaId
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    DisposableEffect(player, textureView) {
        val view = textureView
        if (player != null && view != null) player.setVideoTextureView(view)
        onDispose { if (player != null && view != null) player.clearVideoTextureView(view) }
    }
    LaunchedEffect(buffering, playWhenReady) {
        showLoading = false
        if (buffering && playWhenReady) {
            delay(200)
            showLoading = true
        }
    }
    // Lock the first useful frame's picture bounds for this media item.
    LaunchedEffect(textureView, videoSize, mediaId) {
        val view = textureView ?: return@LaunchedEffect
        if (videoSize.width <= 0 || videoSize.height <= 0) return@LaunchedEffect
        val width = VideoLetterbox.SAMPLE_WIDTH
        val height = max(1, width * videoSize.height / videoSize.width)
        val pixels = IntArray(width * height)
        while (true) {
            delay(100)
            if (!view.isAvailable) continue
            val frame = view.getBitmap(width, height) ?: continue
            frame.getPixels(pixels, 0, width, 0, 0, width, height)
            frame.recycle()
            val sample = VideoLetterbox.contentRect(pixels, width, height) ?: continue
            crop = VideoLetterbox.accumulate(null, sample)
            break
        }
    }

    val target = crop ?: ContentRect.Full
    val left = target.left
    val top = target.top
    val right = target.right
    val bottom = target.bottom
    val zoom = if (fill) 1f else 0f
    LaunchedEffect(videoSize, target) {
        val width = videoSize.width * videoSize.pixelWidthHeightRatio * target.width
        val height = videoSize.height * target.height
        if (width > 0f && height > 0f) onAspectRatio?.invoke(width / height)
    }

    LaunchedEffect(textureView, mediaId, playWhenReady) {
        val view = textureView ?: return@LaunchedEffect
        do {
            if (view.isAvailable && videoSize.width > 0 && videoSize.height > 0) {
                val frame = view.getBitmap(128, max(1, 128 * videoSize.height / videoSize.width))
                if (frame != null) ambient = frame.asImageBitmap()
            }
            delay(500)
        } while (playWhenReady)
    }

    Box(modifier.clipToBounds().background(Color.Black)) {
        ambient?.let { image ->
            Image(image, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f; alpha = 0.65f }.blur(32.dp))
        }
        AndroidView(
            factory = { context -> TextureView(context).apply {
                isClickable = false
                isFocusable = false
                textureView = this
            } },
            modifier = Modifier.graphicsLayer {
                clip = true
                shape = GenericShape { size, _ ->
                    val width = videoSize.width * videoSize.pixelWidthHeightRatio * target.width
                    val height = videoSize.height * target.height
                    if (width <= 0 || height <= 0) addRect(Rect(0f, 0f, size.width, size.height))
                    else {
                        val scale = if (fill) max(size.width / width, size.height / height) else min(size.width / width, size.height / height)
                        val x = (size.width - width * scale) / 2
                        val y = (size.height - height * scale) / 2
                        addRect(Rect(x, y, x + width * scale, y + height * scale))
                    }
                }
            }.layout { measurable, constraints ->
                val boxWidth = constraints.maxWidth
                val boxHeight = constraints.maxHeight
                val frameWidth = videoSize.width * videoSize.pixelWidthHeightRatio
                val frameHeight = videoSize.height.toFloat()
                if (frameWidth <= 0f || frameHeight <= 0f || boxWidth == Constraints.Infinity || boxHeight == Constraints.Infinity) {
                    val placeable = measurable.measure(constraints)
                    return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                // Scale so the cropped picture fits the box, or covers it when filling.
                val pictureWidth = frameWidth * (right - left)
                val pictureHeight = frameHeight * (bottom - top)
                val fit = min(boxWidth / pictureWidth, boxHeight / pictureHeight)
                val cover = max(boxWidth / pictureWidth, boxHeight / pictureHeight)
                val scale = fit + (cover - fit) * zoom
                val viewWidth = (frameWidth * scale).roundToInt()
                val viewHeight = (frameHeight * scale).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(viewWidth, viewHeight))
                // Centre the picture's middle, not the frame's, on the box.
                val x = boxWidth / 2f - (left + right) / 2f * viewWidth
                val y = boxHeight / 2f - (top + bottom) / 2f * viewHeight
                layout(boxWidth, boxHeight) { placeable.place(x.roundToInt(), y.roundToInt()) }
            },
        )
        if (showLoading && buffering && playWhenReady) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = Color.White, strokeWidth = 3.dp)
        }
    }
}

