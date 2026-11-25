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
import dev.sfg.numa.mobile.model.LibrarySnapshot
import org.json.JSONObject
import java.io.File

/** Atomic offline cache for user-owned library metadata and listening history. */
class LibraryCache(context: Context) {
    private val backup = android.app.backup.BackupManager(context.applicationContext)
    private val metadataFile = AtomicFile(File(context.filesDir, "library-cache.json"))
    private val historyFile = AtomicFile(File(context.filesDir, "library-history.json"))
    private var savedMetadata: LibrarySnapshot? = null
    private var savedHistory: LibrarySnapshot? = null
    private val visibility = context.getSharedPreferences("library-playlist-visibility", Context.MODE_PRIVATE)

    fun saveHiddenPlaylists(ids: Set<String>) {
        visibility.edit().putStringSet("hidden", ids.toSet()).apply()
    }

    @Synchronized
    fun load(): LibrarySnapshot {
        val hidden = visibility.getStringSet("hidden", emptySet()).orEmpty().map(::libraryPlaylistId).toSet()
        val history = historyFile.read()
        val snapshot = try {
            LibraryBackupCodec.decodeLocal(metadataFile.read() ?: JSONObject(), history, hidden)
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring unreadable library cache", error)
            LibrarySnapshot(hiddenPlaylistIds = hidden)
        }
        savedMetadata = snapshot.metadata()
        return snapshot
    }

    @Synchronized
    fun save(snapshot: LibrarySnapshot) {
        val metadata = snapshot.metadata()
        val history = snapshot.history()
        var changed = false
        try {
            // Write the new history before replacing any v1 file that still embeds it.
            if (history != savedHistory) {
                historyFile.write(LibraryBackupCodec.encodeHistory(history))
                savedHistory = history
                changed = true
            }
            if (metadata != savedMetadata) {
                metadataFile.write(LibraryBackupCodec.encodeMetadata(metadata))
                savedMetadata = metadata
                changed = true
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not save library cache", error)
        }
        if (changed) runCatching { backup.dataChanged() }
    }

    private fun LibrarySnapshot.metadata() = LibrarySnapshot(
        likedTracks = likedTracks, savedAlbums = savedAlbums, savedArtists = savedArtists,
        savedPlaylists = savedPlaylists, hiddenPlaylistIds = hiddenPlaylistIds,
    )

    private fun LibrarySnapshot.history() = LibrarySnapshot(
        recentlyPlayed = recentlyPlayed, playCounts = playCounts, playedTracks = playedTracks,
        listeningDays = listeningDays, listeningTotalMs = listeningTotalMs,
    )

    private fun AtomicFile.read(): JSONObject? = try {
        JSONObject(openRead().bufferedReader().use { it.readText() })
    } catch (_: java.io.FileNotFoundException) {
        null
    } catch (error: Exception) {
        Log.w(TAG, "Ignoring unreadable ${baseFile.name}", error)
        null
    }

    private fun AtomicFile.write(value: JSONObject) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        val output = startWrite()
        try {
            output.write(bytes)
            finishWrite(output)
        } catch (error: Exception) {
            failWrite(output)
            throw error
        }
    }

    private companion object {
        const val TAG = "LibraryCache"
    }
}
