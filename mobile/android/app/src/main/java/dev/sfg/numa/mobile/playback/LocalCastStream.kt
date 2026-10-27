/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */

package dev.sfg.numa.mobile.playback

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream

internal fun serveLocalCast(
    context: Context, output: BufferedOutputStream, uri: String, mime: String,
    range: String?, head: Boolean,
) {
    val parsed = Uri.parse(uri)
    val descriptor = context.contentResolver.openAssetFileDescriptor(parsed, "r")
        ?: error("Local track is unavailable")
    descriptor.use { asset ->
        val length = asset.length.takeIf { it >= 0 }
            ?: asset.parcelFileDescriptor.statSize
        val requested = if (length > 0) castByteRange(range, length) else null
        val start = requested?.first ?: 0L
        val end = requested?.last ?: (length - 1)
        val count = (end - start + 1).coerceAtLeast(0)
        val status = if (requested == null) "200 OK" else "206 Partial Content"
        val headers = buildString {
            append("HTTP/1.1 $status\r\nContent-Type: ${mime.ifBlank { "application/octet-stream" }}\r\n")
            if (length >= 0) append("Content-Length: $count\r\nAccept-Ranges: bytes\r\n")
            if (requested != null) append("Content-Range: bytes $start-$end/$length\r\n")
            append("Cache-Control: no-store\r\nConnection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.US_ASCII))
        if (!head && (count > 0 || length < 0)) asset.createInputStream().use { input ->
            var skip = start
            while (skip > 0) {
                val skipped = input.skip(skip)
                if (skipped > 0) skip -= skipped else {
                    if (input.read() < 0) error("Local track ended before requested range")
                    skip--
                }
            }
            val buffer = ByteArray(64 * 1024)
            var remaining = if (length < 0) Long.MAX_VALUE else count
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }
}

internal fun castByteRange(value: String?, length: Long): LongRange? {
    if (value == null || !value.startsWith("bytes=") || length <= 0) return null
    val bounds = value.removePrefix("bytes=").substringBefore(',').split('-', limit = 2)
    if (bounds.size != 2) return null
    if (bounds[0].isBlank()) {
        val suffix = bounds[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return (length - suffix).coerceAtLeast(0)..(length - 1)
    }
    val start = bounds[0].toLongOrNull() ?: return null
    val end = bounds[1].toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
    if (start !in 0 until length || end < start) return null
    return start..end
}
