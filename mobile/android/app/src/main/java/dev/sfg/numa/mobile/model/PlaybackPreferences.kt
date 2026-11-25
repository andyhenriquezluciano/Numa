package dev.sfg.numa.mobile.model

enum class PlaybackOrder(val label: String) {
    ORIGINAL("Original"), SHUFFLE("Aleatorio"), BEST_MIX("Mejor mezcla"),
}

enum class TransitionPreference(val label: String) {
    OFF("Desactivada"), CROSSFADE("Fundido cruzado"), ADAPTIVE("Adaptativa"),
}

val NumaSettings.transitionPreference: TransitionPreference
    get() = when {
        !crossfadeEnabled -> TransitionPreference.OFF
        smartCrossfade -> TransitionPreference.ADAPTIVE
        else -> TransitionPreference.CROSSFADE
    }

fun NumaSettings.withTransition(preference: TransitionPreference): NumaSettings = copy(
    crossfadeEnabled = preference != TransitionPreference.OFF,
    smartCrossfade = preference == TransitionPreference.ADAPTIVE,
)

internal fun effectivePlaybackOrder(preferred: PlaybackOrder, explicitShuffle: Boolean): PlaybackOrder =
    if (explicitShuffle) PlaybackOrder.SHUFFLE else preferred

internal fun PlaybackSnapshot.needsBestMixOnResume(preferred: PlaybackOrder): Boolean =
    preferred == PlaybackOrder.BEST_MIX && !alphabeticalQueue && !isPlaying && currentTrack != null && upcoming.size > 1 &&
        (shuffle || !contextTitle.isBestMixTitle())

internal fun String.isBestMixTitle(): Boolean =
    equals("Best Mix", ignoreCase = true) || endsWith(" • Best Mix", ignoreCase = true)
