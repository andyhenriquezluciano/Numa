package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.model.EQ_PRESETS
import dev.sfg.numa.mobile.ui.text.uiText

internal fun equalizerPresetLabel(id: String): String {
    val english = EQ_PRESETS.find { it.id == id }?.label ?: "Custom"
    val spanish = when (id) {
        "flat" -> "Plano"
        "bass_boost" -> "Refuerzo de graves"
        "electronic" -> "Electrónica"
        "vocal" -> "Voz"
        "acoustic" -> "Acústico"
        "bright" -> "Brillante"
        "classical" -> "Clásica"
        else -> EQ_PRESETS.find { it.id == id }?.label ?: "Personalizado"
    }
    return uiText(english, spanish)
}
