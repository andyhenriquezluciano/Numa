package dev.sfg.numa.mobile.artwork

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/** App-local cover overrides; Google playlists and their remote images are never modified. */
class PlaylistCoverRepository(private val context: Context, private val scope: CoroutineScope,
    private val onError: (String) -> Unit) {
    private val directory = File(context.filesDir, "playlist-covers").apply { mkdirs() }
    private val backup = android.app.backup.BackupManager(context.applicationContext)
    private fun key(id: String) = MessageDigest.getInstance("SHA-256")
        .digest(coverPlaylistKey(id).toByteArray()).joinToString("") { "%02x".format(it) }
    private val legacyLikedKey = MessageDigest.getInstance("SHA-256").digest("LM".toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val mutable = MutableStateFlow(directory.listFiles().orEmpty().filter { it.extension == "cover" }
        .associate { it.nameWithoutExtension to Uri.fromFile(it).toString() })
    init {
        directory.listFiles().orEmpty().filter { it.extension == "default" }.forEach {
            mutable.value = mutable.value + (it.nameWithoutExtension to "")
        }
    }
    val covers = mutable.asStateFlow()
    fun reload() {
        mutable.value = directory.listFiles().orEmpty().filter { it.extension == "cover" || it.extension == "default" }
            .associate { it.nameWithoutExtension to if (it.extension == "default") "" else "${Uri.fromFile(it)}?v=${it.lastModified()}" }
    }
    fun cover(id: String, values: Map<String, String> = covers.value): String? {
        values[key(id)]?.let { return it }
        return if (coverPlaylistKey(id) == "FEmusic_liked_videos") values[legacyLikedKey] else null
    }
    fun set(id: String, uri: Uri) { scope.launch(Dispatchers.IO) {
        runCatching {
            val target = File(directory, "${key(id)}.cover")
            val temp = File(directory, "${key(id)}.tmp")
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                ?: error("No se pudo abrir la imagen")
            check(temp.length() > 0 && temp.renameTo(target)) { "No se pudo guardar la carátula" }
            File(directory, "${key(id)}.default").delete()
            mutable.value = mutable.value + (key(id) to "${Uri.fromFile(target)}?v=${target.lastModified()}")
            runCatching { backup.dataChanged() }
        }.onFailure { onError(it.message ?: "No se pudo guardar la carátula") }
    } }
    fun clear(id: String) { scope.launch(Dispatchers.IO) {
        val target = File(directory, "${key(id)}.cover")
        runCatching {
            check(!target.exists() || target.delete())
            File(directory, "${key(id)}.default").writeText("")
            mutable.value = mutable.value + (key(id) to "")
            runCatching { backup.dataChanged() }
        }.onFailure { onError("No se pudo restablecer la carátula") }
    } }
    fun purge(id: String) { scope.launch(Dispatchers.IO) {
        val k = key(id)
        File(directory, "$k.cover").delete()
        File(directory, "$k.default").delete()
        File(directory, "$k.tmp").delete()
        mutable.value = mutable.value - k
        runCatching { backup.dataChanged() }
    } }
}
