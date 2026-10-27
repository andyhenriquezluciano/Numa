package dev.sfg.numa.mobile.lyrics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LyricsTimestampTest {
    @Test fun millisecondJsonDoesNotChangeUnitsForFractionalOrStringNumbers() {
        val lines = LyricsParser.amPayload(JSONObject("""{"lyrics":[
            {"text":"First", "time":1250.5,"duration":500.5},
            {"text":"Next", "time":"2500.5","duration":"300.2"}
        ]}"""))
        assertEquals(listOf(1_250L, 2_500L), lines.map { it.startMs })
        assertEquals(listOf(1_750L, 2_800L), lines.map { it.endMs })
    }

    @Test fun remoteLrcHonorsDeclaredOffsetAndRepeatedTimestamps() {
        val lines = LyricsParser.lrc("[offset:-2500]\n[00:10.00][00:30.00]First\n[00:40.00]Next")
        assertEquals(listOf(12_500L, 32_500L, 42_500L), lines.map { it.startMs })
        assertEquals(listOf("First", "First", "Next"), lines.map { it.text })
    }

    @Test fun integerTtmlSecondsRemainSeconds() {
        val lines = LyricsParser.ttml("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
            <p begin="10s" end="12s"><span begin="10s" end="11s">First </span><span begin="11s" end="12s">phrase</span></p>
        </div></body></tt>""")
        assertEquals(10_000L, lines.single().startMs)
        assertEquals(listOf(10_000L, 11_000L), lines.single().words.map { it.startMs })
    }
}
