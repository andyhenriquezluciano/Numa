/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

internal fun cloudCoverSampleSize(width: Int, height: Int, maximum: Int = 1024): Int {
    require(width > 0 && height > 0 && maximum > 0)
    var sample = 1
    while (maxOf(width, height) / sample > maximum) sample *= 2
    return sample
}

internal fun encodeCloudCover(file: File): ByteArray {
    val raw = file.takeIf { it.length() <= 1048576 }?.readBytes()
    if (raw != null && raw.size >= 12 && String(raw, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(raw, 8, 4, Charsets.US_ASCII) == "WEBP") return raw
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo preparar una carátula." }
    val options = BitmapFactory.Options().apply { inSampleSize = cloudCoverSampleSize(bounds.outWidth, bounds.outHeight) }
    val bitmap = checkNotNull(BitmapFactory.decodeFile(file.path, options))
    return try {
        ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it)) }.toByteArray()
            .also { require(it.size <= 1048576) { "Una carátula supera 1 MB." } }
    } finally { bitmap.recycle() }
}
