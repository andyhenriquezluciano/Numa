package dev.sfg.numa.mobile.widget

import dev.sfg.numa.mobile.model.LyricLine

internal fun widgetLyricLines(lines: List<LyricLine>, positionMs: Long): Pair<String?, String?> {
    if (lines.isEmpty()) return null to null
    if (lines.none { it.startMs != null }) return lines.first().text to lines.getOrNull(1)?.text
    val index = lines.indexOfLast { it.startMs?.let { start -> start <= positionMs } == true }
    if (index < 0) return "♪" to lines.first().text
    val line = lines[index]
    val expired = line.endMs?.let { positionMs > it + 2_000 } == true
    return (if (expired || line.text.isBlank()) "♪" else line.text) to lines.getOrNull(index + 1)?.text
}
