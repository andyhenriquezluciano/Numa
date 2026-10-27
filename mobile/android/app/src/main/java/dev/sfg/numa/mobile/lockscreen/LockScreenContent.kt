package dev.sfg.numa.mobile.lockscreen


import android.graphics.Bitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import dev.sfg.numa.mobile.ui.screens.FullBleedPlayerBackdrop
import dev.sfg.numa.mobile.ui.screens.rememberFullBleedPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.artwork.TrackArtwork
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.ui.components.*
import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.CanopyColors

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
    val active = LockScreenPolicy.animate(interactive, state.isPlaying)
    val renderedTrack = track?.copy(
        artworkUrl = cover,
        animatedArtworkVerticalUrl = motion?.preferredVideoUrl.orEmpty()
            .ifBlank { track.animatedArtworkVerticalUrl.ifBlank { track.animatedArtworkUrl } },
    )
    val hasMotion = renderedTrack?.animatedArtworkVerticalUrl.orEmpty().isNotBlank()
    val framed = LockScreenPolicy.framedArtwork(animatedArtwork, expandedAnimatedArtwork, hasMotion)
    val warpSupported = remember { dev.kawarp.KawarpEngine.isSupported() }
    val warped = warpSupported && animatedBackground && (framed || !animatedArtwork || !hasMotion)
    var videoFrame by remember(renderedTrack?.id) { mutableStateOf<Bitmap?>(null) }
    var controlsTop by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val footerSpace = (maxHeight * 0.10f).coerceAtLeast(40.dp)
        if (renderedTrack != null) {
            val palette = if (framed || !hasMotion) rememberArtworkPalette(cover)
                else rememberFullBleedPalette(renderedTrack, videoFrame)
            FullBleedPlayerBackdrop(
                track = renderedTrack,
                isPlaying = active,
                animatedArtworkEnabled = animatedArtwork && !framed,
                palette = palette,
                warpedArtworkEnabled = warped,
                ambientGlowEnabled = !warped,
                onVideoFrame = { videoFrame = it },
                artworkRoom = controlsTop,
                fullBleedStaticArtwork = !framed,
            )
        } else {
            ArtworkBackdrop(rememberArtworkPalette(cover), animated = false)
        }
        if (framed && renderedTrack != null) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val room = with(density) { (controlsTop ?: (maxHeight.toPx() * 0.56f)).toDp() }
            val artworkSize = minOf(maxWidth - 48.dp, (room - 88.dp).coerceAtLeast(48.dp))
            Box(Modifier.fillMaxWidth().height(room).padding(top = 64.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center) {
                dev.sfg.numa.mobile.ui.screens.NowPlayingArtworkCard(
                    track = renderedTrack.copy(animatedArtworkUrl = motion?.videoUrl.orEmpty()
                        .ifBlank { renderedTrack.animatedArtworkUrl.ifBlank { renderedTrack.animatedArtworkVerticalUrl } }),
                    modifier = Modifier.size(artworkSize), animatedArtworkEnabled = animatedArtwork && hasMotion, isPlaying = active,
                )
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onOpenApp) {
                NumaMark(Modifier.size(22.dp))
            }
            IconButton(onClick = onClose, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                Icon(Icons.Rounded.Close, uiText("Return to lock screen", "Volver al bloqueo"), Modifier.size(22.dp))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onGloballyPositioned { controlsTop = it.positionInRoot().y }
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(track?.title ?: uiText("No song playing", "No hay ninguna canción"),
                color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), maxLines = 2)
            Text(track?.artist.orEmpty(), color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            val duration = state.durationMs.coerceAtLeast(0)
            ThinGlassSlider(
                value = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { onSeek((it * duration).toLong()) },
                enabled = duration > 0, compact = true,
                modifier = Modifier.offset(y = 10.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(state.positionMs), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                Text("-" + clock((duration - state.positionMs).coerceAtLeast(0)),
                    color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onPrevious, modifier = Modifier.weight(1f).height(50.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
                    Icon(Icons.Rounded.SkipPrevious, uiText("Previous", "Anterior"), Modifier.size(22.dp))
                }
                FilledIconButton(onClick = onToggle, modifier = Modifier.weight(1f).height(50.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF101214))) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) uiText("Pause", "Pausar") else uiText("Play", "Reproducir"),
                        Modifier.size(25.dp))
                }
                FilledIconButton(onClick = onNext, modifier = Modifier.weight(1f).height(50.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
                    Icon(Icons.Rounded.SkipNext, uiText("Next", "Siguiente"), Modifier.size(22.dp))
                }
            }
            LockScreenLyrics(
                state = state,
                graph = graph,
                modifier = Modifier.fillMaxWidth().height(footerSpace).padding(top = 12.dp),
            )
        }
    }
}

private fun clock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
