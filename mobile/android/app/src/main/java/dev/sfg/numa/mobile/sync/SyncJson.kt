package dev.sfg.numa.mobile.sync

import org.json.JSONArray
import org.json.JSONObject

internal fun canonicalSyncJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonicalSyncJson(value.get(it)) }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalSyncJson(value.get(it)) }
    is String -> JSONObject.quote(value)
    else -> value.toString()
}
