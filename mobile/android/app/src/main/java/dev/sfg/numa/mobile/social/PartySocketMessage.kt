package dev.sfg.numa.mobile.social

import org.json.JSONObject

/** Bound the UTF-8 payload, including accented titles and artwork URLs. */
internal fun partySocketMessage(message: JSONObject): String {
    if (message.optString("type") != "party:update") return message.toString()
    val bounded = JSONObject(message.toString())
    val state = bounded.optJSONObject("payload") ?: return bounded.toString()
    val history = state.optJSONArray("history")
    val queue = state.optJSONArray("queue")
    var encoded = bounded.toString()
    while (encoded.toByteArray(Charsets.UTF_8).size > 60_000) {
        when {
            history != null && history.length() > 0 -> history.remove(0)
            queue != null && queue.length() > 0 -> queue.remove(queue.length() - 1)
            else -> {
                // Artwork is optional; keep the current song and its playback times intact.
                val track = state.optJSONObject("track") ?: break
                val artworkKeys = listOf("animatedArtworkUrl", "animatedArtworkVerticalUrl", "artworkUrl", "thumbnail")
                if (artworkKeys.none { track.has(it) }) break
                artworkKeys.forEach(track::remove)
            }
        }
        encoded = bounded.toString()
    }
    return encoded
}
