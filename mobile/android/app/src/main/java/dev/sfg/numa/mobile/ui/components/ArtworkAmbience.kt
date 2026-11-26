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

import android.graphics.Bitmap
import androidx.core.graphics.get
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dev.sfg.numa.mobile.artwork.highResolutionArtworkUrl
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

import androidx.compose.ui.graphics.lerp

/** Colours sampled from the cover so the player can extend the image beyond its edges. */
data class ArtworkPalette(
    val top: Color = CanopyColors.PlayerBackdrop,
    val bottom: Color = CanopyColors.PlayerBackdrop,
    val accent: Color = CanopyColors.PlayerBackdrop,
    val deep: Color = CanopyColors.PlayerBackdrop,
)

/**
 * The backdrop is tinted with the artwork's own colours
 * instead of fading to black. This samples the cover's top edge, bottom edge, and
 * most saturated tone so the backdrop reads as a continuation of the image.
 */
@Composable
fun rememberArtworkPalette(
    artworkUrl: String,
    visibleAspect: Float = 1f,
    renderedFrame: Bitmap? = null,
): ArtworkPalette {
    val context = LocalContext.current
    // Seeded from the cache so the second caller for a cover — the backdrop, the pill, the glass
    // tint and the player all ask for the same one — paints it immediately instead of decoding
    // and rescanning the bitmap and then fading in from black.
    var sampled by remember(artworkUrl) {
        mutableStateOf(cachedPalette(artworkUrl, visibleAspect) ?: ArtworkPalette())
    }

    // Sample either the live video frame or the static artwork image.
    LaunchedEffect(artworkUrl, visibleAspect, renderedFrame) {
        if (renderedFrame != null) {
            withContext(Dispatchers.Default) {
                runCatching { palette(renderedFrame, visibleAspect) }.getOrNull()
            }?.let { sampled = it }
            return@LaunchedEffect
        }

        if (artworkUrl.isBlank()) return@LaunchedEffect
        cachedPalette(artworkUrl, visibleAspect)?.let {
            sampled = it
            return@LaunchedEffect
        }
        resourceArtworkBitmap(context, artworkUrl, 128)?.let { bitmap ->
            val colors = withContext(Dispatchers.Default) { palette(bitmap, visibleAspect) }
            cachePalette(artworkUrl, visibleAspect, colors)
            sampled = colors
            return@LaunchedEffect
        }
        val formattedUrl = highResolutionArtworkUrl(artworkUrl, 128)
        val request = ImageRequest.Builder(context)
            .data(formattedUrl)
            .size(128, 128)
            .allowHardware(false)
            .build()

        var imageResult = SingletonImageLoader.get(context).execute(request).image
        if (imageResult == null && formattedUrl != artworkUrl) {
            val fallbackRequest = ImageRequest.Builder(context)
                .data(artworkUrl)
                .size(128, 128)
                .allowHardware(false)
                .build()
            imageResult = SingletonImageLoader.get(context).execute(fallbackRequest).image
        }

        val image = imageResult ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            runCatching { palette(image.toBitmap(), visibleAspect) }.getOrNull()
        }?.let {
            cachePalette(artworkUrl, visibleAspect, it)
            sampled = it
        }
    }

    val top by animateColorAsState(sampled.top, tween(700), label = "ArtworkTintTop")
    val bottom by animateColorAsState(sampled.bottom, tween(700), label = "ArtworkTintBottom")
    val accent by animateColorAsState(sampled.accent, tween(700), label = "ArtworkTintAccent")
    val deep by animateColorAsState(sampled.deep, tween(700), label = "ArtworkTintDeep")
    return ArtworkPalette(top, bottom, accent, deep)
}

/**
 * A cover's palette costs a decode plus two full passes over the bitmap, and several parts of the
 * app want the same answer for the same cover at once. Access-ordered and small: covers go out of
 * scope as fast as the queue moves, and holding four colours each makes this a few kilobytes.
 * Video frames are deliberately not cached, since every frame is a different image.
 */
private val paletteCache = object : LinkedHashMap<String, ArtworkPalette>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkPalette>) = size > 32
}

@Synchronized
private fun cachedPalette(artworkUrl: String, visibleAspect: Float): ArtworkPalette? =
    if (artworkUrl.isBlank()) null else paletteCache["$artworkUrl@$visibleAspect"]

@Synchronized
private fun cachePalette(artworkUrl: String, visibleAspect: Float, value: ArtworkPalette) {
    if (artworkUrl.isNotBlank()) paletteCache["$artworkUrl@$visibleAspect"] = value
}

