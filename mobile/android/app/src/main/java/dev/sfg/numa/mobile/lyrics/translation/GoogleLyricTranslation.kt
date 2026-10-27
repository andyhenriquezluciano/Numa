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
internal class GoogleLyricTranslation(private val client: OkHttpClient) {
    suspend fun translate(texts: List<String>): List<String> = texts.map { text ->
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single")
            .post(FormBody.Builder().add("client", "gtx").add("sl", "auto")
                .add("tl", "es").add("dt", "t").add("q", text).build())
            .build()
        val body = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IOException("No se pudo conectar con Google Translate."))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) continuation.resumeWithException(
                            IOException("Google Translate no está disponible (${it.code})."))
                        else runCatching { it.body.string() }
                            .onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(it) }
                    }
                }
            })
        }
        parseGoogleTranslation(body)
    }
}

internal fun parseGoogleTranslation(body: String): String {
    val segments = JSONArray(body).getJSONArray(0)
    return (0 until segments.length()).joinToString("") {
        segments.getJSONArray(it).getString(0)
    }.trim().also { require(it.isNotEmpty()) { "Google Translate devolvió una traducción vacía." } }
}
