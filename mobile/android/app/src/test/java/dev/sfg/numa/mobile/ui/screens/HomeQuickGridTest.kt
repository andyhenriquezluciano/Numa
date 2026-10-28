package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.CatalogSection
import dev.sfg.numa.mobile.model.LibrarySnapshot
import dev.sfg.numa.mobile.model.LoadState
import dev.sfg.numa.mobile.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeQuickGridTest {
    private val likedAliases = listOf("LM", "VLLM", "FEmusic_liked_videos", "VLFEmusic_liked_videos")

    @Test fun numaAndYouTubeLikedAliasesFillOnlyOneHomeShortcut() {
        for (savedId in likedAliases) for (feedId in likedAliases) {
            val saved = Playlist(savedId, "Música que me gusta", artworkUrl = "custom.png")
            val remote = Playlist(feedId, "Música que te gusta", artworkUrl = "remote.png")
            val others = (1..6).map { CatalogItem.Collection(Playlist("PL$it", "Lista $it", artworkUrl = "$it.png")) }
            val feed = LoadState.Content(listOf(CatalogSection("home", "Home", listOf(CatalogItem.Collection(remote)) + others)))
            val items = quickGridItems(LibrarySnapshot(savedPlaylists = listOf(saved)), feed, {}, {}, {})
            assertEquals("$savedId / $feedId", listOf(dev.sfg.numa.mobile.ui.text.uiText("Liked music", "Música que me gusta")) + (1..5).map { "Lista $it" }, items.map { it.title })
            assertEquals("custom.png", items.first().artworkUrl)
        }
    }

    @Test fun savedLikedAliasesKeepTheFirstShortcutAndItsActions() {
        val saved = likedAliases.map { Playlist(it, "Liked", artworkUrl = "cover.png") }
        var opened = ""
        var played = ""
        val items = quickGridItems(LibrarySnapshot(savedPlaylists = saved), LoadState.Content(emptyList()),
            { opened = it }, {}, { played = it.stableId })
        assertEquals(1, items.size)
        items.single().onClick()
        items.single().onPlay()
        assertEquals(saved.first().id, opened)
        assertEquals(saved.first().id, played)
        assertEquals(1, saved.map { CatalogItem.Collection(it) }.distinctBy(::homeItemKey).size)
    }

    @Test fun hiddenLikedPlaylistCannotReturnThroughAnotherAlias() {
        val saved = likedAliases.map { Playlist(it, "Liked", artworkUrl = "cover.png") }
        val feed = LoadState.Content(listOf(CatalogSection("home", "Home", saved.map { CatalogItem.Collection(it) })))
        val library = LibrarySnapshot(savedPlaylists = saved, hiddenPlaylistIds = setOf("FEmusic_liked_videos"))
        assertEquals(emptyList<QuickGridItem>(), quickGridItems(library, feed, {}, {}, {}))
    }

    @Test fun systemPlaylistsDoNotRepeatAcrossLibraryAndHome() {
        val liked = Playlist("LM", "Liked Music", artworkUrl = "liked.png")
        val episodes = Playlist("SE", "Episodes", artworkUrl = "episodes.png")
        val other = Playlist("PL123", "Otra lista", artworkUrl = "other.png")
        val library = LibrarySnapshot(savedPlaylists = listOf(liked, episodes, liked.copy(id = "VLLM")))
        val home = LoadState.Content(listOf(CatalogSection("library", "Library",
            listOf(CatalogItem.Collection(liked), CatalogItem.Collection(episodes), CatalogItem.Collection(other)))))
        val items = quickGridItems(library, home, {}, {}, {})
        assertEquals(listOf(dev.sfg.numa.mobile.ui.text.uiText("Liked music", "Música que me gusta"),
            dev.sfg.numa.mobile.ui.text.uiText("Episodes for later", "Episodios para más tarde"), "Otra lista"), items.map { it.title })
    }

    @Test fun gridDoesNotDropDistinctPlaylistsWithTheSameTitle() {
        val library = LibrarySnapshot(savedPlaylists = listOf(Playlist("one", "Prueba"), Playlist("two", "Prueba")))
        assertEquals(2, quickGridItems(library, LoadState.Content(emptyList()), {}, {}, {}).size)
    }

    @Test fun deletedPlaylistIsExcludedFromQuickGridEvenIfPresentInHomeFeed() {
        val deleted = Playlist("PL-deleted", "Deleted List", artworkUrl = "deleted.png")
        val kept = Playlist("PL-kept", "Kept List", artworkUrl = "kept.png")
        val library = LibrarySnapshot(
            savedPlaylists = listOf(kept),
            hiddenPlaylistIds = setOf("PL-deleted"),
        )
        val home = LoadState.Content(listOf(CatalogSection("recent", "Listen again",
            listOf(CatalogItem.Collection(deleted), CatalogItem.Collection(kept)))))
        val items = quickGridItems(library, home, {}, {}, {})
        assertEquals(listOf("Kept List"), items.map { it.title })
    }

    @Test fun playlistsFromHomeFeedUsePlaylistPrefix() {
        val playlist = Playlist("PL-new", "Feed Playlist", artworkUrl = "art.png")
        val library = LibrarySnapshot()
        val home = LoadState.Content(listOf(CatalogSection("feed", "Picks", listOf(CatalogItem.Collection(playlist)))))
        val items = quickGridItems(library, home, {}, {}, {})
        assertEquals(1, items.size)
        assertEquals("st_playlist:PL-new", items.first().id)
    }
}
