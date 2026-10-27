package dev.sfg.numa.mobile.ui.text

/** Known service captions only; unknown text is preserved as catalog/user content. */
fun catalogCaption(value: String): String = when (value) {
    "New releases" -> uiText(value, "Novedades")
    "Your playlists" -> uiText(value, "Tus listas")
    "Auto playlist" -> uiText(value, "Lista automática")
    "Your queued episodes" -> uiText(value, "Tus episodios guardados")
    "From your library" -> uiText(value, "De tu biblioteca")
    "Your library" -> uiText(value, "Tu biblioteca")
    "Artist" -> uiText(value, "Artista")
    "Playlist" -> uiText(value, "Lista de reproducción")
    else -> if (value.startsWith("Subscribed on", ignoreCase = true)) {
        uiText(value, "Siguiendo en" + value.substring("Subscribed on".length))
    } else value
}
