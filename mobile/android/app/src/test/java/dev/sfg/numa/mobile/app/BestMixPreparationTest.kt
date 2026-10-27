package dev.sfg.numa.mobile.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BestMixPreparationTest {
    @Test fun stalledDownloadsDoNotBlockAvailableAudio() = runTest {
        val analyzed = mutableListOf<Int>()
        prepareBestMixTracks(listOf(1, 2, 3), fetch = {
            if (it == 1) delay(60_000)
            it
        }, analyze = { track, _ -> analyzed += track }, progress = {})
        assertEquals(listOf(2, 3), analyzed)
        assertEquals(20_000L, currentTime)
    }

    @Test fun entireOnlineQueueHasAFiniteWait() = runTest {
        prepareBestMixTracks((1..51).toList(), fetch = { delay(60_000); it },
            analyze = { _, _ -> fail("Timed out audio must not be analyzed") }, progress = {})
        assertEquals(120_000L, currentTime)
    }

    @Test fun dismissingTheQueueCancelsPreparation() = runTest {
        var analyzed = false
        val job = launch {
            prepareBestMixTracks(listOf(1), fetch = { delay(10_000); it },
                analyze = { _, _ -> analyzed = true }, progress = {})
        }
        delay(100)
        job.cancel()
        job.join()
        assertFalse(analyzed)
        assertTrue(job.isCancelled)
    }
}
