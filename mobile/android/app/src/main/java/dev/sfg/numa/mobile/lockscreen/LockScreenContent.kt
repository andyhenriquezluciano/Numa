package dev.sfg.numa.mobile.lockscreen


import android.graphics.Bitmap
import android.os.SystemClock
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.artwork.TrackArtwork
import dev.sfg.numa.mobile.model.PlaybackSnapshot
import dev.sfg.numa.mobile.model.clock
import dev.sfg.numa.mobile.model.withoutClock
import dev.sfg.numa.mobile.ui.components.*
import dev.sfg.numa.mobile.ui.screens.NowPlayingArtworkCard
import dev.sfg.numa.mobile.ui.screens.PhonePlayerBottomPadding
import dev.sfg.numa.mobile.ui.screens.PlayerDestinationsGap
import dev.sfg.numa.mobile.ui.screens.PlayerDestinationsHeight
import dev.sfg.numa.mobile.ui.screens.PlayerScrubber
import dev.sfg.numa.mobile.ui.screens.PlayerTransportControls
import dev.sfg.numa.mobile.ui.screens.PlayerTransportGap
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.currentRosePine
import dev.sfg.numa.mobile.ui.theme.LocalTrackAccent
import dev.sfg.numa.mobile.ui.theme.asTrackAccent

@UnstableApi
@Composable
internal fun LockScreenContent(
    snapshot: State<PlaybackSnapshot>,
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
    val state by remember(snapshot) {
        derivedStateOf(structuralEqualityPolicy()) { snapshot.value.withoutClock() }
    }
    val screenInteractive by rememberUpdatedState(interactive)
    val clock = remember(snapshot) {
        derivedStateOf {
            val sample = snapshot.value.clock(SystemClock.elapsedRealtime())
            sample.copy(playing = screenInteractive && sample.playing)
        }
    }
    val rose = currentRosePine()
    val textColor = rose?.Text ?: Color.White
    val secondaryText = rose?.SecondaryText ?: Color.White.copy(alpha = 0.76f)
    val downloads by graph.downloads.downloads.collectAsState()
    val source = state.currentTrack
    val saved = downloads[source?.id]
    val context = androidx.compose.ui.platform.LocalContext.current
    val track = remember(context, source, saved) {
        val cachedMotion = source?.let { dev.sfg.numa.mobile.download.offlineArtwork(it, saved) }
        source?.copy(
            artworkUrl = dev.sfg.numa.mobile.download.OfflineArtwork.localUri(context, source.id)
                ?: dev.sfg.numa.mobile.download.OfflineArtwork.usableArtworkUrl(source.artworkUrl),
            animatedArtworkUrl = cachedMotion?.videoUrl.orEmpty().ifBlank { source.animatedArtworkUrl },
            animatedArtworkVerticalUrl = cachedMotion?.videoUrlVertical.orEmpty()
                .ifBlank { source.animatedArtworkVerticalUrl },
        )
    }
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
    Box(Modifier.fillMaxSize().onGloballyPositioned { backdropTop = it.positionInRoot().y }) {
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
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(bottom = PhonePlayerBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .onGloballyPositioned { headerBottom = it.positionInRoot().y + it.size.height }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundedControlButton(onClick = onOpenApp) {
                    NumaMark(Modifier.size(22.dp))
                }
                RoundedControlButton(onClick = onClose, contentColor = textColor) {
                    Icon(Icons.Rounded.Close, uiText("Return to lock screen", "Volver al bloqueo"), Modifier.size(22.dp))
                }
            }
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (framed && renderedTrack != null) {
                    NowPlayingArtworkCard(
                        track = renderedTrack.copy(animatedArtworkUrl = motion?.videoUrl.orEmpty()
                            .ifBlank { renderedTrack.animatedArtworkUrl.ifBlank { renderedTrack.animatedArtworkVerticalUrl } }),
                        modifier = Modifier.size(minOf(maxWidth, maxHeight)),
                        animatedArtworkEnabled = animatedArtwork && hasMotion,
                        isPlaying = active,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .onGloballyPositioned { controlsTop = it.positionInRoot().y }
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        track?.title ?: uiText("No song playing", "No hay ninguna canción"),
                        color = textColor,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    track?.let {
                        Text(
                            it.artist,
                            color = rose?.InfoInk ?: secondaryText,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (track != null) {
                    LockScreenLyrics(
                        state = state,
                        clock = clock,
                        graph = graph,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 44.dp),
                    )
                } else {
                    Spacer(Modifier.height(24.dp))
                }
                val displayState = state.copy(isPlaying = active)
                val presentation = rememberPlayerPresentation(displayState, clock, marker = null)
                CompositionLocalProvider(LocalPlayerClock provides presentation.clock, LocalTrackAccent provides accent) {
                    PlayerScrubber(playback = displayState, onSeek = { if (state.durationMs > 0) onSeek(it) })
                    Spacer(Modifier.height(PlayerTransportGap))
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
                }
                // Reserve the same space the destination bar uses in the full player.
                Spacer(Modifier.height(PlayerDestinationsGap + PlayerDestinationsHeight))
            }
        }
    }
}
