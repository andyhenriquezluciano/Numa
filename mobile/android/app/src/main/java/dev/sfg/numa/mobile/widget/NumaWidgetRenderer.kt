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

package dev.sfg.numa.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import dev.sfg.numa.connect.R
import dev.sfg.numa.connect.app.MainActivity
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient

internal object NumaWidgetRenderer {
    private var artworkUrl = ""
    private var cachedArtwork: Bitmap? = null
    suspend fun updateAll(context: Context, state: NumaWidgetState, client: OkHttpClient) =
        updateLocalized(dev.sfg.numa.mobile.settings.NumaLanguage.wrap(context), state, client)

    private suspend fun updateLocalized(
        context: Context,
        state: NumaWidgetState,
        client: OkHttpClient,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val playerIds = manager.getAppWidgetIds(
            ComponentName(context, NumaPlayerWidgetProvider::class.java)
        )
        val recentIds = manager.getAppWidgetIds(
            ComponentName(context, NumaRecentWidgetProvider::class.java)
        )
        if (playerIds.isEmpty() && recentIds.isEmpty()) return

        val url = state.currentTrack?.artworkUrl.orEmpty()
        fun render(artwork: Bitmap?) {
            playerIds.forEach { id ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                manager.updateAppWidget(id, playerViews(context, state, artwork, NUMA_PURPLE, height >= EXPANDED_HEIGHT_DP, height))
            }
            recentIds.forEach { id ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 176)
                manager.updateAppWidget(id, playerViews(context, state, artwork, NUMA_PURPLE, true, height))
            }
        }
        render(if (artworkUrl == url) cachedArtwork else null)
        if (url.isBlank() || artworkUrl == url && cachedArtwork != null) return
        val bitmap = WidgetArtworkLoader(context, client).load(url)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        artworkUrl = url; cachedArtwork = bitmap
        if (bitmap != null) render(bitmap)
    }

    private fun playerViews(
        context: Context,
        state: NumaWidgetState,
        artwork: Bitmap?,
        accent: Int,
        expanded: Boolean,
        heightDp: Int,
    ): RemoteViews {
        val layout = if (expanded) R.layout.widget_player_expanded else R.layout.widget_player_compact
        val views = RemoteViews(context.packageName, layout)
        val track = state.currentTrack
        val backgroundHeight = if (expanded) 176 else 88
        views.setImageViewBitmap(
            R.id.widget_background,
            widgetBackground(accent, BACKGROUND_WIDTH, backgroundHeight),
        )
        if (artwork != null) {
            views.setImageViewBitmap(
                R.id.widget_artwork,
                roundedArtwork(artwork, artworkPx(context, 56)),
            )
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_numa)
        }
        views.setTextViewText(
            R.id.widget_eyebrow,
            if (track == null) context.getString(R.string.widget_ready)
            else context.getString(R.string.widget_now_playing),
        )
        views.setTextViewText(
            R.id.widget_title,
            track?.title ?: context.getString(R.string.widget_nothing_playing),
        )
        views.setTextViewText(
            R.id.widget_artist,
            track?.artist ?: context.getString(R.string.widget_open_numa),
        )
        if (expanded) bindLyrics(context, views, state, heightDp)
        val progress = if (state.durationMs > 0) {
            ((state.positionMs.coerceIn(0, state.durationMs) * PROGRESS_MAX) / state.durationMs).toInt()
        } else {
            0
        }
        views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, progress, false)
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (state.isPlaying) R.string.pause else R.string.play),
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_previous,
            NumaPlayerWidgetProvider.actionIntent(
                context,
                NumaPlayerWidgetProvider.ACTION_PREVIOUS,
                REQUEST_PREVIOUS,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            NumaPlayerWidgetProvider.actionIntent(
                context,
                NumaPlayerWidgetProvider.ACTION_TOGGLE,
                REQUEST_TOGGLE,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            NumaPlayerWidgetProvider.actionIntent(
                context,
                NumaPlayerWidgetProvider.ACTION_NEXT,
                REQUEST_NEXT,
            ),
        )
        return views
    }

    private fun bindLyrics(context: Context, views: RemoteViews, state: NumaWidgetState, heightDp: Int) {
        val fallback = when {
            state.currentTrack == null -> R.string.widget_lyrics_idle
            state.lyricsLoading -> R.string.widget_lyrics_finding
            else -> R.string.widget_lyrics_unavailable
        }
        views.setInt(R.id.widget_lyrics_current, "setMaxLines", if (heightDp >= 160) 2 else 1)
        views.setTextViewText(R.id.widget_lyrics_current, state.currentLyric ?: context.getString(fallback))
        views.setTextViewText(R.id.widget_lyrics_upcoming, state.upcomingLyric.orEmpty())
        views.setViewVisibility(R.id.widget_lyrics_upcoming, if (heightDp < 144 || state.upcomingLyric.isNullOrBlank()) View.GONE else View.VISIBLE)
    }

    fun updateProgressAndLyrics(context: Context, state: NumaWidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        for (provider in listOf(NumaPlayerWidgetProvider::class.java, NumaRecentWidgetProvider::class.java)) {
            for (id in manager.getAppWidgetIds(ComponentName(context, provider))) {
                val expanded = provider == NumaRecentWidgetProvider::class.java ||
                    manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= EXPANDED_HEIGHT_DP
                val views = RemoteViews(context.packageName, if (expanded) R.layout.widget_player_expanded else R.layout.widget_player_compact)
                val progress = if (state.durationMs > 0) (state.positionMs.coerceIn(0, state.durationMs) * PROGRESS_MAX / state.durationMs).toInt() else 0
                views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, progress, false)
                if (expanded) bindLyrics(context, views, state, manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 176))
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Purple brand wash shared by both widget families. */
    private fun widgetBackground(accent: Int, width: Int, height: Int): Bitmap {
        val output = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = height.coerceAtMost(width) * 0.12f
        val baseAccent = ColorUtils.blendARGB(accent, CANOPY_NIGHT, 0.58f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(baseAccent, CANOPY_SURFACE, CANOPY_NIGHT),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = RadialGradient(
            width * 0.12f,
            height * 0.1f,
            width * 0.7f,
            ColorUtils.setAlphaComponent(accent, 118),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, paint)
        return output
    }

    // Device pixels for the on-screen size, capped to the loader's stored edge.
    private fun artworkPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt().coerceIn(dp, MAX_ARTWORK_PX)

    private fun roundedArtwork(source: Bitmap, size: Int): Bitmap {
        val edge = minOf(source.width, source.height)
        val left = (source.width - edge) / 2
        val top = (source.height - edge) / 2
        val square = Bitmap.createBitmap(source, left, top, edge, edge)
        val scaled = square.scale(size, size, true)
        val output = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(output).drawRoundRect(
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            size * 0.16f,
            size * 0.16f,
            paint,
        )
        return output
    }

    private const val EXPANDED_HEIGHT_DP = 112
    private const val BACKGROUND_WIDTH = 336
    private const val MAX_ARTWORK_PX = 384
    private const val PROGRESS_MAX = 1_000
    private const val REQUEST_OPEN = 900
    private const val REQUEST_PREVIOUS = 901
    private const val REQUEST_TOGGLE = 902
    private const val REQUEST_NEXT = 903
    private const val NUMA_PURPLE = 0xFFA78BFA.toInt()
    private const val CANOPY_NIGHT = 0xFF171120.toInt()
    private const val CANOPY_SURFACE = 0xFF2A203C.toInt()
}
