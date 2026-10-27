package dev.sfg.numa.mobile.playback

internal data class PlayerModeAvailability(val song: Boolean, val video: Boolean)

internal fun playerModeAvailability(
    videoAvailable: Boolean,
    videoPlaying: Boolean,
    checking: Boolean,
    localOutput: Boolean,
    controlAllowed: Boolean,
    casting: Boolean,
): PlayerModeAvailability = PlayerModeAvailability(
    song = controlAllowed && (!videoPlaying || localOutput),
    video = controlAllowed && localOutput && !casting && (videoPlaying || (videoAvailable && !checking)),
)
