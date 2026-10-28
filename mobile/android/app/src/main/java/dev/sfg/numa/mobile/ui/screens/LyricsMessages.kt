package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.lyrics.translation.LyricTranslationState
import dev.sfg.numa.mobile.ui.text.uiText
import java.util.Locale

// Resolve at display time: translation jobs can outlive an interface-language change.
private val lyricMessages = listOf(
    "Translating into English" to "Traduciendo al inglés",
    "This source needs an Internet connection." to "Esta fuente necesita conexión.",
    "Preparing the on-device translator…" to "Preparando el traductor local…",
    "On-device translator ready" to "Traductor local preparado",
    "Translating into Spanish" to "Traduciendo al español",
    "Preparing the language model…" to "Preparando el modelo del idioma…",
    "Connect to download the translation model." to "Conéctate para descargar el modelo de traducción.",
    "Lyrics could not be translated." to "No se pudo traducir la letra.",
    "This language does not support on-device translation yet." to "Este idioma todavía no admite traducción local.",
    "The model download is taking too long. Try again." to "La descarga del modelo está tardando demasiado. Vuelve a intentarlo.",
    "The language model is not ready yet." to "El modelo del idioma todavía no está preparado.",
    "Preparation paused by battery saver" to "Preparación pausada por ahorro de batería",
    "Connect to an unmetered Wi-Fi network to prepare the translator" to "Conéctate a una red Wi-Fi sin consumo medido para preparar el traductor",
    "Enter a translation model in Settings." to "Introduce un modelo de traducción en Ajustes.",
    "Add an API key in Settings." to "Añade una clave de API en Ajustes.",
    "Translation network error." to "No se pudo conectar con el servicio de traducción.",
    "Could not read translation response." to "No se pudo leer la respuesta de traducción.",
    "The model returned an invalid number of lines. Try again." to "El modelo devolvió una cantidad incorrecta de líneas. Reinténtalo.",
    "Enter a valid translation API URL." to "Introduce una dirección válida para la API de traducción.",
    "Enter an API URL without credentials or query parameters." to "Usa una dirección de API sin credenciales ni parámetros de consulta.",
    "Use HTTPS for an API key. Keyless HTTP APIs and localhost are also allowed." to "Usa HTTPS para enviar una clave de API. También se permiten HTTP sin clave y localhost.",
    "Sign in to Numa to translate lyrics." to "Inicia sesión en NÜMA para traducir letras.",
    "Sign in to Numa again." to "Vuelve a iniciar sesión en NÜMA.",
    "This song has no ID." to "Esta canción no tiene identificador.",
    "Translation took too long. Try again." to "La traducción tardó demasiado. Reinténtalo.",
    "Account translation network error." to "No se pudo conectar con la traducción de tu cuenta.",
    "Translation is busy. Try again shortly." to "El traductor está ocupado. Reinténtalo en unos instantes.",
    "The model returned invalid lyric lines. Try again." to "El modelo devolvió líneas incorrectas. Reinténtalo.",
    "The account or IP has reached 10 songs today. Resets at 00:00 UTC." to "La cuenta o la red alcanzó el límite de 10 canciones de hoy. Se restablece a las 00:00 UTC.",
    "This song is being translated. Try again shortly." to "Esta canción se está traduciendo. Reinténtalo en unos instantes.",
    "Account translation failed. Try again later." to "Falló la traducción de la cuenta. Reinténtalo más tarde.",
    "Could not read account translation." to "No se pudo leer la traducción de la cuenta.",
    "The translation returned an invalid number of lines." to "La traducción devolvió una cantidad incorrecta de líneas.",
    "Could not connect to Google Translate." to "No se pudo conectar con Google Translate.",
    "Google Translate returned an empty translation." to "Google Translate devolvió una traducción vacía.",
    "Lyrics are not available for this track." to "La letra no está disponible para esta canción.",
    "Lyrics could not be loaded." to "No se pudo cargar la letra.",
    "Finding lyrics…" to "Buscando letras…",
    "Start a song to see its lyrics." to "Reproduce una canción para ver su letra.",
    "No lyrics found." to "No se encontró la letra.",
    "Translation failed" to "No se pudo traducir",
    "Account translation failed" to "Falló la traducción de la cuenta",
    "Account translation uses the account service." to "La traducción de la cuenta usa el servicio de NÜMA.",
    "Local translation has no API endpoint." to "La traducción local no necesita una dirección de API.",
)

