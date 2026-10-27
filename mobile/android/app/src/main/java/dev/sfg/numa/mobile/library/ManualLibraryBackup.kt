package dev.sfg.numa.mobile.library

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Base64
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.model.LibrarySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Portable, versioned metadata archive. Audio, tokens and account preferences are excluded. */
class ManualLibraryBackup(private val context: Context, private val graph: NumaGraph) {
    class Preview internal constructor(internal val library: LibrarySnapshot,
        internal val covers: Map<String, ByteArray>, internal val automatic: JSONObject) {
        val description: String get() = "${library.likedTracks.size} canciones favoritas, ${library.savedArtists.size} artistas, " +
            "${library.savedAlbums.size} álbumes, ${covers.size} carátulas y ${library.playCounts.size} contadores."
    }
    private val codec = LibraryCache(context)

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val library = codec.encode(graph.library.exportSnapshot())
        val covers = JSONObject()
        File(context.filesDir, "playlist-covers").listFiles().orEmpty().filter { validCoverName(it.name) }.forEach {
            require(it.length() <= MAX_IMAGE) { "Una carátula supera 8 MB." }
            covers.put(it.name, Base64.encodeToString(it.readBytes(), Base64.NO_WRAP))
        }
        val automatic = JSONObject()
        context.getSharedPreferences("automatic-playlist-covers", Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if ((key.startsWith("mode:") && value is Boolean) || (key.startsWith("art:") && value is String)) automatic.put(key, value)
        }
        val payload = JSONObject().put("library", library).put("covers", covers).put("automatic", automatic).toString()
        val document = JSONObject().put("format", "numa-library").put("version", 1)
            .put("createdAt", java.time.Instant.now().toString()).put("sha256", digest(payload)).put("payload", payload)
        val bytes = document.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_ARCHIVE) { "La copia supera 48 MB." }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("No se pudo crear la copia.")
        context.getSharedPreferences("manual-library-backup", Context.MODE_PRIVATE).edit()
            .putString("lastExport", java.time.Instant.now().toString()).apply()
    }

    suspend fun preview(uri: Uri): Preview = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_ARCHIVE + 1) }
            ?: error("No se pudo abrir la copia.")
        require(bytes.size <= MAX_ARCHIVE) { "El archivo supera 48 MB." }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getString("format") == "numa-library" && root.getInt("version") == 1) { "Formato de copia no compatible." }
        val payload = root.getString("payload")
        require(digest(payload) == root.getString("sha256")) { "La copia está dañada o incompleta." }
        val data = JSONObject(payload)
        val libraryRoot = data.getJSONObject("library")
        require(libraryRoot.getInt("version") == 1) { "Biblioteca no compatible." }
        val library = codec.decode(libraryRoot)
        require(library.listeningDays.all { runCatching { java.time.LocalDate.parse(it.date) }.isSuccess }) { "Fechas no válidas." }
        val coversRoot = data.getJSONObject("covers")
        val covers = coversRoot.keys().asSequence().associateWith { name ->
            require(validCoverName(name)) { "Nombre de carátula no válido." }
            Base64.decode(coversRoot.getString(name), Base64.DEFAULT).also {
                require(it.size <= MAX_IMAGE) { "Carátula demasiado grande." }
                if (name.endsWith(".cover")) {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size, options)
                    require(options.outWidth in 1..8192 && options.outHeight in 1..8192 &&
                        options.outWidth.toLong() * options.outHeight <= 16_777_216L) { "Imagen no válida o demasiado grande." }
                } else require(it.isEmpty())
            }
        }
        val automatic = data.getJSONObject("automatic")
        automatic.keys().forEach { key ->
            require((key.startsWith("mode:") && automatic.get(key) is Boolean) ||
                (key.startsWith("art:") && automatic.get(key) is String)) { "Preferencia no válida." }
        }
        Preview(library, covers, automatic)
    }

    suspend fun restore(preview: Preview) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "playlist-covers").apply { mkdirs() }
        preview.covers.forEach { (name, bytes) ->
            val stem = name.substringBeforeLast('.')
            if (!File(directory, "$stem.cover").exists() && !File(directory, "$stem.default").exists()) {
                val target = AtomicFile(File(directory, name))
                val output = target.startWrite()
                try { output.write(bytes); target.finishWrite(output) }
                catch (error: Exception) { target.failWrite(output); throw error }
            }
        }
        val preferences = context.getSharedPreferences("automatic-playlist-covers", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        preview.automatic.keys().forEach { key ->
            if (!preferences.contains(key)) when (val value = preview.automatic.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        check(editor.commit()) { "No se pudieron guardar las preferencias." }
        graph.library.importSnapshot(preview.library)
        graph.playlistCovers.reload()
        graph.automaticPlaylistCovers.reload()
        android.app.backup.BackupManager(context).dataChanged()
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun validCoverName(name: String) = name.matches(Regex("[a-f0-9]{64}\\.(cover|default)"))
    private companion object { const val MAX_ARCHIVE = 48 * 1024 * 1024; const val MAX_IMAGE = 8 * 1024 * 1024 }
}
