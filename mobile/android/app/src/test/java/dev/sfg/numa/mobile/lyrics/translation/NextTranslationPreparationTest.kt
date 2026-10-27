package dev.sfg.numa.mobile.lyrics.translation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NextTranslationPreparationTest {
    @Test fun rapidQueueChangesPrepareOnlyTheLatestNextSong() = runTest {
        val next = MutableStateFlow<String?>("old")
        val prepared = mutableListOf<String>()
        backgroundScope.launch { prepareNextTranslation(next) { prepared += it } }
        runCurrent()
        advanceTimeBy(200)
        next.value = "new"
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf("new"), prepared)
    }

    @Test fun foregroundOrResourceRestrictionsCancelActivePreparation() = runTest {
        val next = MutableStateFlow<String?>("next")
        var active = 0
        var finished = false
        backgroundScope.launch {
            prepareNextTranslation(next) {
                active++
                try { delay(10_000); finished = true } finally { active-- }
            }
        }
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals(1, active)
        next.value = null
        runCurrent()
        assertEquals(0, active)
        assertFalse(finished)
    }

    @Test fun timeoutReleasesPreparationAndAllowsTheFollowingSong() = runTest {
        val next = MutableStateFlow<String?>("stalled")
        val prepared = mutableListOf<String>()
        backgroundScope.launch {
            prepareNextTranslation(next) { if (it == "stalled") awaitCancellation() else prepared += it }
        }
        runCurrent()
        advanceTimeBy(60_501)
        runCurrent()
        next.value = "available"
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf("available"), prepared)
    }

    @Test fun failureDoesNotDisableFuturePreparation() = runTest {
        val next = MutableStateFlow<String?>("broken")
        val prepared = mutableListOf<String>()
        backgroundScope.launch {
            prepareNextTranslation(next) { if (it == "broken") error("No lyrics") else prepared += it }
        }
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        next.value = "good"
        runCurrent()
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf("good"), prepared)
    }
}
