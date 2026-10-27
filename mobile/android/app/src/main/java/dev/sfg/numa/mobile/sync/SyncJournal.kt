package dev.sfg.numa.mobile.sync

import android.content.Context
import android.util.AtomicFile
import dev.sfg.numa.mobile.library.LibraryBackupCodec
import dev.sfg.numa.mobile.model.LibrarySnapshot
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class SyncJournal(context: Context) {
    private val directory = File(context.noBackupFilesDir, "account-sync").apply { mkdirs() }
    private val install = AtomicFile(File(directory, "install.json"))
    private val identity = read(install).also { if (!it.has("client")) it.put("client", UUID.randomUUID().toString()) }
    val client: String get() = data.optString("client").ifBlank { identity.getString("client") }
    var owner: String = identity.optString("owner"); private set
    private var data = read(file(owner))
    init { if (!data.has("client")) data.put("client", UUID.randomUUID().toString()); write(install, identity); save() }
    private fun file(id: String) = AtomicFile(File(directory, "${accountKey(id)}.json"))
    @Synchronized fun bind(id: String, local: LibrarySnapshot): LibrarySnapshot {
        if (owner == id) return local
        if (owner.isNotBlank()) { data.put("snapshot", LibraryBackupCodec.encode(local)); save() }
        val adopt = owner.isBlank()
        val unclaimed = data
        owner = id; data = read(file(id))
        if (!data.has("client")) data.put("client", UUID.randomUUID().toString())
        if (adopt && !data.has("counters")) data.put("counters", unclaimed.optJSONObject("counters") ?: JSONObject())
        identity.put("owner", id); write(install, identity); save()
        return if (adopt) local else data.optJSONObject("snapshot")?.let(LibraryBackupCodec::decode) ?: LibrarySnapshot()
    }
    @Synchronized fun record(id: String, ms: Long, listen: Boolean) {
        data.put("counters", counters().record(id, ms, listen).json()); save()
    }
    @Synchronized fun counters() = SyncCounters.read(data.optJSONObject("counters") ?: JSONObject())
    @Synchronized fun base() = JSONObject((data.optJSONObject("base") ?: JSONObject()).toString())
    @Synchronized fun sequence(): Long {
        val value = data.optLong("seq") + 1; data.put("seq", value); save(); return value
    }
    @Synchronized fun migrationPending() = data.optLong("migration") != data.optLong("migrated")
    @Synchronized fun migrationVersion() = data.optLong("migration")
    @Synchronized fun migrate() { data.put("migration", data.optLong("migration") + 1); save() }
    @Synchronized fun acknowledged() = SyncCounters.read(data.optJSONObject("acknowledged") ?: JSONObject())
    @Synchronized fun complete(remote: JSONObject, local: LibrarySnapshot, sent: SyncCounters, migration: Long) {
        data.put("base", remote).put("snapshot", LibraryBackupCodec.encode(local)).put("acknowledged", sent.json()).put("migrated", migration); save()
    }
    fun accountDirectory(id: String) = File(directory, accountKey(id)).apply { mkdirs() }
    private fun save() = write(file(owner), data)
    private fun read(file: AtomicFile): JSONObject = if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) JSONObject()
        else JSONObject(file.openRead().bufferedReader().use { it.readText() })
    private fun write(file: AtomicFile, json: JSONObject) {
        val output = file.startWrite()
        try { output.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    companion object {
        fun accountKey(id: String) = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
