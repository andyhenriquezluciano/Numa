package dev.sfg.numa.mobile.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal suspend fun postRoomRequest(http: OkHttpClient, baseUrl: String, path: String, body: JSONObject): JSONObject =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl$path")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val payload = runCatching { JSONObject(response.body.string()) }.getOrNull() ?: JSONObject()
            if (!response.isSuccessful || !payload.optBoolean("ok")) {
                throw PartyException(payload.optString("error").ifBlank { "No se pudo abrir la sala (${response.code})." })
            }
            payload.optJSONObject("data") ?: JSONObject()
        }
    }
