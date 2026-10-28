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

package dev.sfg.numa.mobile.download

import android.content.Context
import android.os.Environment
import android.util.Log
import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-disk index and file store for offline downloaded tracks.
 */
class DownloadStore(
    context: Context? = null,
    baseDir: File? = null,
    /** Return true to place new downloads on removable storage when it is mounted. */
    private val useSdCard: () -> Boolean = { false },
) {
    private val appContext = context?.applicationContext

    val downloadDir: File = baseDir ?: context?.let(::defaultDownloadDir) ?: File(
        File(System.getProperty("java.io.tmpdir"), "numa-downloads"),
        DOWNLOADS_FOLDER,
    ).apply { if (!exists()) mkdirs() }

    /** Index stays on primary storage so it survives a removed or swapped SD card. */
    private val indexFile: File = File(downloadDir, INDEX_FILE_NAME)
    private val lock = Any()
    private var cachedIndex: Map<String, DownloadItem>? = null

    /** Returns all persisted completed/failed download entries. */
    fun loadAll(): Map<String, DownloadItem> = synchronized(lock) {
        cachedIndex?.let { return it }
        if (!indexFile.exists()) return emptyMap<String, DownloadItem>().also { cachedIndex = it }
        return runCatching {
            val text = indexFile.readText()
            val array = JSONArray(text)
            val result = mutableMapOf<String, DownloadItem>()
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val item = DownloadItem.fromJson(json)
                if (item.track.id.isNotBlank()) {
                    // Check if file still exists on disk for completed downloads
                    if (item.status == DownloadStatus.COMPLETED && item.filePath.isNotBlank()) {
                        val file = File(item.filePath)
                        if (file.exists() && file.length() > 0) {
                            result[item.track.id] = item
                        }
                    } else if (item.status == DownloadStatus.FAILED) {
                        result[item.track.id] = item
                    }
                }
            }
            result
        }.onFailure {
            Log.w(TAG, "Failed to load downloads index", it)
        }.getOrDefault(emptyMap()).also { cachedIndex = it }
    }

    /** Save or update a download record. */
    fun save(item: DownloadItem) = synchronized(lock) {
        val current = loadAll().toMutableMap()
        current[item.track.id] = item
        writeIndex(current.values.toList())
    }

    /** Save multiple download records. */
    fun saveAll(items: List<DownloadItem>) = synchronized(lock) {
        val current = loadAll().toMutableMap()
        for (item in items) {
            current[item.track.id] = item
        }
        writeIndex(current.values.toList())
    }

    /** Remove a download entry and delete its file from disk. */
    fun remove(videoId: String): Boolean = removeAll(listOf(videoId)) > 0

    fun removeAll(videoIds: Collection<String>): Int = synchronized(lock) {
        val current = loadAll().toMutableMap()
        var removed = 0
        for (videoId in videoIds.toSet()) {
            current.remove(videoId)?.let { item ->
                removed++
                if (item.filePath.isNotBlank()) File(item.filePath).delete()
            }
            val safe = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            storageDirs().forEach { dir ->
                File(dir, "$safe.lyrics.json").delete()
                File(dir, "$safe.tmp").delete()
            }
        }
        writeIndex(current.values.toList())
        removed
    }

    fun removeTemp(videoId: String) {
        val safe = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        storageDirs().forEach { File(it, "$safe.tmp").delete() }
    }

    private fun storageDirs(): List<File> =
        (listOf(downloadDir) + listOfNotNull(appContext?.let(::sdCardDownloadDir))).distinct()

    /** Gets lyrics file path for a track. */
    fun getLyricsFile(videoId: String): File {
        val safeName = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return storageDirs().map { File(it, "$safeName.lyrics.json") }.firstOrNull { it.isFile }
            ?: File(trackDir(), "$safeName.lyrics.json")
    }

    /** Returns true if offline lyrics exist on disk for the track. */
    fun hasLyrics(videoId: String): Boolean = getLyricsFile(videoId).let { it.exists() && it.length() > 0 }

    /** Persists offline lyrics for a track. */
    fun saveLyrics(
        videoId: String,
        lines: List<LyricLine>,
        spanishTranslations: List<String?> = emptyList(),
        timingIdentity: String = "",
        translationTarget: String = "es",
    ): Boolean = synchronized(lock) {
        if (videoId.isBlank() || lines.isEmpty()) return false
        runCatching {
            val array = JSONArray()
            for ((index, line) in lines.withIndex()) {
                val obj = JSONObject()
                obj.put("text", line.text)
                if (index == 0 && timingIdentity.isNotBlank()) {
                    obj.put("timingIdentity", timingIdentity)
                    obj.put("timingValidatedAt", System.currentTimeMillis())
                }
                spanishTranslations.getOrNull(index)?.takeIf(String::isNotBlank)?.let {
                    obj.put(if (translationTarget == "en") "translationEn" else "translationEs", it)
                }
                line.startMs?.let { obj.put("startMs", it) }
                line.endMs?.let { obj.put("endMs", it) }
                line.agentLane?.let { obj.put("agentLane", it) }
                if (line.words.isNotEmpty()) {
                    val wordsArray = JSONArray()
                    for (w in line.words) {
                        val wObj = JSONObject()
                        wObj.put("text", w.text)
                        wObj.put("startMs", w.startMs)
                        w.endMs?.let { wObj.put("endMs", it) }
                        wordsArray.put(wObj)
                    }
                    obj.put("words", wordsArray)
                }
                if (line.adlibs.isNotEmpty()) {
                    val adlibsArray = JSONArray()
                    for (w in line.adlibs) {
                        val wObj = JSONObject()
                        wObj.put("text", w.text)
                        wObj.put("startMs", w.startMs)
                        w.endMs?.let { wObj.put("endMs", it) }
                        adlibsArray.put(wObj)
                    }
                    obj.put("adlibs", adlibsArray)
                }
                array.put(obj)
            }
            val target = getLyricsFile(videoId)
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(array.toString())
            try {
                java.nio.file.Files.move(temp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure {
            Log.w(TAG, "Failed to save offline lyrics for $videoId", it)
        }.isSuccess
    }

    fun hasValidatedLyrics(videoId: String, identity: String): Boolean = synchronized(lock) {
        runCatching {
            val first = JSONArray(getLyricsFile(videoId).readText()).optJSONObject(0) ?: return@runCatching false
            first.optString("timingIdentity") == identity &&
                System.currentTimeMillis() - first.optLong("timingValidatedAt") in 0L..604_800_000L
        }.getOrDefault(false)
    }

    /** Loads offline lyrics for a track if present on disk. */
    fun loadLyrics(videoId: String): List<LyricLine>? = synchronized(lock) {
        val file = getLyricsFile(videoId)
        if (!file.exists() || file.length() == 0L) return null
        runCatching {
            val text = file.readText()
            val array = JSONArray(text)
            val lines = mutableListOf<LyricLine>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val lineText = obj.optString("text", "")
                val startMs = if (obj.has("startMs")) obj.optLong("startMs") else null
                val endMs = if (obj.has("endMs")) obj.optLong("endMs") else null
                val agentLane = if (obj.has("agentLane")) obj.optString("agentLane") else null

                val words = mutableListOf<LyricWord>()
                val wordsArray = obj.optJSONArray("words")
                if (wordsArray != null) {
                    for (j in 0 until wordsArray.length()) {
                        val wObj = wordsArray.optJSONObject(j) ?: continue
                        words.add(LyricWord(
                            text = wObj.optString("text", ""),
                            startMs = wObj.optLong("startMs", 0L),
                            endMs = if (wObj.has("endMs")) wObj.optLong("endMs") else null,
                        ))
                    }
                }

                val adlibs = mutableListOf<LyricWord>()
                val adlibsArray = obj.optJSONArray("adlibs")
                if (adlibsArray != null) {
                    for (j in 0 until adlibsArray.length()) {
                        val wObj = adlibsArray.optJSONObject(j) ?: continue
                        adlibs.add(LyricWord(
                            text = wObj.optString("text", ""),
                            startMs = wObj.optLong("startMs", 0L),
                            endMs = if (wObj.has("endMs")) wObj.optLong("endMs") else null,
                        ))
                    }
                }

                lines.add(LyricLine(lineText, startMs, endMs, words, adlibs, agentLane))
            }
            lines
        }.onFailure {
            Log.w(TAG, "Failed to load offline lyrics for $videoId", it)
        }.getOrNull()
    }

    /** Loads the Spanish lines saved with an offline track. */
    fun loadSpanishLyrics(videoId: String): List<String?>? = loadTranslatedLyrics(videoId, "es")

    fun loadTranslatedLyrics(videoId: String, target: String): List<String?>? = synchronized(lock) {
        val file = getLyricsFile(videoId)
        if (!file.exists() || file.length() == 0L) return null
        runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                array.optJSONObject(index)?.optString(if (target == "en") "translationEn" else "translationEs")?.takeIf(String::isNotBlank)
            }.takeIf { values -> values.any { it != null } }
        }.onFailure {
            Log.w(TAG, "Failed to load offline Spanish lyrics for $videoId", it)
        }.getOrNull()
    }

    /** Gets target file path for a track. */
    fun getTargetFile(videoId: String, extension: String): File {
        val safeName = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(trackDir(), "$safeName.$extension")
    }

    /** Gets temp download file path for a track. */
    fun getTempFile(videoId: String): File {
        val safeName = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(trackDir(), "$safeName.tmp")
    }

    /** Calculate total bytes used by downloaded tracks. */
    fun totalBytesUsed(): Long = synchronized(lock) {
        loadAll().values
            .filter { it.status == DownloadStatus.COMPLETED }
            .sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
    }

    private fun trackDir(): File {
        if (useSdCard()) appContext?.let(::sdCardDownloadDir)?.let { return it }
        return downloadDir
    }

    private fun writeIndex(items: List<DownloadItem>) {
        cachedIndex = items.associateBy { it.track.id }
        runCatching {
            val array = JSONArray()
            for (item in items) {
                array.put(item.toJson())
            }
            val tempIndex = File(downloadDir, "$INDEX_FILE_NAME.tmp")
            tempIndex.writeText(array.toString(2))
            try {
                java.nio.file.Files.move(
                    tempIndex.toPath(),
                    indexFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                java.nio.file.Files.move(
                    tempIndex.toPath(),
                    indexFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }.onFailure {
            Log.e(TAG, "Failed to write downloads index", it)
        }
    }

    companion object {
        private const val TAG = "DownloadStore"
        private const val DOWNLOADS_FOLDER = "offline_downloads"
        private const val INDEX_FILE_NAME = "downloads.json"

        /** App-specific directory on mounted removable storage, or null when none exists. */
        fun sdCardDownloadDir(context: Context): File? = context.getExternalFilesDirs(null)
            .firstOrNull { dir ->
                dir != null &&
                    Environment.isExternalStorageRemovable(dir) &&
                    Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
            }
            ?.let { File(it, DOWNLOADS_FOLDER) }
            ?.apply { if (!exists()) mkdirs() }
            ?.takeIf { it.isDirectory }

        fun defaultDownloadDir(context: Context): File = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            DOWNLOADS_FOLDER,
        ).apply { if (!exists()) mkdirs() }
    }
}
