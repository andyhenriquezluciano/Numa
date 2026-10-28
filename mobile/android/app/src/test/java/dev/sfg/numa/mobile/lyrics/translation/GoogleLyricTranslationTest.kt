package dev.sfg.numa.mobile.lyrics.translation

import org.junit.Assert.assertEquals
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody

class GoogleLyricTranslationTest {
    @Test fun joinsSegmentsWithinOneLyricLine() {
        assertEquals("Hola mundo", parseGoogleTranslation(
            """[[["Hola ","Hello ",null,null],["mundo","world",null,null]],null,"en"]"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyTranslation() {
        parseGoogleTranslation("""[[]]""")
    }
    @Test fun sendsTheSelectedTargetInBothDirectionsWithoutMovingLines() = kotlinx.coroutines.runBlocking {
        val targets = mutableListOf<String>()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val form = chain.request().body as okhttp3.FormBody
            val target = form.value((0 until form.size).first { form.name(it) == "tl" })
            targets += target
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body((if (target == "en") """[[["Hello","Hola"]]]""" else """[[["Hola","Hello"]]]""")
                    .toResponseBody()).build()
        }.build()
        val translator = GoogleLyricTranslation(client)
        assertEquals(listOf("Hello", "Hello"), translator.translate(listOf("Hola", "Hola"), "en"))
        assertEquals(listOf("Hola"), translator.translate(listOf("Hello"), "es"))
        assertEquals(listOf("en", "en", "es"), targets)
    }

}
