package dev.sfg.numa.mobile.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

internal fun artworkResourceId(source: String, packageName: String): Int? =
    source.takeIf { it.startsWith("android.resource://$packageName/") }
        ?.substringAfterLast('/')?.toIntOrNull()?.takeIf { it > 0 }

internal fun resourceArtworkBitmap(context: Context, source: String, size: Int): Bitmap? {
    val resource = artworkResourceId(source, context.packageName) ?: return null
    return ContextCompat.getDrawable(context, resource)?.toBitmap(size, size, Bitmap.Config.ARGB_8888)
}
