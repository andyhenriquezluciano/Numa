package dev.sfg.numa.mobile.sync

import dev.sfg.numa.mobile.auth.NumaAccountService
import dev.sfg.numa.mobile.library.readBounded
import dev.sfg.numa.mobile.network.awaitResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

internal class CloudTransport(private val http: OkHttpClient, private val account: NumaAccountService) {
    var owner: String = ""
    suspend fun exchange(path: String, body: ByteArray? = null, method: String = if (body == null) "GET" else "POST",
        image: Boolean = false): ByteArray {
        val expected = owner
        check(expected.isNotBlank() && account.state.value.userId == expected) { "La cuenta cambió." }
        val token = account.accessToken() ?: throw IOException(if (account.state.value.signedIn)
            "No se pudo renovar la conexión con NÜMA. Comprueba tu conexión y reintenta."
            else "Inicia sesión en NÜMA para sincronizar.")
        check(account.state.value.userId == expected) { "La cuenta cambió." }
        val request = Request.Builder().url(account.serviceUrl + path).header("Authorization", "Bearer $token")
            .method(method, body?.toRequestBody((if (image) "image/webp" else "application/json").toMediaType())).build()
        return http.newCall(request).awaitResponse().use { response ->
            check(account.state.value.userId == expected) { "La cuenta cambió." }
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(response.body.string()).optString("error_description") }.getOrDefault("")
                throw IOException(cloudSyncFailureMessage(response.code, message))
            }
            require((response.body.contentLength() <= 4L * 1024 * 1024)) { "Respuesta demasiado grande." }
            response.body.byteStream().use { input -> input.readBounded(4 * 1024 * 1024 + 1).also {
                require(it.size <= 4 * 1024 * 1024) { "Respuesta demasiado grande." }
            } }
        }
    }
}
