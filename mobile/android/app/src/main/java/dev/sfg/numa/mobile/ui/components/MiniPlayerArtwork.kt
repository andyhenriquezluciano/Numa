package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.model.playerMotionArtwork

@Composable
internal fun MiniPlayerArtwork(
    track: Track,
    animatedArtworkEnabled: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        RemoteArtwork(dev.sfg.numa.mobile.artwork.trackArtworkUrl(track), track.title, Modifier.fillMaxSize())
        if (animatedArtworkEnabled) {
            AnimatedArtworkVideo(track.playerMotionArtwork(), isPlaying, Modifier.fillMaxSize())
        }
    }
}
