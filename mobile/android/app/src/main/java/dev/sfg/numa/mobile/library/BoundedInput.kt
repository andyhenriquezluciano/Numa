package dev.sfg.numa.mobile.library

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun InputStream.readBounded(limit: Int): ByteArray {
    require(limit >= 0)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(minOf(limit.coerceAtLeast(1), 8192))
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        if (count == 0) {
            val next = read()
            if (next < 0) break
            output.write(next)
        } else output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
