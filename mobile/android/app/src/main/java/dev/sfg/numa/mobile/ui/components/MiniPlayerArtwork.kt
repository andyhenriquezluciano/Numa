package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.model.playerMotionArtwork

@Composable
internal fun MiniPlayerArtwork(
    track: Track,
    animatedArtworkEnabled: Boolean,
    isPlaying: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val decodePixels = with(LocalDensity.current) { size.roundToPx() * 2 }
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        RemoteArtwork(
            track.artworkUrl, track.title, Modifier.fillMaxSize(),
            fallbackUrl = dev.sfg.numa.mobile.artwork.trackArtworkFallbackUrl(track),
            decodePixels = decodePixels,
            preferLargeVideoFrame = true,
        )
        if (animatedArtworkEnabled && isPlaying) {
            AnimatedArtworkVideo(track.playerMotionArtwork(), active = true, modifier = Modifier.fillMaxSize())
        }
    }
}
