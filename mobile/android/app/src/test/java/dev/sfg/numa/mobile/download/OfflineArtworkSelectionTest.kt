package dev.sfg.numa.mobile.download

import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files

class OfflineArtworkSelectionTest {
    private val track = Track(id = "song", title = "Song", artist = "Artist", artworkUrl = "remote.jpg")
    private val saved = DownloadItem(
        track = track.copy(artworkUrl = "file:///cover.jpg"),
        status = DownloadStatus.COMPLETED,
        cachedAnimatedArtworkUrl = "cached-wide.m3u8",
        cachedAnimatedArtworkVerticalUrl = "cached-tall.m3u8",
    )

    @Test fun savedRenditionsAndCoverWinOverRemoteMetadata() {
        val art = offlineArtwork(track.copy(animatedArtworkUrl = "expired.m3u8"), saved)!!
        assertEquals("file:///cover.jpg", art.staticUrl)
        assertEquals("cached-wide.m3u8", art.videoUrl)
        assertEquals("cached-tall.m3u8", art.preferredVideoUrl)
    }

    @Test fun wideOnlyCoverRemainsPlayable() {
        assertEquals("cached-wide.m3u8",
            offlineArtwork(track, saved.copy(cachedAnimatedArtworkVerticalUrl = ""))!!.preferredVideoUrl)
    }

    @Test fun incompleteAndDifferentTracksCannotSupplyArtwork() {
        assertNull(offlineArtwork(track, saved.copy(status = DownloadStatus.DOWNLOADING)))
        assertNull(offlineArtwork(track.copy(id = "other"), saved))
    }

    @Test fun missingMotionDoesNotInventAnOfflineAnimation() {
        assertNull(offlineArtwork(track, saved.copy(
            cachedAnimatedArtworkUrl = "", cachedAnimatedArtworkVerticalUrl = "")))
        assertNull(offlineArtwork(track, null))
    }

    @Test fun lowResolutionOfflineCoverIsEvictedAndFallsBackToHighResolutionRemoteUrl() {
        val tempRoot = Files.createTempDirectory("numa-cover-test").toFile()
        try {
            val coversDir = File(tempRoot, "offline_covers").apply { mkdirs() }
            val tinyCover = File(coversDir, "tiny.jpg").apply { writeBytes(pngHeader(60, 60)) }
            val tinyUri = tinyCover.toURI().toString()
            val remoteThumb = "https://lh3.googleusercontent.com/art=w60-h60-l90-rj"

            assertEquals("", OfflineArtwork.usableArtworkUrl(tinyUri))
            assertFalse(tinyCover.exists())

            val art = offlineArtwork(
                track.copy(artworkUrl = remoteThumb),
                saved.copy(track = track.copy(artworkUrl = tinyUri)),
            )!!
            assertEquals("https://lh3.googleusercontent.com/art=w1024-h1024-l90-rj", art.staticUrl)

            val crispCover = File(coversDir, "crisp.jpg").apply { writeBytes(pngHeader(1024, 1024)) }
            val crispUri = crispCover.toURI().toString()
            assertEquals(crispUri, OfflineArtwork.usableArtworkUrl(crispUri))
            assertTrue(crispCover.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun pngHeader(width: Int, height: Int): ByteArray = ByteBuffer.allocate(24)
        .put(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        .putInt(13)
        .put(byteArrayOf(0x49, 0x48, 0x44, 0x52))
        .putInt(width)
        .putInt(height)
        .array()
}
