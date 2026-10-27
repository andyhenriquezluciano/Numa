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

package dev.sfg.numa.mobile.library

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dev.sfg.numa.mobile.model.Album
import dev.sfg.numa.mobile.model.Artist
import dev.sfg.numa.mobile.model.CatalogJson
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.ListeningDay
import dev.sfg.numa.mobile.model.Playlist
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Atomic offline cache for user-owned library metadata and listening history. */
class LibraryCache(context: Context) {
    private val backup = android.app.backup.BackupManager(context.applicationContext)
    private val file = AtomicFile(File(context.filesDir, "library-cache.json"))
    private val visibility = context.getSharedPreferences("library-playlist-visibility", Context.MODE_PRIVATE)

    fun saveHiddenPlaylists(ids: Set<String>) {
        visibility.edit().putStringSet("hidden", ids.toSet()).apply()
    }

    fun load(): LibrarySnapshot {
        val hidden = visibility.getStringSet("hidden", emptySet()).orEmpty().map(::libraryPlaylistId).toSet()
        if (!file.baseFile.exists()) return LibrarySnapshot(hiddenPlaylistIds = hidden)
        return try {
            val root = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            LibraryBackupCodec.decode(root, hidden)
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring unreadable library cache", error)
            LibrarySnapshot(hiddenPlaylistIds = hidden)
        }
    }

    @Synchronized
    fun save(snapshot: LibrarySnapshot) {
        val root = encode(snapshot)
        var output: FileOutputStream? = file.startWrite()
        try {
            output?.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
            output = null
            runCatching { backup.dataChanged() }
        } catch (error: Exception) {
            output?.let(file::failWrite)
            Log.e(TAG, "Could not save library cache", error)
        }
    }

    internal fun encode(snapshot: LibrarySnapshot) = LibraryBackupCodec.encode(snapshot)
    internal fun decode(root: JSONObject) = LibraryBackupCodec.decode(root)

    private companion object {
        const val TAG = "LibraryCache"
    }
}