internal fun localizedLyricMessage(message: String): String? = lyricMessages
    .firstOrNull { message == it.first || message == it.second }?.let { uiText(it.first, it.second) }

internal fun lyricLanguageName(name: String): String {
    val aliases = mapOf("inglés" to "en", "english" to "en", "eng" to "en", "español" to "es", "spanish" to "es", "spa" to "es",
        "coreano" to "ko", "korean" to "ko", "kor" to "ko", "japonés" to "ja", "japanese" to "ja", "jpn" to "ja",
        "chino" to "zh", "chinese" to "zh", "zho" to "zh", "ruso" to "ru", "russian" to "ru", "rus" to "ru",
        "francés" to "fr", "french" to "fr", "fra" to "fr", "alemán" to "de", "german" to "de", "deu" to "de",
        "griego" to "el", "greek" to "el", "ell" to "el", "árabe" to "ar", "arabic" to "ar", "ara" to "ar",
        "turco" to "tr", "turkish" to "tr", "tur" to "tr", "italiano" to "it", "italian" to "it", "ita" to "it",
        "neerlandés" to "nl", "dutch" to "nl", "nld" to "nl", "catalán" to "ca", "catalan" to "ca", "cat" to "ca",
        "portugués" to "pt", "portuguese" to "pt", "por" to "pt")
    if (name == "la copia descargada") return uiText("the downloaded copy", name)
    val code = aliases[name.lowercase(Locale.ROOT)] ?: name.takeIf { it.length == 2 } ?: return name
    return Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
}

internal fun translationStatusMessage(state: LyricTranslationState): String {
    localizedLyricMessage(state.message)?.let { return it }
    val provider = when {
        state.message.startsWith("Translated with ") -> state.message.removePrefix("Translated with ")
        state.message.startsWith("Translating with ") -> state.message.removePrefix("Translating with ")
        else -> ""
    }.let { if (it == "Numa Account") uiText("NÜMA Account", "la cuenta NÜMA") else it }
    if (provider.isNotBlank()) return if (state.status == LyricTranslationState.Status.READY)
        uiText("Translated with $provider", "Traducido con $provider") else uiText("Translating with $provider", "Traduciendo con $provider")
    return when (state.status) {
        LyricTranslationState.Status.READY -> if (state.sourceName.isNotBlank()) {
            val language = lyricLanguageName(state.sourceName)
            uiText("Translated from $language", "Traducido de $language")
        } else uiText("Translation ready", "Traducción lista")
        LyricTranslationState.Status.DOWNLOADING -> uiText("Preparing translation…", "Preparando traducción…")
        LyricTranslationState.Status.TRANSLATING -> uiText("Translating…", "Traduciendo…")
        LyricTranslationState.Status.WAITING -> uiText("Connect to prepare the translator", "Conéctate para preparar el traductor")
        LyricTranslationState.Status.UNSUPPORTED -> uiText("Translation unavailable", "Traducción no disponible")
        LyricTranslationState.Status.FAILED -> {
            val code = Regex("""(?:HTTP |unavailable \(|disponible \(|failed \()(\d{3})""").find(state.message)?.groupValues?.get(1)
            if (code != null) uiText("Translation service unavailable (HTTP $code). Try again.", "Servicio de traducción no disponible (HTTP $code). Reinténtalo.")
            else uiText("Couldn't translate the lyrics. Check your connection and try again.", "No se pudo traducir la letra. Comprueba la conexión y reinténtalo.")
        }
        else -> ""
    }
}

internal fun lyricNoticeMessage(message: String): String = localizedLyricMessage(message)
    ?: uiText("Lyrics could not be loaded. Check your connection and try again.",
        "No se pudo cargar la letra. Comprueba la conexión y reinténtalo.")
