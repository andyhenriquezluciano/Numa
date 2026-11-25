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

package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.model.LyricLine

internal sealed interface LyricDisplayItem {
    val key: String

    data class Line(
        val line: LyricLine,
        val originalIndex: Int,
    ) : LyricDisplayItem {
        override val key: String get() = "line-$originalIndex"
    }

    data class Pause(
        val afterLineIndex: Int,
        val startMs: Long,
        val endMs: Long,
    ) : LyricDisplayItem {
        override val key: String get() = "pause-$afterLineIndex"
    }
}

private const val LYRIC_PAUSE_MIN_MS = 7_000L
private const val LYRIC_PAUSE_LINE_TAIL_ACCURATE_MS = 400L
private const val LYRIC_PAUSE_LINE_TAIL_FALLBACK_MS = 2_400L
private const val LYRIC_VISUAL_LEAD_MS = 90L

/** Small lookahead for the visual highlight. Seeking still uses the provider's timestamps. */
internal fun lyricVisualPositionMs(positionMs: Long, playing: Boolean, offsetMs: Int = 0): Long =
    positionMs + offsetMs + if (playing) LYRIC_VISUAL_LEAD_MS else 0L

/** A provider's paragraph boundary can follow the first sung word. */
internal fun lyricHighlightStartMs(line: LyricLine): Long? {
    val firstWord = line.words.minOfOrNull { it.startMs }
    val lineStart = line.startMs ?: return firstWord
    return firstWord?.let { minOf(lineStart, it) } ?: lineStart
}

internal fun lyricPauseWindow(line: LyricLine, nextLine: LyricLine): Pair<Long, Long>? {
    val lineStart = lyricHighlightStartMs(line) ?: return null
    val nextStart = lyricHighlightStartMs(nextLine) ?: return null

    val gapLength = nextStart - lineStart
    if (gapLength < LYRIC_PAUSE_MIN_MS) return null

    val (lineEnd, hasAccurateEnd) = when {
        line.endMs != null && line.endMs > lineStart -> line.endMs to true
        line.words.isNotEmpty() -> {
            val lastWord = line.words.last()
            if (lastWord.endMs != null && lastWord.endMs > lineStart) {
                lastWord.endMs to true
            } else if (lastWord.startMs > lineStart) {
                (lastWord.startMs + 400L) to true
            } else {
                lineStart to false
            }
        }
        else -> lineStart to false
    }

    val tail = if (hasAccurateEnd) LYRIC_PAUSE_LINE_TAIL_ACCURATE_MS else LYRIC_PAUSE_LINE_TAIL_FALLBACK_MS
    val pauseStart = lineEnd + tail
    val pauseEnd = nextStart
    if (pauseStart >= pauseEnd) return null

    return pauseStart to pauseEnd
}

internal fun buildLyricDisplayItems(lines: List<LyricLine>): List<LyricDisplayItem> = buildList {
    lines.forEachIndexed { index, line ->
        add(LyricDisplayItem.Line(line, index))
        val nextLine = lines.getOrNull(index + 1)
        if (nextLine != null) {
            val pause = lyricPauseWindow(line, nextLine)
            if (pause != null) {
                add(LyricDisplayItem.Pause(index, pause.first, pause.second))
            }
        }
    }
}

/** Locates the active row without walking every lyric and pause on each frame. */
internal class LyricTimeline(items: List<LyricDisplayItem>) {
    private data class Cue(val start: Float, val index: Int, val end: Float = 0f)

    private val lines = items.mapIndexedNotNull { index, item ->
        (item as? LyricDisplayItem.Line)?.line?.let(::lyricHighlightStartMs)?.let { Cue(it.toFloat(), index) }
    }
    private val pauses = items.mapIndexedNotNull { index, item ->
        (item as? LyricDisplayItem.Pause)?.let { Cue(it.startMs.toFloat(), index, it.endMs.toFloat()) }
    }
    private val orderedLines = lines.zipWithNext().all { (a, b) -> a.start <= b.start }
    private val orderedPauses = pauses.zipWithNext().all { (a, b) -> a.end <= b.start }

    fun activeIndex(position: Float): Int {
        val pause = if (orderedPauses) pauses.atOrBefore(position)?.takeIf { position < it.end }
            else pauses.firstOrNull { position >= it.start && position < it.end }
        val line = if (orderedLines) lines.atOrBefore(position) else lines.lastOrNull { it.start <= position }
        return pause?.index ?: line?.index ?: -1
    }

    private fun List<Cue>.atOrBefore(position: Float): Cue? {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (this[mid].start <= position) low = mid + 1 else high = mid
        }
        return getOrNull(low - 1)
    }
}
