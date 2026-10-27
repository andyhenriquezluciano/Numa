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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import dev.sfg.numa.mobile.download.OfflineArtwork
import dev.sfg.numa.mobile.library.readBounded
import dev.sfg.numa.mobile.network.awaitResponse
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

internal class WidgetArtworkLoader(
    context: Context,
    client: OkHttpClient,
) {
    private val client = client.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    private val directory = context.cacheDir.resolve("widget-artwork").apply(File::mkdirs)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val resolved = OfflineArtwork.usableArtworkUrl(url, 600)
        if (resolved.isBlank()) return@withContext null
        if (resolved.startsWith("file://")) {
            return@withContext BitmapFactory.decodeFile(android.net.Uri.parse(resolved).path)?.fitInside(MAX_ARTWORK_EDGE)
        }
        val cached = directory.resolve("${resolved.sha256()}.webp")
        BitmapFactory.decodeFile(cached.path)?.let { return@withContext it }

        val bytes = runCatching {
            client.newCall(Request.Builder().url(resolved).build()).awaitResponse().use { response ->
                if (!response.isSuccessful) return@use null
                if (response.body.contentLength() > MAX_DOWNLOAD_BYTES) return@use null
                response.body.byteStream().use { it.readBounded(MAX_DOWNLOAD_BYTES + 1) }.takeIf { it.size <= MAX_DOWNLOAD_BYTES }
            }
        }.getOrNull() ?: return@withContext null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val scaled = decoded.fitInside(MAX_ARTWORK_EDGE)
        runCatching {
            cached.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, output)
            }
            trimCache()
        }
        scaled
    }

    private fun trimCache() {
        directory.listFiles().orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_CACHE_FILES)
            .forEach(File::delete)
    }

    private fun Bitmap.fitInside(maxEdge: Int): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= maxEdge) return this
        val factor = maxEdge.toFloat() / largest
        return scale(
            (width * factor).toInt().coerceAtLeast(1),
            (height * factor).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ARTWORK_EDGE = 384
        const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
        const val MAX_CACHE_FILES = 24
    }
}
