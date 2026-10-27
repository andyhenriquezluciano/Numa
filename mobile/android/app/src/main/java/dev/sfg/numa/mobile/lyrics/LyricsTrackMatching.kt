package dev.sfg.numa.mobile.lyrics

import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.abs

internal fun matchesLyricsTrack(candidate: JSONObject, title: String, artist: String, durationMs: Long): Boolean {
    val expectedTitle = lookupIdentity(title)
    val foundTitle = lookupIdentity(candidate.firstText("trackName", "track_name", "title", "name"))
    if (expectedTitle.isBlank() || expectedTitle != foundTitle) return false
    val expectedArtist = lookupIdentity(artist.substringBefore(',').substringBefore(" feat."))
    val foundArtist = lookupIdentity(candidate.firstText("artistName", "artist_name", "artist"))
    if (expectedArtist.isBlank() || !(" $foundArtist ").contains(" $expectedArtist ")) return false
    val foundDurationMs = candidate.optDouble("duration", 0.0).takeIf { it > 0 }?.times(1000)
        ?: durationFromClock(candidate.optString("totalDuration"))
    return durationMs <= 0 || (foundDurationMs > 0 && abs(foundDurationMs - durationMs) <= 1000.0)
}

internal fun matchesRecording(candidate: JSONObject, title: String, artist: String, durationMs: Long): Boolean =
    durationMs > 0 && matchesLyricsTrack(candidate, title, artist, durationMs) &&
        Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}").matches(candidate.firstText("isrc").uppercase(java.util.Locale.ROOT))

internal fun JSONObject.firstText(vararg keys: String): String = keys.firstNotNullOfOrNull {
    optString(it).takeIf { value -> value.isNotBlank() && value != "null" }
}.orEmpty()

private fun durationFromClock(value: String): Double {
    val parts = value.split(':').map { it.toDoubleOrNull() ?: return 0.0 }
    if (parts.size !in 2..3) return 0.0
    return parts.fold(0.0) { seconds, part -> seconds * 60 + part } * 1000
}

internal fun lookupIdentity(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

internal fun rankedLyricsCandidates(results: org.json.JSONArray, title: String, artist: String,
    album: String, durationMs: Long): List<JSONObject> =
    (0 until results.length()).mapNotNull(results::optJSONObject)
        .filter { matchesLyricsTrack(it, title, artist, durationMs) }
        .sortedWith(compareBy<JSONObject> {
            if (durationMs > 0) abs(it.optDouble("duration", 0.0) * 1000 - durationMs) else 0.0
        }.thenBy {
            album.isNotBlank() && lookupIdentity(it.firstText("albumName", "album_name", "album")) != lookupIdentity(album)
        })
