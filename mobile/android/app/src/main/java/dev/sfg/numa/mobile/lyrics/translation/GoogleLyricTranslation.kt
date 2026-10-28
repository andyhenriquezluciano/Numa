package dev.sfg.numa.mobile.lyrics.translation

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

/** Translate each distinct line independently so timing cannot shift between lyric lines. */
internal class GoogleLyricTranslation(http: OkHttpClient, private val now: () -> Long = System::currentTimeMillis) {
    private val client = http.newBuilder().followRedirects(false).followSslRedirects(false)
        .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
    @Volatile private var retryAt = 0L

    suspend fun translate(texts: List<String>, target: String = "es"): List<String> = texts.map { text ->
        if (now() < retryAt) throw GoogleTranslationUnavailable()
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single")
            .post(FormBody.Builder().add("client", "gtx").add("sl", "auto")
                .add("tl", target).add("dt", "t").add("q", text).build())
            .build()
        val body = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        retryAt = now() + 30_000
                        continuation.resumeWithException(GoogleTranslationUnavailable())
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) {
                            val timestamp = now()
                            val header = it.header("Retry-After")
                            val requested = header?.toLongOrNull()?.let { seconds ->
                                runCatching { Math.addExact(timestamp, Math.multiplyExact(seconds.coerceAtLeast(0), 1000L)) }.getOrDefault(Long.MAX_VALUE)
                            } ?: runCatching { java.time.ZonedDateTime.parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                            retryAt = maxOf(timestamp + 60_000, requested ?: timestamp + 600_000)
                            continuation.resumeWithException(GoogleTranslationUnavailable())
                        } else runCatching { it.body.string() }
                            .onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(IOException("Could not read translation response.")) }
                    }
                }
            })
        }
        try { parseGoogleTranslation(body) }
        catch (_: Exception) { retryAt = now() + 600_000; throw GoogleTranslationUnavailable() }
    }
}

internal fun parseGoogleTranslation(body: String): String {
    val segments = JSONArray(body).getJSONArray(0)
    return (0 until segments.length()).joinToString("") {
        segments.getJSONArray(it).getString(0)
    }.trim().also { require(it.isNotEmpty()) { "Google Translate devolvió una traducción vacía." } }
}

internal class GoogleTranslationUnavailable : IOException("Google web translation is unavailable. Use the Google on-device translator.")
