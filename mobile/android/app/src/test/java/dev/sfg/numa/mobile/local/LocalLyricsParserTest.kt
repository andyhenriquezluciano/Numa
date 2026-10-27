/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLyricsParserTest {
    @Test fun enhancedLrcKeepsWordTimingInsteadOfDiscardingIt() {
        val line = LocalLyricsParser.parse("[00:20.000]<00:23.981>First <00:24.500>phrase<00:25.000>").single()
        assertEquals("First phrase", line.text)
        assertEquals(23_981L, line.startMs)
        assertEquals(listOf(23_981L, 24_500L), line.words.map { it.startMs })
        assertEquals(listOf(24_500L, 25_000L), line.words.map { it.endMs })
    }

    @Test fun offsetsAndRepeatedLinesShiftWordsAndLinesTogether() {
        val lines = LocalLyricsParser.parse("[offset:-500]\n[00:10.000][00:30.000]<00:10.200>First <00:10.800>phrase<00:11.000>")
        assertEquals(listOf(10_700L, 30_700L), lines.map { it.startMs })
        assertEquals(listOf(11_300L, 31_300L), lines.map { it.words[1].startMs })
        assertEquals(listOf(11_500L, 31_500L), lines.map { it.endMs })
    }

    @Test fun offsetAtEndAppliesToEveryLineAndIndentedStampsStaySynced() {
        val lines = LocalLyricsParser.parse("  [00:01.50]First\n[00:10.00]Second\n[offset:-500]")
        assertEquals(listOf(2000L, 10500L), lines.map { it.startMs })
        assertEquals(10500L, lines[0].endMs)
    }

    @Test
    fun `lrc keeps several stamps per line, applies the offset and sorts by time`() {
        val lines = LocalLyricsParser.parse(
            "[ar:Someone]\n[offset:500]\n[00:20.00]Second\n[00:01.50][00:10.00]First <00:02.00>word\n",
        )
        assertEquals(listOf("First word", "First word", "Second"), lines.map { it.text })
        // 1.5 s shifted earlier by the 0.5 s offset, then ends inferred from the next line.
        assertEquals(listOf(1000L, 9500L, 19500L), lines.map { it.startMs })
        assertEquals(9500L, lines[0].endMs)
    }

    @Test
    fun `srt lines carry both stamps`() {
        val lines = LocalLyricsParser.parse("1\n00:00:01,000 --> 00:00:03,500\nHello\nthere\n\n")
        assertEquals(1, lines.size)
        assertEquals("Hello there", lines[0].text)
        assertEquals(1000L, lines[0].startMs)
        assertEquals(3500L, lines[0].endMs)
    }

    @Test
    fun `plain text is unsynced and blank input is empty`() {
        val plain = LocalLyricsParser.parse("one\n\ntwo\n")
        assertEquals(listOf("one", "two"), plain.map { it.text })
        assertTrue(plain.all { it.startMs == null })
        assertTrue(LocalLyricsParser.parse("  \n").isEmpty())
    }

    @Test
    fun `a byte order mark and windows line endings are tolerated`() {
        val lines = LocalLyricsParser.parse("﻿[00:01.00]hi\r\n[00:02.00]there\r\n")
        assertEquals(listOf("hi", "there"), lines.map { it.text })
        assertNull(lines.last().endMs)
    }
}
