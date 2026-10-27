package dev.sfg.numa.mobile.sync

import android.content.Context
import android.util.AtomicFile
import dev.sfg.numa.mobile.NumaGraph
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

internal class CloudCoverStore(private val context: Context, private val graph: NumaGraph, private val transport: CloudTransport) {
    private val directory = File(context.filesDir, "playlist-covers").apply { mkdirs() }
    private val automatic = context.getSharedPreferences("automatic-playlist-covers", Context.MODE_PRIVATE)
    private val assets = File(context.noBackupFilesDir, "cloud-cover-assets").apply { mkdirs() }
    private var uploaded = File(assets, "unclaimed").apply { mkdirs() }
    private val deferred = mutableSetOf<String>()
    var incomplete: Boolean = false; private set
    private fun valid(name: String) = name.matches(Regex("[a-f0-9]{64}\\.(cover|default)"))
    fun bind(previous: File?, current: File, changing: Boolean) {
        uploaded = File(current, "uploaded").apply { mkdirs() }
        if (!changing) return
        previous?.let { old ->
            val archive = File(old, "covers").apply { mkdirs() }
            archive.listFiles().orEmpty().filter { valid(it.name) }.forEach { it.delete() }
            directory.listFiles().orEmpty().filter { valid(it.name) }.forEach { it.copyTo(File(archive, it.name), overwrite = true) }
            File(old, "automatic.json").writeText(automaticJson().toString())
        }
        directory.listFiles().orEmpty().filter { valid(it.name) }.forEach { it.delete() }
        File(current, "covers").listFiles().orEmpty().filter { valid(it.name) }.forEach { it.copyTo(File(directory, it.name), overwrite = true) }
        val saved = File(current, "automatic.json")
        applyAutomatic(if (saved.isFile) JSONObject(saved.readText()) else JSONObject())
        graph.playlistCovers.reload()
    }
    fun automaticJson(): JSONObject = JSONObject().also { json ->
        automatic.all.forEach { (key, value) ->
            if ((key.startsWith("mode:") && value is Boolean) || (key.startsWith("art:") && value is String)) json.put(key, value)
        }
    }
    private fun applyAutomatic(values: JSONObject) {
        val edit = automatic.edit()
        automatic.all.keys.filter { it.startsWith("mode:") || it.startsWith("art:") }.forEach(edit::remove)
        values.keys().forEach { key -> when (val value = values.get(key)) {
            is Boolean -> if (key.startsWith("mode:")) edit.putBoolean(key, value)
            is String -> if (key.startsWith("art:")) edit.putString(key, value)
        } }
        check(edit.commit()); graph.automaticPlaylistCovers.reload()
    }
    suspend fun styles(fallback: JSONObject = JSONObject()): JSONObject = JSONObject().also { styles ->
        deferred.clear(); incomplete = false
        directory.listFiles().orEmpty().filter { valid(it.name) }.forEach { file ->
            val key = file.nameWithoutExtension
            try {
                if (file.extension == "default") styles.put(key, JSONObject().put("kind", "default"))
                else {
                    val bytes = encodeCloudCover(file)
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    val cached = File(assets, hash)
                    val marker = File(uploaded, hash)
                    if (!marker.isFile) { transport.exchange("/sync/covers/$hash", bytes, "PUT", image = true); cached.writeBytes(bytes); marker.writeText("") }
                    styles.put(key, JSONObject().put("kind", "custom").put("asset", hash))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                deferred.add(key); incomplete = true
                fallback.optJSONObject(key)?.let { styles.put(key, it) }
            }
        }
    }
    suspend fun apply(sentStyles: JSONObject, styles: JSONObject, sentAutomatic: JSONObject, remoteAutomatic: JSONObject) {
        val wasIncomplete = incomplete
        val currentStyles = styles(sentStyles)
        incomplete = incomplete || wasIncomplete
        (currentStyles.keys().asSequence().toSet() + styles.keys().asSequence().toSet()).forEach { key ->
            if (!key.matches(Regex("[a-f0-9]{64}"))) return@forEach
            if (key in deferred) return@forEach
            if (canonicalSyncJson(currentStyles.opt(key)) != canonicalSyncJson(sentStyles.opt(key))) return@forEach
            if (canonicalSyncJson(currentStyles.opt(key)) == canonicalSyncJson(styles.opt(key))) return@forEach
            val style = styles.optJSONObject(key)
            val target = File(directory, "$key.cover"); val marker = File(directory, "$key.default")
            when (style?.optString("kind")) {
                "custom" -> {
                    val digest = style.getString("asset"); require(digest.matches(Regex("[a-f0-9]{64}")))
                    val local = File(assets, digest)
                    val bytes = if (local.isFile) local.readBytes() else transport.exchange("/sync/covers/$digest")
                    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    check(actual == digest) { "La carátula recibida está dañada." }
                    local.writeBytes(bytes)
                    File(uploaded, digest).writeText("")
                    val atomic = AtomicFile(target); val out = atomic.startWrite()
                    try { out.write(bytes); atomic.finishWrite(out) } catch (error: Exception) { atomic.failWrite(out); throw error }
                    marker.delete()
                }
                "default" -> { target.delete(); marker.writeText("") }
                null -> { target.delete(); marker.delete() }
            }
        }
        val merged = mergeJsonMap(sentAutomatic, automaticJson(), remoteAutomatic)
        applyAutomatic(merged); graph.playlistCovers.reload()
    }
}

internal fun mergeJsonMap(base: JSONObject, local: JSONObject, remote: JSONObject): JSONObject = JSONObject(remote.toString()).also { result ->
    (base.keys().asSequence().toSet() + local.keys().asSequence().toSet()).forEach { key ->
        if (canonicalSyncJson(base.opt(key)) != canonicalSyncJson(local.opt(key))) {
            if (local.has(key)) result.put(key, local.get(key)) else result.remove(key)
        }
    }
}
