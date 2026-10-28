/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.Normalizer
import java.util.Locale

/** Pages under the settings hub. Home has no title bar blurb. */
internal enum class SettingsPage(val title: String, val blurb: String, val icon: ImageVector?) {
    Home("Ajustes", "", null),
    Accounts("Cuentas", "Cuentas, historial y sincronización.", Icons.Rounded.Person),
    Lyrics("Letras y traducción", "Motor de traducción y modelos sin conexión.", Icons.Rounded.Translate),
    Audio("Reproducción", "Calidad, volumen, transiciones y cola.", Icons.Rounded.GraphicEq),
    Appearance("Apariencia", "Tema, bordes, carátulas y fondos.", Icons.Rounded.Palette),
    Connections("Integraciones", "Servicios externos y dispositivos de reproducción.", Icons.Rounded.Link),
    Storage("Descargas", "Calidad, carátulas y almacenamiento sin conexión.", Icons.Rounded.Storage),
}

@androidx.compose.runtime.Composable
internal fun SettingsPage.displayTitle(): String = dev.sfg.numa.mobile.ui.text.uiText(when (this) {
    SettingsPage.Home -> "Settings"
    SettingsPage.Accounts -> "Account and backup"
    SettingsPage.Lyrics -> "Lyrics and translation"
    SettingsPage.Audio -> "Playback"
    SettingsPage.Storage -> "Downloads"
    SettingsPage.Appearance -> "Appearance"
    SettingsPage.Connections -> "Integrations"
}, title)


internal fun SettingsPage.displayBlurb(): String = dev.sfg.numa.mobile.ui.text.uiText(when (this) {
    SettingsPage.Home -> ""
    SettingsPage.Accounts -> "Accounts, history and synchronization."
    SettingsPage.Lyrics -> "Translation service and offline language models."
    SettingsPage.Audio -> "Quality, volume, transitions and queue."
    SettingsPage.Appearance -> "Theme, borders, artwork and backgrounds."
    SettingsPage.Connections -> "External services and playback devices."
    SettingsPage.Storage -> "Quality, artwork and offline storage."
}, blurb)

internal fun lyricSettingsText(value: String): String = dev.sfg.numa.mobile.ui.text.uiText(when (value) {
    "Traducir letras" -> "Translate lyrics"
    "Muestra las letras en español" -> "Show lyrics in Spanish"
    "Calidad de traducción" -> "Translation quality"
    "Modelos de traducción" -> "Translation models"
    "Fuente de traducción" -> "Translation source"
    "Local o Google Translate" -> "On-device or Google Translate"
    else -> value
}, value)

/** One searchable setting; [keywords] catches words the title does not contain. */
internal data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val page: SettingsPage,
    val keywords: String = "",
)

