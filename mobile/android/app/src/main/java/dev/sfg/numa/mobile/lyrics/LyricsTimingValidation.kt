package dev.sfg.numa.mobile.lyrics

import dev.sfg.numa.mobile.model.LyricLine
import dev.sfg.numa.mobile.model.Track
import kotlin.math.abs

internal fun lyricCacheIdentity(track: Track): String = listOf(
    "timing-v3", track.id, track.title, track.artist, track.album, track.durationMs.toString(),
).joinToString("\u0000")

/** Compare matching phrases, including providers that split a verse into different lines. */
internal fun compatibleLyricTiming(reference: List<LyricLine>, candidate: List<LyricLine>): Boolean {
    data class Token(val text: String, val time: Long)
    val tokens = candidate.flatMap { line ->
        if (line.words.isNotEmpty()) line.words.flatMap { word ->
            lookupIdentity(word.text).split(' ').filter(String::isNotBlank).map { Token(it, word.startMs) }
        } else lookupIdentity(line.text).split(' ').filter(String::isNotBlank)
            .mapNotNull { text -> line.startMs?.let { Token(text, it) } }
    }
    var cursor = 0
    val differences = mutableListOf<Long>()
    for (line in reference) {
        val time = line.startMs ?: continue
        val phrase = lookupIdentity(line.text).split(' ').filter(String::isNotBlank).take(4)
        if (phrase.isEmpty()) continue
        val match = (cursor..tokens.size - phrase.size).firstOrNull { index ->
            phrase.indices.all { tokens[index + it].text == phrase[it] }
        } ?: continue
        differences += abs(tokens[match].time - time)
        cursor = match + phrase.size
    }
    val expected = reference.count { it.startMs != null && it.text.isNotBlank() }
    if (differences.size < minOf(3, expected) || differences.size < expected * 0.5) return false
    return differences.count { it <= 750L } >= differences.size * 0.8
}
