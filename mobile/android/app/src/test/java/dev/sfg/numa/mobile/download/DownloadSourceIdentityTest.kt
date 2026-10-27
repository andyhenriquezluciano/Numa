package dev.sfg.numa.mobile.download

import dev.sfg.numa.mobile.model.Track
import org.junit.Assert.*
import org.junit.Test

class DownloadSourceIdentityTest {
    @Test fun actualRecordingIdentitySurvivesRestartWithoutChangingQueueId() {
        val item = DownloadItem(Track("queue", "Song", "Artist", durationMs = 211_000),
            sourceVideoId = "actual-audio", status = DownloadStatus.COMPLETED)
        val restored = DownloadItem.fromJson(item.toJson())
        assertEquals("queue", restored.track.id)
        assertEquals("actual-audio", restored.sourceVideoId)
        assertEquals(211_000L, restored.track.durationMs)
    }
}