private fun palette(bitmap: Bitmap, visibleAspect: Float): ArtworkPalette {
    val height = bitmap.height.coerceAtLeast(1)
    val width = bitmap.width.coerceAtLeast(1)

    // ContentScale.Crop centres the cover and trims whichever axis overflows: a cover
    // wider than the box loses its sides, a taller one loses its top and bottom rows.
    // Only the surviving rect reaches the screen, so only it may feed the tint.
    val sourceAspect = width.toFloat() / height
    val visibleWidth = (visibleAspect / sourceAspect).coerceIn(0.1f, 1f)
    val visibleHeight = (sourceAspect / visibleAspect).coerceIn(0.1f, 1f)

    val firstColumn = ((width * (1f - visibleWidth)) / 2f).toInt().coerceIn(0, width - 1)
    val lastColumn = (width - firstColumn).coerceIn(firstColumn + 1, width)
    val firstRow = ((height * (1f - visibleHeight)) / 2f).toInt().coerceIn(0, height - 1)
    val lastRow = (height - firstRow).coerceIn(firstRow + 1, height)

    // Thin bands: the tint has to match the pixels it abuts, not the whole half of the cover.
    val band = ((lastRow - firstRow) * EDGE_BAND).toInt().coerceAtLeast(1)
    val rawTop = edgeAverage(bitmap, firstRow, firstRow + band, firstColumn, lastColumn)
    val rawBottom = edgeAverage(bitmap, lastRow - band, lastRow, firstColumn, lastColumn)
    val top = rawTop.toneForBackdrop()
    val bottom = rawBottom.toneForBackdrop()
    val accent = dominantArtworkAccent(width, height) { x, y -> bitmap[x, y] }
    val deep = lerp(bottom, CanopyColors.Chrome, 0.40f)
    return ArtworkPalette(
        top = top,
        bottom = bottom,
        accent = accent.shade(0.92f),
        deep = deep,
    )
}

/** Averages a horizontal band of the cover across the columns the crop leaves visible. */
private fun edgeAverage(bitmap: Bitmap, firstRow: Int, lastRow: Int, firstColumn: Int, lastColumn: Int): Color {
    val step = ((lastColumn - firstColumn) / 24).coerceAtLeast(1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var samples = 0

    for (y in firstRow.coerceAtLeast(0) until lastRow.coerceAtMost(bitmap.height)) {
        var x = firstColumn
        while (x < lastColumn) {
            val pixel = bitmap[x, y]
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 32) {
                x += step
                continue
            }
            red += (pixel shr 16) and 0xFF
            green += (pixel shr 8) and 0xFF
            blue += pixel and 0xFF
            samples++
            x += step
        }
    }
    if (samples == 0) return CanopyColors.PlayerBackdrop
    return Color((red / samples) / 255f, (green / samples) / 255f, (blue / samples) / 255f)
}

private const val EDGE_BAND = 0.05f

private const val HUE_BINS = 24
private const val MIN_COLOR_COVERAGE = 0.12f

/** Uses the same coverage threshold as desktop so tiny color details cannot tint a gray cover. */
internal fun dominantArtworkAccent(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Color {
    val step = (min(width, height) / 40).coerceAtLeast(1)
    val weights = FloatArray(HUE_BINS)
    val reds = FloatArray(HUE_BINS)
    val greens = FloatArray(HUE_BINS)
    val blues = FloatArray(HUE_BINS)
    var visibleWeight = 0f
    var coloredWeight = 0f
    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            val pixel = pixelAt(x, y)
            val opacity = (pixel ushr 24) / 255f
            if (opacity < MIN_COLOR_COVERAGE) continue
            visibleWeight += opacity
            val red = (pixel ushr 16) and 0xFF
            val green = (pixel ushr 8) and 0xFF
            val blue = pixel and 0xFF
            val brightest = maxOf(red, green, blue)
            val chroma = brightest - minOf(red, green, blue)
            if (chroma < 28) continue
            coloredWeight += opacity
            val hueSector = when (brightest) {
                red -> (green - blue).toFloat() / chroma
                green -> 2f + (blue - red).toFloat() / chroma
                else -> 4f + (red - green).toFloat() / chroma
            }
            val bin = (hueSector * (HUE_BINS / 6f)).roundToInt().mod(HUE_BINS)
            val weight = opacity * (0.65f + 0.35f * chroma / 255f)
            weights[bin] += weight
            reds[bin] += red * weight
            greens[bin] += green * weight
            blues[bin] += blue * weight
        }
    }

    if (visibleWeight == 0f || coloredWeight < MIN_COLOR_COVERAGE * visibleWeight) {
        return Color(0xFFB8B8B8)
    }

    // Adjacent hue bins count together so a broad range of one color beats a small saturated detail.
    fun neighborhood(values: FloatArray, index: Int): Float =
        values[index] + 0.75f * (values[(index - 1).mod(HUE_BINS)] + values[(index + 1) % HUE_BINS])

    val winner = weights.indices.maxBy { neighborhood(weights, it) }
    val denominator = neighborhood(weights, winner) * 255f
    return Color(
        (neighborhood(reds, winner) / denominator).coerceIn(0f, 1f),
        (neighborhood(greens, winner) / denominator).coerceIn(0f, 1f),
        (neighborhood(blues, winner) / denominator).coerceIn(0f, 1f),
    )
}

private fun Color.shade(factor: Float): Color = Color(red * factor, green * factor, blue * factor)

private fun Color.toneForBackdrop(): Color {
    val maxChannel = max(red, max(green, blue))
    return if (maxChannel > 0.80f) {
        val factor = 0.80f / maxChannel
        Color(red * factor, green * factor, blue * factor, alpha)
    } else {
        this
    }
}
