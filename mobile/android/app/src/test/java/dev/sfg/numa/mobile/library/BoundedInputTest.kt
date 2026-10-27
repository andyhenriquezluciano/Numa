package dev.sfg.numa.mobile.library

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedInputTest {
    @Test fun stopsAtLimitWithoutReadingUnboundedArchive() {
        val stream = ByteArrayInputStream(ByteArray(100_000) { 7 })
        assertEquals(1025, stream.readBounded(1025).size)
        assertEquals(98_975, stream.available())
        assertArrayEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2)).readBounded(10))
    }
    @Test fun handlesStreamsThatReturnZeroWithoutSpinning() {
        val stream = object : InputStream() {
            var left = 3
            override fun read(buffer: ByteArray, offset: Int, length: Int) = if (left > 0) 0 else -1
            override fun read() = if (left-- > 0) 5 else -1
        }
        assertArrayEquals(byteArrayOf(5, 5, 5), stream.readBounded(10))
        assertTrue(ByteArrayInputStream(byteArrayOf(1)).readBounded(0).isEmpty())
    }
}
