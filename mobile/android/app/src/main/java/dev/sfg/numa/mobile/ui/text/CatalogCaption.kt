package dev.sfg.numa.mobile.ui.text

private val serviceCaptions = listOf(
    "New releases" to "Novedades", "Your playlists" to "Tus listas", "Auto playlist" to "Lista automática",
    "Your queued episodes" to "Tus episodios guardados", "From your library" to "De tu biblioteca",
    "Your library" to "Tu biblioteca", "Artist" to "Artista", "Playlist" to "Lista de reproducción",
    "Your daily discovery" to "Tu descubrimiento diario", "Personalized mixes" to "Mixes personalizados",
    "Listen together" to "Escuchar juntos", "Recaps" to "Recaps", "Listen again" to "Volver a escuchar",
    "Quick picks" to "Selección rápida", "Recommended for you" to "Recomendado para ti",
    "Mixed for you" to "Mezclas para ti", "Recently played" to "Reproducido recientemente",
    "Automatically created playlist" to "Lista creada automáticamente",
    "Archive mix" to "Mix de archivo", "Discover mix" to "Mix de descubrimiento",
)

/** Known service copy only. Song names and personal playlist titles are preserved. */
fun catalogCaption(value: String): String = serviceCaptions.firstOrNull {
    value.equals(it.first, true) || value.equals(it.second, true)
}?.let { uiText(it.first, it.second) } ?: when {
    value.startsWith("Lista creada automáticamente", true) -> uiText("Automatically created playlist", value)
    value.startsWith("Automatically created playlist", true) -> uiText(value, "Lista creada automáticamente")
    value.startsWith("Subscribed on", true) -> uiText(value, "Siguiendo en" + value.substring("Subscribed on".length))
    value.startsWith("Siguiendo en", true) -> uiText("Subscribed on" + value.substring("Siguiendo en".length), value)
    else -> value
}

fun playlistDisplayTitle(id: String, original: String): String = when (id.removePrefix("VL")) {
    "LM", "FEmusic_liked_videos" -> uiText("Liked music", "Música que me gusta")
    "SE" -> uiText("Episodes for later", "Episodios para más tarde")
    else -> original
}

fun navigationTitle(route: String): String = when (route) {
    dev.sfg.numa.mobile.ui.navigation.Routes.HOME -> uiText("Home", "Inicio")
    dev.sfg.numa.mobile.ui.navigation.Routes.LIBRARY -> uiText("Library", "Biblioteca")
    dev.sfg.numa.mobile.ui.navigation.Routes.ROOMS -> uiText("Rooms", "Salas")
    dev.sfg.numa.mobile.ui.navigation.Routes.SETTINGS -> uiText("Settings", "Ajustes")
    else -> route
}
