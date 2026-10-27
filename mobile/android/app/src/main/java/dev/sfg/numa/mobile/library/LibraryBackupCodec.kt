package dev.sfg.numa.mobile.library

import dev.sfg.numa.mobile.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object LibraryBackupCodec {
    fun encode(snapshot: LibrarySnapshot): JSONObject {
        val countsObj = JSONObject()
        snapshot.playCounts.forEach { (id, count) -> countsObj.put(id, count) }
        return JSONObject()
            .put("version", 1)
            .put("likedTracks", CatalogJson.tracks(snapshot.likedTracks))
            .put("savedAlbums", array(snapshot.savedAlbums, CatalogJson::album))
            .put("savedArtists", array(snapshot.savedArtists, CatalogJson::artist))
            .put("savedPlaylists", array(snapshot.savedPlaylists, CatalogJson::playlist))
            .put("recentlyPlayed", CatalogJson.tracks(snapshot.recentlyPlayed))
            .put("playCounts", countsObj)
            .put("listeningTotalMs", snapshot.listeningTotalMs)
            .put("playedTracks", CatalogJson.tracks(snapshot.playedTracks))
            .put("listeningDays", array(snapshot.listeningDays) {
                JSONObject().put("date", it.date).put("trackId", it.trackId).put("listens", it.listens).put("heardMs", it.heardMs)
            })
            .put("hiddenPlaylistIds", JSONArray(snapshot.hiddenPlaylistIds.toList()))
    }

    fun decode(root: JSONObject, hidden: Set<String> = root.optJSONArray("hiddenPlaylistIds")?.let { values ->
        (0 until values.length()).map { values.getString(it) }.toSet()
    }.orEmpty()): LibrarySnapshot {
            val countsObj = root.optJSONObject("playCounts") ?: JSONObject()
            val countsMap = buildMap {
                countsObj.keys().forEach { key -> put(key, countsObj.optInt(key, 0).coerceAtLeast(0)) }
            }
            return LibrarySnapshot(
                likedTracks = CatalogJson.tracks(root.optJSONArray("likedTracks")),
                savedAlbums = objects(root.optJSONArray("savedAlbums"), CatalogJson::album),
                savedArtists = objects(root.optJSONArray("savedArtists"), CatalogJson::artist),
                savedPlaylists = objects(root.optJSONArray("savedPlaylists"), CatalogJson::playlist)
                    .filterNot { libraryPlaylistId(it.id) in hidden },
                recentlyPlayed = CatalogJson.tracks(root.optJSONArray("recentlyPlayed")),
                playCounts = countsMap,
                listeningTotalMs = root.optLong("listeningTotalMs").coerceAtLeast(0),
                playedTracks = CatalogJson.tracks(root.optJSONArray("playedTracks")),
                listeningDays = objects(root.optJSONArray("listeningDays")) {
                    ListeningDay(it.getString("date"), it.getString("trackId"), it.optInt("listens").coerceAtLeast(0),
                        it.optLong("heardMs").coerceIn(0, 86_400_000))
                },
                hiddenPlaylistIds = hidden,
            )
    }

    private fun <T> array(values: List<T>, encode: (T) -> JSONObject): JSONArray =
        JSONArray().also { output -> values.forEach { output.put(encode(it)) } }

    private fun <T> objects(values: JSONArray?, decode: (JSONObject) -> T): List<T> = buildList {
        if (values == null) return@buildList
        for (index in 0 until values.length()) values.optJSONObject(index)?.let { add(decode(it)) }
    }

}
