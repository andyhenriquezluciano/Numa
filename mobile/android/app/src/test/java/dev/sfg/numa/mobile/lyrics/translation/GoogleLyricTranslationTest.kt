package dev.sfg.numa.mobile.lyrics.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleLyricTranslationTest {
    @Test fun joinsSegmentsWithinOneLyricLine() {
        assertEquals("Hola mundo", parseGoogleTranslation(
            """[[["Hola ","Hello ",null,null],["mundo","world",null,null]],null,"en"]"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyTranslation() {
        parseGoogleTranslation("""[[]]""")
    }
}
