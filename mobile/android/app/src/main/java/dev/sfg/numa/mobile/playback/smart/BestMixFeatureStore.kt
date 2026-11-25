/* Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL. */

package dev.sfg.numa.mobile.playback.smart

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject

/**
 * Best Mix's feature cache, row for row the desktop's `best-mix-v2/features.sqlite`: full head and
 * tail edges for planning plus the compact summaries the shortlist reads, all as the shared Rust
 * analysis returned them.
 *
 * Calls are synchronous and must be made off the main thread. Failures only disable persistence.
 */
class BestMixFeatureStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE features (id TEXT PRIMARY KEY, duration REAL NOT NULL, head TEXT NOT NULL, " +
                "tail TEXT NOT NULL, head_summary TEXT NOT NULL, tail_summary TEXT NOT NULL, " +
                "used_at INTEGER NOT NULL)",
        )
    }

    // Derived, reproducible data: rebuilding is the safe upgrade, as desktop's version bump is.
    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.execSQL("DROP TABLE IF EXISTS track_features")
        database.execSQL("DROP TABLE IF EXISTS features")
        onCreate(database)
    }

    /** Finds matching analyses in batches and updates their recency for the cache trim. */
    internal fun matchingIds(durations: Map<String, Double>): Set<String> = buildSet {
        durations.keys.chunked(QUERY_BATCH_SIZE).forEach { ids ->
            val matched = runCatching {
                val placeholders = ids.joinToString(",") { "?" }
                readableDatabase.rawQuery(
                    "SELECT id,duration FROM features WHERE id IN ($placeholders)",
                    ids.toTypedArray(),
                ).use { rows ->
                    buildList {
                        while (rows.moveToNext()) {
                            val id = rows.getString(0)
                            if (kotlin.math.abs(rows.getDouble(1) - durations.getValue(id)) <= 1) add(id)
                        }
                    }
                }.also { matching ->
                    if (matching.isNotEmpty()) {
                        val selection = matching.joinToString(",") { "?" }
                        val values = ContentValues().apply { put("used_at", System.currentTimeMillis() / 1000) }
                        writableDatabase.update("features", values, "id IN ($selection)", matching.toTypedArray())
                    }
                }
            }.getOrDefault(emptyList())
            addAll(matched)
        }
    }

    /** Stores a `bestMixAnalyze` result. False when it lacks an edge or a summary. */
    fun store(id: String, duration: Double, features: JSONObject): Boolean = runCatching {
        val parts = listOf("head", "tail", "headSummary", "tailSummary")
            .mapNotNull { key -> features.optJSONObject(key)?.takeIf { it.length() > 0 } }
        if (parts.size != 4) return false
        val values = ContentValues().apply {
            put("id", id)
            put("duration", duration)
            put("head", parts[0].toString())
            put("tail", parts[1].toString())
            put("head_summary", parts[2].toString())
            put("tail_summary", parts[3].toString())
            put("used_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.insertWithOnConflict("features", null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0
    }.onFailure { Log.w(TAG, "Could not persist Best Mix analysis", it) }.getOrDefault(false)

    /** `{"head","tail"}` summaries for [id], or an empty object, as desktop's `summary()`. */
    fun summary(id: String): JSONObject = runCatching {
        readableDatabase.rawQuery("SELECT head_summary,tail_summary FROM features WHERE id=?", arrayOf(id)).use {
            if (!it.moveToFirst()) JSONObject()
            else JSONObject().put("head", JSONObject(it.getString(0))).put("tail", JSONObject(it.getString(1)))
        }
    }.getOrDefault(JSONObject())

    /** The full edge for planning, or null. */
    fun edge(id: String, tail: Boolean): JSONObject? = runCatching {
        readableDatabase.rawQuery("SELECT ${if (tail) "tail" else "head"} FROM features WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) JSONObject(it.getString(0)) else null
        }
    }.getOrNull()

    /** Desktop's 32 MB cap, oldest rows first. */
    fun trim() = runCatching {
        val database = writableDatabase
        var bytes = database.rawQuery(
            "SELECT COALESCE(SUM(length(head)+length(tail)+length(head_summary)+length(tail_summary)),0) FROM features",
            null,
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        while (bytes > MAX_BYTES) {
            val oldest = database.rawQuery(
                "SELECT id,length(head)+length(tail)+length(head_summary)+length(tail_summary) " +
                    "FROM features ORDER BY used_at LIMIT 1", null,
            ).use { if (it.moveToFirst()) it.getString(0) to it.getLong(1) else null } ?: break
            database.delete("features", "id=?", arrayOf(oldest.first))
            bytes -= oldest.second
        }
    }

    companion object {
        private const val QUERY_BATCH_SIZE = 500
        private const val TAG = "NumaBestMixStore"
        internal const val DATABASE_NAME = "best-mix-analysis.db"
        // Version 3: saver-quality audio, the encode desktop analyzes for its feature version 2.
        // Version 4: edge bass curves, desktop's feature version 3.
        private const val DATABASE_VERSION = 4
        private const val MAX_BYTES = 32L * 1024 * 1024
    }
}
