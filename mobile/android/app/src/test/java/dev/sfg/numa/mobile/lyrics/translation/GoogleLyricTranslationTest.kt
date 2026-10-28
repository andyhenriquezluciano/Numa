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

    @Test fun aBlockedServiceDoesNotFollowTheChallengeOrRetryBeforeTheServerAllowsIt() = kotlinx.coroutines.runBlocking {
        var time = 1000L
        var calls = 0
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(if (calls == 1) 429 else 200).message("Test")
                .header("Retry-After", "120")
                .body((if (calls == 1) "blocked" else """[[["Hello","Hola"]]]""").toResponseBody()).build()
        }.build()
        try {
            val translator = GoogleLyricTranslation(client) { time }
            repeat(2) {
                val failure = runCatching { translator.translate(listOf("Hola"), "en") }.exceptionOrNull()
                org.junit.Assert.assertTrue(failure is GoogleTranslationUnavailable)
            }
            assertEquals(1, calls)
            time += 120_001
            assertEquals(listOf("Hello"), translator.translate(listOf("Hola"), "en"))
            assertEquals(2, calls)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun aRedirectIsReportedInsteadOfParsingTheChallengeAsLyrics() = kotlinx.coroutines.runBlocking {
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(302).message("Redirect").header("Location", "https://example.invalid/challenge")
                .body("<html>challenge</html>".toResponseBody()).build()
        }.build()
        try {
            org.junit.Assert.assertTrue(runCatching { GoogleLyricTranslation(client).translate(listOf("Hola"), "en") }
                .exceptionOrNull() is GoogleTranslationUnavailable)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

}
