package dev.sfg.numa.mobile.local

import dev.sfg.numa.mobile.model.LyricWord
import kotlin.math.roundToLong

private val enhancedStamp = Regex("""<(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)>""")

internal fun enhancedLrcWords(body: String, originalStart: Long, repeatedStart: Long, offsetMs: Long): List<LyricWord> {
    val stamps = enhancedStamp.findAll(body).toList()
    if (stamps.isEmpty()) return emptyList()
    val shift = repeatedStart - originalStart + offsetMs
    fun time(stamp: MatchResult): Long = (stamp.groupValues[1].toLong() * 60_000 +
        (stamp.groupValues[2].replace(':', '.').toDouble() * 1000).roundToLong() + shift).coerceAtLeast(0)
    return buildList {
        body.substring(0, stamps.first().range.first).trim().takeIf(String::isNotBlank)?.let {
            add(LyricWord(it, (repeatedStart + offsetMs).coerceAtLeast(0), time(stamps.first())))
        }
        stamps.forEachIndexed { index, stamp ->
            val next = stamps.getOrNull(index + 1)
            val text = body.substring(stamp.range.last + 1, next?.range?.first ?: body.length).trim()
            if (text.isNotBlank()) add(LyricWord(text, time(stamp), next?.let(::time)))
        }
    }
}
