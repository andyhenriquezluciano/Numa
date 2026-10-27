package dev.sfg.numa.mobile.social

import dev.sfg.numa.mobile.model.Track
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PartySocketMessageTest {
    @Test fun boundsUnicodeMessagesWithoutChangingTheOriginalQueue() {
        val tracks = (1..100).map { Track(id = "$it", title = "🎵".repeat(250), artist = "Artista") }
        val source = JSONObject().put("type", "party:update").put("payload",
            PartyStateJson.encode(PartyPlaybackState(track = tracks.first(), queue = tracks)))
        val encoded = partySocketMessage(source)
        assertTrue(encoded.toByteArray(Charsets.UTF_8).size <= 60_000)
        assertEquals(100, source.getJSONObject("payload").getJSONArray("queue").length())
        assertEquals("1", JSONObject(encoded).getJSONObject("payload").getJSONObject("track").getString("id"))
    }
}
