package dev.sfg.numa.mobile.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dev.sfg.numa.mobile.artwork.highResolutionArtworkUrl
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.network.awaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Persistent covers shared by the player, keyguard and media-session metadata. */
object OfflineArtwork {
    internal const val COVER_PIXELS = 1024
    internal const val MIN_COVER_PIXELS = 320

    private fun file(context: Context, id: String): File = File(
        File(context.filesDir, "offline_covers"),
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".jpg",
    )

    fun localUri(context: Context, id: String): String? = file(context, id)
        .takeIf { isUsableCover(it) }?.let { Uri.fromFile(it).toString() }

    fun usableArtworkUrl(url: String, pixels: Int = COVER_PIXELS): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("file:")) return highResolutionArtworkUrl(trimmed, pixels)
        val path = runCatching { java.net.URI(trimmed).path }.getOrNull().orEmpty()
            .ifBlank { trimmed.removePrefix("file://").removePrefix("file:") }
        if (path.contains("/offline_covers/") || path.contains("\\offline_covers\\")) {
            return if (isUsableCover(File(path))) trimmed else ""
        }
        return trimmed
    }

    internal fun isUsableCover(file: File, minPixels: Int = MIN_COVER_PIXELS): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val (width, height) = imageDimensions(file)
        if (width < minPixels || height < minPixels) {
            file.delete()
            return false
        }
        return true
    }

    suspend fun bytes(context: Context, track: Track): ByteArray? = withContext(Dispatchers.IO) {
        val data = localUri(context, track.id)
            ?: usableArtworkUrl(track.artworkUrl, COVER_PIXELS).ifBlank { return@withContext null }
        val request = ImageRequest.Builder(context)
            .data(data)
            .size(COVER_PIXELS, COVER_PIXELS).allowHardware(false).build()
        val image = SingletonImageLoader.get(context).execute(request).image ?: return@withContext null
        val bitmap = image.toBitmap()
        if (bitmap.width < MIN_COVER_PIXELS || bitmap.height < MIN_COVER_PIXELS) return@withContext null
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            output.toByteArray()
        }
        val target = file(context, track.id)
        if (!isUsableCover(target)) writeCover(target, bytes)
        bytes
    }

    suspend fun save(
        context: Context,
        http: OkHttpClient,
        track: Track,
        fallbackUrl: String = "",
    ): String? = withContext(Dispatchers.IO) {
        localUri(context, track.id)?.let { return@withContext it }
        val remoteUrl = usableArtworkUrl(track.artworkUrl, COVER_PIXELS)
            .ifBlank { usableArtworkUrl(fallbackUrl, COVER_PIXELS) }
        if (!remoteUrl.startsWith("http")) return@withContext null
        http.newCall(Request.Builder().url(remoteUrl).build()).awaitResponse().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body.bytes()
            if (bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) return@use null
            val (width, height) = imageDimensions(bytes)
            if (width < MIN_COVER_PIXELS || height < MIN_COVER_PIXELS) return@use null
            val target = file(context, track.id)
            writeCover(target, bytes)
            Uri.fromFile(target).toString()
        }
    }

    internal fun imageDimensions(file: File): Pair<Int, Int> {
        val decoded = runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth to options.outHeight
        }.getOrDefault(0 to 0)
        if (decoded.first > 0 && decoded.second > 0) return decoded
        return runCatching { headerDimensions(file.readBytes()) }.getOrDefault(0 to 0)
    }

    internal fun imageDimensions(bytes: ByteArray): Pair<Int, Int> {
        val decoded = runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.outWidth to options.outHeight
        }.getOrDefault(0 to 0)
        if (decoded.first > 0 && decoded.second > 0) return decoded
        return headerDimensions(bytes)
    }

    private fun headerDimensions(bytes: ByteArray): Pair<Int, Int> {
        if (bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            val width = readInt32BE(bytes, 16)
            val height = readInt32BE(bytes, 20)
            return width to height
        }
        if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var index = 2
            while (index + 8 < bytes.size) {
                while (index < bytes.size && bytes[index] != 0xFF.toByte()) index++
                while (index < bytes.size && bytes[index] == 0xFF.toByte()) index++
                if (index >= bytes.size) break
                val marker = bytes[index].toInt() and 0xFF
                index++
                if (marker == 0xD9 || marker == 0xDA) break
                if (marker == 0x01 || marker in 0xD0..0xD7) continue
                if (index + 1 >= bytes.size) break
                val length = ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                if (length < 2 || index + length > bytes.size) break
                if (marker in 0xC0..0xC3 || marker in 0xC5..0xC7 || marker in 0xC9..0xCB || marker in 0xCD..0xCF) {
                    if (index + 6 < bytes.size) {
                        val height = ((bytes[index + 3].toInt() and 0xFF) shl 8) or (bytes[index + 4].toInt() and 0xFF)
                        val width = ((bytes[index + 5].toInt() and 0xFF) shl 8) or (bytes[index + 6].toInt() and 0xFF)
                        return width to height
                    }
                }
                index += length
            }
        }
        return 0 to 0
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun writeCover(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File.createTempFile("cover", ".tmp", target.parentFile)
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
        } finally {
            temp.delete()
        }
    }
}
