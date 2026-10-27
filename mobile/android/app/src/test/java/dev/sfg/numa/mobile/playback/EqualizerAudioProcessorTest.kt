/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */
package dev.sfg.numa.mobile.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import dev.sfg.numa.mobile.model.EqualizerConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class EqualizerAudioProcessorTest {
    private fun configured() = EqualizerAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    @Test fun flushDoesNotCarryAudioIntoTheNextTrack() {
        val eq = configured()
        eq.config = EqualizerConfig(enabled = true, bassBoost = 0.8f)
        val signal = ByteBuffer.allocate(512).order(ByteOrder.nativeOrder())
        repeat(256) { signal.putShort(12000) }
        signal.flip()
        eq.queueInput(signal)
        eq.output
        eq.flush(AudioProcessor.StreamMetadata.DEFAULT)
        eq.queueInput(ByteBuffer.allocate(512).order(ByteOrder.nativeOrder()))
        val silence = eq.output
        while (silence.hasRemaining()) assertEquals(0, silence.short.toInt())
    }
}
