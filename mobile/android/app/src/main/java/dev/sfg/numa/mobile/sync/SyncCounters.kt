package dev.sfg.numa.mobile.sync

import dev.sfg.numa.mobile.model.ListeningDay
import dev.sfg.numa.mobile.model.LibrarySnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class SyncCounters(val counts: Map<String, Long> = emptyMap(), val heard: Map<String, Long> = emptyMap(),
    val days: List<ListeningDay> = emptyList()) {
    fun record(id: String, ms: Long, listen: Boolean): SyncCounters {
        val day = LocalDate.now().toString()
        val previous = days.firstOrNull { it.date == day && it.trackId == id } ?: ListeningDay(day, id)
        return copy(counts = if (listen) counts + (id to ((counts[id] ?: 0) + 1)) else counts,
            heard = if (ms > 0) heard + (id to ((heard[id] ?: 0) + ms)) else heard,
            days = days.filterNot { it.date == day && it.trackId == id } + previous.copy(
                listens = previous.listens + if (listen) 1 else 0, heardMs = previous.heardMs + ms))
    }
    fun delta(sent: SyncCounters): SyncCounters {
        fun difference(a: Map<String, Long>, b: Map<String, Long>) = a.mapValues { (id, value) -> (value - (b[id] ?: 0)).coerceAtLeast(0) }
        val old = sent.days.associateBy { it.date to it.trackId }
        return SyncCounters(difference(counts, sent.counts), difference(heard, sent.heard), days.map {
            val previous = old[it.date to it.trackId]
            it.copy(listens = (it.listens - (previous?.listens ?: 0)).coerceAtLeast(0),
                heardMs = (it.heardMs - (previous?.heardMs ?: 0)).coerceAtLeast(0)) })
    }
    fun applyTo(snapshot: LibrarySnapshot): LibrarySnapshot {
        val allDays = (snapshot.listeningDays + days).groupBy { it.date to it.trackId }.values.map { rows ->
            rows.first().copy(listens = rows.sumOf { it.listens }, heardMs = rows.sumOf { it.heardMs })
        }
        return snapshot.copy(playCounts = (snapshot.playCounts.keys + counts.keys).associateWith {
            ((snapshot.playCounts[it] ?: 0).toLong() + (counts[it] ?: 0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
            listeningDays = allDays, listeningTotalMs = maxOf(snapshot.listeningTotalMs, snapshot.listeningDays.sumOf { it.heardMs }) + heard.values.sum())
    }
    fun json(): JSONObject = JSONObject().put("counts", JSONObject(counts)).put("heard", JSONObject(heard))
        .put("days", JSONArray().also { array -> days.forEach { array.put(JSONObject().put("date", it.date)
            .put("trackId", it.trackId).put("listens", it.listens).put("heardMs", it.heardMs)) } })
    companion object {
        fun read(json: JSONObject): SyncCounters {
            fun map(key: String): Map<String, Long> = json.optJSONObject(key)?.let { values -> values.keys().asSequence()
                .associateWith { values.optLong(it).coerceAtLeast(0) } }.orEmpty()
            val days = json.optJSONArray("days") ?: JSONArray()
            return SyncCounters(map("counts"), map("heard"), (0 until days.length()).map { days.getJSONObject(it).let { row ->
                ListeningDay(row.getString("date"), row.getString("trackId"), row.optInt("listens"), row.optLong("heardMs")) } })
        }
    }
}
