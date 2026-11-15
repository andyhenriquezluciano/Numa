package dev.sfg.numa.mobile.sync

import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.model.Playlist
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibrarySyncTest {
    @Test fun localPlaylistsStayOutOfAccountBackupsAndLegacyCloudReads() {
        val local = Playlist(id = "local-playlist:phone", title = "Only this device")
        val shared = Playlist(id = "PL-shared", title = "Shared")
        val codec = dev.sfg.numa.mobile.library.LibraryBackupCodec
        val snapshot = LibrarySnapshot(savedPlaylists = listOf(local, shared), hiddenPlaylistIds = setOf(local.id, "SE"))
        val encoded = codec.encode(snapshot)
        assertEquals(listOf(shared), codec.decode(encoded).savedPlaylists)
        assertEquals(setOf("SE"), codec.decode(encoded).hiddenPlaylistIds)
        val legacy = JSONObject(encoded.toString()).put("savedPlaylists", org.json.JSONArray()
            .put(JSONObject().put("id", local.id).put("title", local.title))
            .put(JSONObject().put("id", shared.id).put("title", shared.title)))
        assertEquals(listOf(shared), codec.decode(legacy).savedPlaylists)
    }

    private val song = Track("song", "Song", "Artist")

    @Test fun acceptsDesktopPlaylistAndCoverWhileKeepingAnInFlightLocalEdit() {
        val playlist = Playlist(id = "PL-shared", title = "Shared", tracks = listOf(song))
        val remote = LibrarySnapshot(savedPlaylists = listOf(playlist))
        assertEquals(listOf(playlist), reconcileLibrary(LibrarySnapshot(), LibrarySnapshot(), remote).savedPlaylists)
        val original = JSONObject("""{"cover":{"kind":"custom","asset":"old"}}""")
        val received = JSONObject("""{"cover":{"kind":"custom","asset":"pc"}}""")
        assertEquals("pc", mergeJsonMap(original, original, received).getJSONObject("cover").getString("asset"))
        val edited = JSONObject("""{"cover":{"kind":"custom","asset":"phone"}}""")
        assertEquals("phone", mergeJsonMap(original, edited, received).getJSONObject("cover").getString("asset"))
        val decoded = dev.sfg.numa.mobile.library.LibraryBackupCodec.decode(
            dev.sfg.numa.mobile.library.LibraryBackupCodec.encode(remote))
        assertEquals(remote.savedPlaylists, decoded.savedPlaylists)
    }

    @Test fun countsOnlyUnacknowledgedListeningAfterResponse() {
        val sent = SyncCounters().record("song", 15_000, true)
        val current = sent.record("song", 4_000, false).record("other", 15_000, true)
        val remote = LibrarySnapshot(playCounts = mapOf("song" to 5), listeningTotalMs = 75_000)
        val result = current.delta(sent).applyTo(remote)
        assertEquals(mapOf("song" to 5, "other" to 1), result.playCounts)
        assertEquals(94_000L, result.listeningTotalMs)
        assertEquals(1, result.listeningDays.sumOf { it.listens })
        assertEquals(result, current.delta(current).applyTo(result))
    }

    @Test fun keepsEditsMadeDuringUploadAndAcceptsRemoteDeletes() {
        val sent = LibrarySnapshot(likedTracks = listOf(song))
        val added = Track("new", "New", "Artist")
        val local = sent.copy(likedTracks = listOf(song, added))
        val result = reconcileLibrary(sent, local, LibrarySnapshot(playCounts = mapOf("song" to 7)))
        assertEquals(listOf(added), result.likedTracks)
        assertEquals(7, result.playCounts["song"])
    }

    @Test fun preservesLocalDeleteWhileRemoteAddsAnotherTrack() {
        val added = Track("new", "New", "Artist")
        val result = reconcileLibrary(LibrarySnapshot(likedTracks = listOf(song)), LibrarySnapshot(),
            LibrarySnapshot(likedTracks = listOf(song, added)))
        assertEquals(listOf(added), result.likedTracks)
    }

    @Test fun jsonOrderDoesNotCreateMetadataChanges() {
        assertEquals(canonicalSyncJson(JSONObject("""{"a":1,"b":{"x":true,"y":2}}""")),
            canonicalSyncJson(JSONObject("""{"b":{"y":2,"x":true},"a":1}""")))
        val counters = SyncCounters().record("song", 16_000, true)
        assertEquals(counters, SyncCounters.read(JSONObject(counters.json().toString())))
    }
}
