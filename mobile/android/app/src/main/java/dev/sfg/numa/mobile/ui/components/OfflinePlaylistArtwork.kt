package dev.sfg.numa.mobile.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Selected covers survive eviction of the image loader's temporary cache. */
@Composable
internal fun rememberOfflinePlaylistArtwork(source: String): String {
    val context = LocalContext.current.applicationContext
    val cached by produceState(source to source, source) {
        value = source to source
        if (!source.startsWith("https://") && !source.startsWith("http://")) return@produceState
        withContext(Dispatchers.IO) {
            try {
            val key = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val directory = File(context.filesDir, "playlist-artwork-cache").apply { mkdirs() }
            val target = File(directory, "$key.png")
            if (dev.sfg.numa.mobile.download.OfflineArtwork.isUsableCover(target, 720)) {
                value = source to target.toURI().toString()
                return@withContext
            }
            val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                .data(source).size(1200).allowHardware(false)
                .decoderFactory(coil3.decode.BitmapFactoryDecoder.Factory()).build())
            val image = result.image ?: return@withContext
            val temp = File.createTempFile(key, ".tmp", directory)
            try {
                temp.outputStream().use { image.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (temp.length() > 0 && temp.renameTo(target)) value = source to target.toURI().toString()
            } catch (_: java.io.IOException) {
                // The remote image remains available when local storage is full.
            } finally {
                temp.delete()
            }
            } catch (_: java.io.IOException) {
                // Keep the normal loader when persistent storage is unavailable.
            }
        }
    }
    return playlistArtworkSource(source, cached)
}

internal fun playlistArtworkSource(source: String, cached: Pair<String, String>): String =
    if (cached.first == source) cached.second else source
