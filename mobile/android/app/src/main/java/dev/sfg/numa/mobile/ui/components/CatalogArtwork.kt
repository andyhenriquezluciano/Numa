package dev.sfg.numa.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.sfg.numa.mobile.model.CatalogItem

/** Every catalog placement shares playlist identities, custom covers and defaults. */
@Composable
internal fun CatalogArtwork(item: CatalogItem, modifier: Modifier = Modifier, radius: Int = 14) {
    if (item is CatalogItem.Collection) PlaylistArtwork(item.playlist.id, item.title, item.artworkUrl, modifier)
    else ArtworkTile(item.artworkUrl, item.title, modifier, radius)
}
