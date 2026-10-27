/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */

package dev.sfg.numa.mobile.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
internal inline fun publishAdoptedPlayer(
    incoming: ExoPlayer, recover: () -> Unit, snapshot: (Player) -> Unit,
    resetArtwork: () -> Unit, hydrateArtwork: () -> Unit, publishBitrate: () -> Unit,
    scrobble: (Player) -> Unit,
) {
    recover()
    snapshot(incoming)
    resetArtwork()
    hydrateArtwork()
    publishBitrate()
    scrobble(incoming)
}
