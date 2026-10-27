/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */

package dev.sfg.numa.mobile.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheWriter
import java.io.InterruptedIOException

@UnstableApi
internal class InFlightPrefetch {
    private var cancelled = false
    private val writers = mutableSetOf<CacheWriter>()

    @Synchronized fun checkActive() {
        if (cancelled) throw InterruptedIOException("Prefetch cancelled")
    }

    fun cache(writer: CacheWriter) {
        synchronized(this) { checkActive(); writers += writer }
        try { writer.cache(); checkActive() }
        finally { synchronized(this) { writers -= writer } }
    }

    @Synchronized fun cancel() {
        cancelled = true
        writers.forEach { it.cancel() }
    }
}
