package dev.sfg.numa.mobile.model

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

internal fun String.isBestMixTitle(): Boolean =
    equals("Best Mix", ignoreCase = true) || endsWith(" • Best Mix", ignoreCase = true)
