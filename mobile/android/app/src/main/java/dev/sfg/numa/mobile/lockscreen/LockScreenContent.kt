package dev.sfg.numa.mobile.lockscreen


import android.graphics.Bitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import dev.sfg.numa.mobile.ui.screens.FullBleedPlayerBackdrop
import dev.sfg.numa.mobile.ui.screens.rememberFullBleedPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.artwork.TrackArtwork
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.ui.components.*
import dev.sfg.numa.mobile.ui.screens.PlayerTransportControls
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.currentRosePine
import dev.sfg.numa.mobile.ui.theme.asTrackAccent

@UnstableApi
@Composable
internal fun LockScreenContent(
    state: PlaybackSnapshot,
    graph: NumaGraph,
    interactive: Boolean,
    animatedArtwork: Boolean,
    expandedAnimatedArtwork: Boolean,
    animatedBackground: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val rose = currentRosePine()
    val textColor = rose?.Text ?: Color.White
    val secondaryText = rose?.SecondaryText ?: Color.White.copy(alpha = 0.76f)
    val sectionSpacing = 12.dp
    val downloads by graph.downloads.downloads.collectAsState()
    val source = state.currentTrack
    val saved = downloads[source?.id]
    val cachedMotion = source?.let { dev.sfg.numa.mobile.download.offlineArtwork(it, saved) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val track = source?.copy(
        artworkUrl = dev.sfg.numa.mobile.download.OfflineArtwork.localUri(context, source.id)
            ?: dev.sfg.numa.mobile.download.OfflineArtwork.usableArtworkUrl(source.artworkUrl),
        animatedArtworkUrl = cachedMotion?.videoUrl.orEmpty().ifBlank { source.animatedArtworkUrl },
        animatedArtworkVerticalUrl = cachedMotion?.videoUrlVertical.orEmpty()
            .ifBlank { source.animatedArtworkVerticalUrl },
    )
    val motion by produceState<TrackArtwork?>(null, track?.id, animatedArtwork,
        track?.animatedArtworkUrl, track?.animatedArtworkVerticalUrl) {
        value = null
        if (animatedArtwork && track != null) value = if (
            track.animatedArtworkUrl.isNotBlank() || track.animatedArtworkVerticalUrl.isNotBlank()
        ) TrackArtwork(track.id, track.artworkUrl, track.animatedArtworkUrl, track.animatedArtworkVerticalUrl)
        else runCatching { graph.artwork.artwork(track) }.getOrNull()
    }
    val cover = motion?.staticUrl.orEmpty().ifBlank { track?.artworkUrl.orEmpty() }
    val coverPalette = if (rose == null) rememberArtworkPalette(cover) else null
    val sampledAccent = coverPalette?.accent
    val accent = rose?.Rose ?: remember(sampledAccent) { sampledAccent?.asTrackAccent() ?: Color.White }
    val active = LockScreenPolicy.animate(interactive, state.isPlaying)
    val renderedTrack = track?.copy(
        artworkUrl = cover,
        animatedArtworkVerticalUrl = motion?.preferredVideoUrl.orEmpty()
            .ifBlank { track.animatedArtworkVerticalUrl.ifBlank { track.animatedArtworkUrl } },
    )
    val hasMotion = renderedTrack?.animatedArtworkVerticalUrl.orEmpty().isNotBlank()
    val framed = rose != null || LockScreenPolicy.framedArtwork(animatedArtwork, expandedAnimatedArtwork, hasMotion)
    val warpSupported = rose == null && remember { dev.kawarp.KawarpEngine.isSupported() }
    val warped = warpSupported && animatedBackground && (framed || !animatedArtwork || !hasMotion)
    var videoFrame by remember(renderedTrack?.id) { mutableStateOf<Bitmap?>(null) }
    var controlsTop by remember { mutableStateOf<Float?>(null) }
    var headerBottom by remember { mutableStateOf(0f) }
    var backdropTop by remember { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { backdropTop = it.positionInRoot().y }) {
        if (rose != null) {
            Box(Modifier.fillMaxSize().background(rose.Base))
        } else if (renderedTrack != null) {
            val palette = if (framed || !hasMotion) checkNotNull(coverPalette)
                else rememberFullBleedPalette(renderedTrack, videoFrame)
            FullBleedPlayerBackdrop(
                track = renderedTrack,
                isPlaying = active,
                animatedArtworkEnabled = animatedArtwork && !framed,
                palette = palette,
                warpedArtworkEnabled = warped,
                ambientGlowEnabled = !warped,
                onVideoFrame = { videoFrame = it },
                artworkRoom = controlsTop?.minus(backdropTop),
                artworkTop = (headerBottom - backdropTop).coerceAtLeast(0f),
                fullBleedStaticArtwork = !framed,
            )
        } else {
            ArtworkBackdrop(checkNotNull(coverPalette), animated = false)
        }
        if (framed && renderedTrack != null) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val artworkTop = with(density) { (headerBottom - backdropTop).coerceAtLeast(0f).toDp() }
            val artworkBottom = with(density) { controlsTop?.minus(backdropTop)?.toDp() } ?: maxHeight * 0.56f
            val artworkSize = minOf(maxWidth - 48.dp, artworkBottom - artworkTop - sectionSpacing * 2)
            if (artworkSize > 0.dp) Box(
                Modifier.fillMaxWidth().height(artworkBottom.coerceAtLeast(0.dp)).padding(bottom = sectionSpacing),
                contentAlignment = Alignment.BottomCenter,
            ) {
                dev.sfg.numa.mobile.ui.screens.NowPlayingArtworkCard(
                    track = renderedTrack.copy(animatedArtworkUrl = motion?.videoUrl.orEmpty()
                        .ifBlank { renderedTrack.animatedArtworkUrl.ifBlank { renderedTrack.animatedArtworkVerticalUrl } }),
                    modifier = Modifier.size(artworkSize), animatedArtworkEnabled = animatedArtwork && hasMotion, isPlaying = active,
                )
            }
        }
        Row(Modifier.fillMaxWidth().onGloballyPositioned { headerBottom = it.positionInRoot().y + it.size.height }
            .statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            RoundedControlButton(onClick = onOpenApp) {
                NumaMark(Modifier.size(22.dp))
            }
            RoundedControlButton(onClick = onClose, contentColor = textColor) {
                Icon(Icons.Rounded.Close, uiText("Return to lock screen", "Volver al bloqueo"), Modifier.size(22.dp))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onGloballyPositioned { controlsTop = it.positionInRoot().y }
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track?.title ?: uiText("No song playing", "No hay ninguna canción"),
                    color = textColor, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), maxLines = 2)
                track?.artist?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = rose?.InfoInk ?: secondaryText,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
            val duration = state.durationMs.coerceAtLeast(0)
            Column {
                ThinGlassSlider(
                    value = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    enabled = duration > 0,
                    compact = true,
                    followTheme = true,
                    accent = accent,
                    description = uiText("Playback position", "Posición de reproducción"),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(state.positionMs), color = secondaryText, fontSize = 11.sp)
                    Text("-" + clock((duration - state.positionMs).coerceAtLeast(0)),
                        color = secondaryText, fontSize = 11.sp)
                }
            }
            PlayerTransportControls(
                isPlaying = state.isPlaying,
                status = state.status,
                shuffle = state.shuffle,
                repeatMode = state.repeatMode,
                localControls = true,
                onToggle = onToggle,
                onPrevious = onPrevious,
                onNext = onNext,
                onShuffle = {},
                onRepeat = {},
                showModes = false,
                accent = accent,
            )
            if (track != null) LockScreenLyrics(
                state = state,
                graph = graph,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            )
        }
    }
}

private fun clock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
