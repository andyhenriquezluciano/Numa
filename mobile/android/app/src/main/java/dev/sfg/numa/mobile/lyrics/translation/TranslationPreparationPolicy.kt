package dev.sfg.numa.mobile.lyrics.translation

data class TranslationConditions(val online: Boolean, val metered: Boolean, val powerSave: Boolean) {
    val allowPreparation: Boolean get() = online && !metered && !powerSave
    fun allowModelDownload(foreground: Boolean): Boolean = online && (foreground || allowPreparation)
    val waitingMessage: String get() = if (powerSave) "Preparación pausada por ahorro de batería"
        else "Conéctate a una red Wi-Fi sin consumo medido para preparar el traductor"
}

internal fun missingTranslationModels(source: String, installed: Set<String>, target: String = "es"): Set<String> =
    if (source == target) emptySet() else setOf(source, target) - "en" - installed
