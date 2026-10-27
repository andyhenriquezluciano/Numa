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

import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.LyricWord
import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test fun cancellingAnUnindexedDownloadRemovesItsTemporaryFile() {
        val store = DownloadStore(baseDir = tempFolder.newFolder("cancel-temp"))
        val temporary = store.getTempFile("in-flight").apply { writeBytes(ByteArray(4096)) }
        store.remove("in-flight")
        assertFalse(temporary.exists())
    }

    @Test fun batchRemovalPersistsTheRemainingEntries() {
        val dir = tempFolder.newFolder("batch")
        val store = DownloadStore(baseDir = dir)
        val items = (0..4).map { id ->
            val file = store.getTargetFile("track$id", "opus").apply { writeText("audio") }
            DownloadItem(track = Track(id = "track$id", title = "Song", artist = "Artist"),
                status = DownloadStatus.COMPLETED, filePath = file.absolutePath, bytesDownloaded = file.length())
        }
        store.saveAll(items)
        store.removeAll(listOf("track0", "track1", "track2", "track3"))
        assertEquals(setOf("track4"), DownloadStore(baseDir = dir).loadAll().keys)
        assertTrue(File(items.last().filePath).isFile)
        items.dropLast(1).forEach { assertFalse(File(it.filePath).exists()) }
    }

    @Test
    fun storePersistsAndLoadsCompletedDownloads() {
        val baseDir = tempFolder.newFolder("downloads_test")
        val store = DownloadStore(baseDir = baseDir)

        val trackFile = File(store.downloadDir, "track_1.opus").apply {
            writeText("dummy-audio-bytes-1234567890")
        }

        val track = Track(
            id = "track_1",
            title = "Awesome Song",
            artist = "Awesome Artist",
            album = "Awesome Album",
            albumId = "album_1",
            artistId = "artist_1",
            artworkUrl = "https://example.com/cover.png",
            durationMs = 210000L,
        )

        val item = DownloadItem(
            track = track,
            status = DownloadStatus.COMPLETED,
            progress = 1.0f,
            bytesDownloaded = trackFile.length(),
            totalBytes = trackFile.length(),
            filePath = trackFile.absolutePath,
            mimeType = "audio/opus",
            downloadedAtMs = System.currentTimeMillis(),
        )

        store.save(item)

        // Reload fresh from disk using a new store instance pointing to same baseDir
        val freshStore = DownloadStore(baseDir = baseDir)
        val loaded = freshStore.loadAll()

        assertEquals(1, loaded.size)
        val loadedItem = loaded["track_1"]
        assertNotNull(loadedItem)
        assertEquals("Awesome Song", loadedItem?.track?.title)
        assertEquals("Awesome Artist", loadedItem?.track?.artist)
        assertEquals(DownloadStatus.COMPLETED, loadedItem?.status)
        assertEquals(trackFile.length(), loadedItem?.bytesDownloaded)
        assertTrue(File(loadedItem!!.filePath).exists())
    }

    @Test
    fun removeDeletesPersistedEntryAndPhysicalFile() {
        val baseDir = tempFolder.newFolder("downloads_remove_test")
        val store = DownloadStore(baseDir = baseDir)

        val trackFile = File(store.downloadDir, "track_remove.opus").apply {
            writeText("audio-data-to-be-removed")
        }
        assertTrue(trackFile.exists())

        val item = DownloadItem(
            track = Track(id = "track_remove", title = "To Remove", artist = "Artist"),
            status = DownloadStatus.COMPLETED,
            filePath = trackFile.absolutePath,
            bytesDownloaded = trackFile.length(),
        )

        store.save(item)
        assertEquals(1, store.loadAll().size)

        // Remove
        store.remove("track_remove")

        assertEquals(0, store.loadAll().size)
        assertTrue(!trackFile.exists())
    }

    @Test
    fun totalBytesCalculatedCorrectly() {
        val baseDir = tempFolder.newFolder("downloads_bytes_test")
        val store = DownloadStore(baseDir = baseDir)

        val f1 = File(store.downloadDir, "t1.opus").apply { writeBytes(ByteArray(1024)) }
        val f2 = File(store.downloadDir, "t2.opus").apply { writeBytes(ByteArray(2048)) }

        store.save(DownloadItem(track = Track(id = "t1", title = "T1", artist = "A1"), status = DownloadStatus.COMPLETED, filePath = f1.absolutePath, bytesDownloaded = 1024L))
        store.save(DownloadItem(track = Track(id = "t2", title = "T2", artist = "A2"), status = DownloadStatus.COMPLETED, filePath = f2.absolutePath, bytesDownloaded = 2048L))

        assertEquals(3072L, store.totalBytesUsed())
    }

    @Test
    fun completedDownloadRequiresARealNonEmptyFile() {
        val track = Track(id = "stale", title = "Stale", artist = "Artist")
        val missing = File(tempFolder.root, "removed.webm")
        val empty = tempFolder.newFile("empty.webm")
        val audio = tempFolder.newFile("audio.webm").apply { writeText("audio") }

        assertNull(
            DownloadItem(track, DownloadStatus.COMPLETED, filePath = missing.absolutePath)
                .completedFileOrNull(),
        )
        assertNull(
            DownloadItem(track, DownloadStatus.COMPLETED, filePath = empty.absolutePath)
                .completedFileOrNull(),
        )
        assertNull(
            DownloadItem(track, DownloadStatus.DOWNLOADING, filePath = audio.absolutePath)
                .completedFileOrNull(),
        )
        assertEquals(
            audio.absolutePath,
            DownloadItem(track, DownloadStatus.COMPLETED, filePath = audio.absolutePath)
                .completedFileOrNull()?.absolutePath,
        )
    }

    @Test
    fun storeSavesLoadsAndDeletesLyrics() {
        val baseDir = tempFolder.newFolder("downloads_lyrics_test")
        val store = DownloadStore(baseDir = baseDir)

        assertFalse(store.hasLyrics("song_123"))
        assertNull(store.loadLyrics("song_123"))

        val lines = listOf(
            LyricLine(
                text = "Hello world",
                startMs = 1000L,
                endMs = 3000L,
                words = listOf(LyricWord("Hello", 1000L, 2000L), LyricWord("world", 2000L, 3000L)),
            ),
            LyricLine(
                text = "Second line",
                startMs = 3500L,
                endMs = 5000L,
            ),
        )

        assertTrue(store.saveLyrics("song_123", lines, listOf("Hola mundo", "Segunda línea")))
        assertTrue(store.hasLyrics("song_123"))

        val loaded = store.loadLyrics("song_123")
        assertNotNull(loaded)
        assertEquals(2, loaded!!.size)
        assertEquals("Hello world", loaded[0].text)
        assertEquals(1000L, loaded[0].startMs)
        assertEquals(3000L, loaded[0].endMs)
        assertEquals(2, loaded[0].words.size)
        assertEquals("Hello", loaded[0].words[0].text)
        assertEquals("Second line", loaded[1].text)
        assertEquals(3500L, loaded[1].startMs)
        assertEquals(listOf("Hola mundo", "Segunda línea"), store.loadSpanishLyrics("song_123"))

        // Ensure remove() also cleans up the lyrics file
        val trackFile = File(store.downloadDir, "song_123.opus").apply { writeText("audio") }
        store.save(DownloadItem(track = Track(id = "song_123", title = "T", artist = "A"), status = DownloadStatus.COMPLETED, filePath = trackFile.absolutePath, bytesDownloaded = 5L))
        assertTrue(store.hasLyrics("song_123"))

        store.remove("song_123")
        assertFalse(store.hasLyrics("song_123"))
        assertNull(store.loadLyrics("song_123"))
        assertNull(store.loadSpanishLyrics("song_123"))
    }
}
