package dev.sfg.numa.mobile.download

import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.*
import org.junit.Test

class DownloadBatchProgressTest {
    private fun item(id: String, status: DownloadStatus) = DownloadItem(Track(id = id, title = id, artist = "Artist"), status)

    @Test fun `counts only songs in the requested batch`() {
        val map = listOf(item("old", DownloadStatus.COMPLETED), item("one", DownloadStatus.COMPLETED),
            item("two", DownloadStatus.DOWNLOADING), item("three", DownloadStatus.QUEUED)).associateBy { it.track.id }
        assertEquals(DownloadBatchProgress(1, 3, 0, 2), downloadBatchProgress(setOf("one", "two", "three"), map))
    }

    @Test fun `failed and cancelled songs are not counted as downloaded`() {
        val map = listOf(item("one", DownloadStatus.COMPLETED), item("two", DownloadStatus.FAILED)).associateBy { it.track.id }
        val result = downloadBatchProgress(setOf("one", "two", "cancelled"), map)
        assertEquals(DownloadBatchProgress(1, 2, 1, 0), result)
        assertFalse(result.active)
    }
}
