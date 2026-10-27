package dev.sfg.numa.mobile.sync

import org.junit.Assert.*
import org.junit.Test

class CloudCoverEncodingTest {
    @Test fun smallAndLargePicturesAlwaysUseAPositiveSample() {
        for ((width, height) in listOf(1 to 1, 600 to 600, 1024 to 1024, 1025 to 1025,
            4096 to 3072, 100 to 8192, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val sample = cloudCoverSampleSize(width, height)
            assertTrue(sample >= 1)
            assertTrue(maxOf(width, height) / sample <= 1024)
        }
        assertEquals(1, cloudCoverSampleSize(600, 600))
        assertEquals(4, cloudCoverSampleSize(4096, 3072))
    }
    @Test fun invalidDimensionsAreRejectedWithoutDividingByZero() {
        assertThrows(IllegalArgumentException::class.java) { cloudCoverSampleSize(100, 100, 0) }
        for ((width, height) in listOf(0 to 0, -1 to 100, 100 to 0)) {
            assertThrows(IllegalArgumentException::class.java) { cloudCoverSampleSize(width, height) }
        }
    }
}
