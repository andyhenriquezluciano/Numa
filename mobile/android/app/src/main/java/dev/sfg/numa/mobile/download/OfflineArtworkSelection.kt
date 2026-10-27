package dev.sfg.numa.mobile.download

import dev.sfg.numa.mobile.artwork.TrackArtwork
import dev.sfg.numa.mobile.model.Track

/** Saved rendition URLs are cache keys and must not be replaced by fresh provider URLs. */
fun offlineArtwork(track: Track, item: DownloadItem?): TrackArtwork? {
    if (item == null || item.status != DownloadStatus.COMPLETED || item.track.id != track.id) return null
    if (item.cachedAnimatedArtworkUrl.isBlank() && item.cachedAnimatedArtworkVerticalUrl.isBlank()) return null
    return TrackArtwork(
        track.id,
        OfflineArtwork.usableArtworkUrl(item.track.artworkUrl)
            .ifBlank { OfflineArtwork.usableArtworkUrl(track.artworkUrl) },
        item.cachedAnimatedArtworkUrl,
        item.cachedAnimatedArtworkVerticalUrl,
    )
}