private val entries = listOf(
    SettingsEntry("Idioma de la interfaz", "Sistema, español o inglés", SettingsPage.Home, "language idioma"),
    SettingsEntry("Calidad de audio", "Desde menor consumo hasta mayor tasa de bits", SettingsPage.Audio, "bitrate stream datos ahorro"),
    SettingsEntry("Ecualizador", "Ajusta el sonido con preajustes o bandas", SettingsPage.Audio, "eq graves agudos"),
    SettingsEntry("Mostrar tasa de bits", "Tasa de transmisión bajo la barra de progreso", SettingsPage.Appearance),
    SettingsEntry("Normalización de volumen", "Equilibra el volumen entre canciones", SettingsPage.Audio, "volumen replaygain"),
    SettingsEntry("Volumen exponencial", "Control más preciso a volumen bajo", SettingsPage.Audio, "botones multimedia"),
    SettingsEntry("Reproducción automática", "Continúa con música relacionada", SettingsPage.Audio, "cola radio"),
    SettingsEntry("Orden de reproducción", "Original, aleatorio o Mejor mezcla", SettingsPage.Audio, "cola ritmo tonalidad ordenar"),
    SettingsEntry("Mezcla adaptativa", "Transición ajustada al audio", SettingsPage.Audio, "ritmo fundido"),
    SettingsEntry("Cuentas", "Google, historial y sincronización", SettingsPage.Accounts, "cuenta conectar biblioteca sincronizar"),
    SettingsEntry("Calidad de descarga", "Calidad para nuevos archivos sin conexión", SettingsPage.Storage, "datos wifi"),
    SettingsEntry("Fundido cruzado", "Mezcla el final de una canción con la siguiente", SettingsPage.Audio, "mezcla adaptativa transición"),
    SettingsEntry("Chromecast", "Transmite a altavoces y televisores", SettingsPage.Connections, "cast tv altavoz"),
    SettingsEntry("Diseño de Inicio", "Reordena y oculta secciones", SettingsPage.Appearance, "secciones"),
    SettingsEntry("Gestos del reproductor", "Desliza para cambiar y toca para marcar Me gusta", SettingsPage.Audio),
    SettingsEntry("Reproductor de NÜMA al bloquear", "Carátula animada y controles sobre el bloqueo", SettingsPage.Audio, "bloqueo pantalla pin huella"),
    SettingsEntry("Carátula animada", "Anima la carátula durante la reproducción", SettingsPage.Appearance, "movimiento portada"),
    SettingsEntry("Fuentes de carátulas", "Elige el orden de las fuentes animadas", SettingsPage.Appearance, "m8tec boidu spotify canvas"),
    SettingsEntry("Descargar carátula animada", "Guarda portadas en movimiento sin conexión", SettingsPage.Storage),
    SettingsEntry("Fondo animado", "Los colores de la carátula se mueven por la aplicación", SettingsPage.Appearance, "fondo"),
    SettingsEntry("Traducir letras", "Muestra las letras en español", SettingsPage.Lyrics, "traducción idioma español"),
    SettingsEntry("Calidad de traducción", "Modelos de traducción", SettingsPage.Lyrics, "letras modelo tamaño"),
    SettingsEntry("Fuente de traducción", "Local o Google Translate", SettingsPage.Lyrics, "letras google proveedor"),
    SettingsEntry("Discord", "Comparte lo que estás escuchando", SettingsPage.Connections, "presencia"),
    SettingsEntry("Last.fm", "Actualiza la canción y envía reproducciones", SettingsPage.Connections, "scrobble"),
    SettingsEntry("ListenBrainz", "Envía reproducciones con un token", SettingsPage.Connections, "scrobble"),
    SettingsEntry("Spotify", "Carátulas animadas de Spotify Canvas", SettingsPage.Connections, "canvas cookie"),
    SettingsEntry("Qobuz", "Audio sin pérdida y de alta resolución", SettingsPage.Connections, "hires streaming"),
    SettingsEntry("Cuenta de NÜMA", "Inicia sesión con Google y administra dispositivos", SettingsPage.Accounts, "cuenta dispositivos google"),
    SettingsEntry("Cuenta de YouTube Music", "Iniciar sesión, cambiar cuenta o cerrar sesión", SettingsPage.Accounts, "google acceso"),
    SettingsEntry("Guardar en el historial de YouTube Music", "Añade a tu historial las canciones reproducidas", SettingsPage.Accounts, "historial"),
    SettingsEntry("Audio en caché", "Límite para las canciones reproducidas", SettingsPage.Storage, "tamaño límite"),
    SettingsEntry("Borrar caché", "Libera audio y carátulas temporales", SettingsPage.Storage, "almacenamiento eliminar"),
)

/** Case-insensitive match on every word of [query] against title, subtitle and keywords. */
internal fun searchSettings(query: String): List<SettingsEntry> {
    val words = query.searchKey().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries.filter { entry ->
        if (entry.title == "Last.fm" && dev.sfg.numa.connect.BuildConfig.NUMA_LASTFM_URL.isBlank()) return@filter false
        val haystack = "${entry.title} ${entry.subtitle} ${lyricSettingsText(entry.title)} ${lyricSettingsText(entry.subtitle)} ${entry.keywords} ${entry.page.title}".searchKey()
        words.all { it in haystack }
    }
}

private fun String.searchKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
