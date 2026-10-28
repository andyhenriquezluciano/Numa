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

package dev.sfg.numa.mobile

import dev.sfg.numa.connect.BuildConfig

/** Bundled changelog and current release notes for Numa Mobile. */
object MobileChangelog {
    const val CURRENT_VERSION = BuildConfig.VERSION_NAME
    const val CURRENT_CODENAME = BuildConfig.CODENAME

    val CURRENT_RELEASE_NOTES: String
        get() = dev.sfg.numa.mobile.ui.text.uiText(
            """
            ### Fixed
            - **Artwork**: Recover missing covers from library metadata and retry alternative image sources.
            - **Lyrics**: Lyrics follow the selected English or Spanish app language, with separate caches. Navigation, default playlists, catalog headings, settings and widgets use that language. System language detection was removed.
            - **Lock-screen player**: Open Android permission settings on every activation or deactivation. Close the player immediately when disabled.
            """.trimIndent(),
            """
            ### Corregido
            - **Carátulas**: Recuperación desde la biblioteca y reintento con otras fuentes de imagen.
            - **Letras**: Traducción al inglés o español según el idioma elegido, con cachés separadas. Navegación, listas predeterminadas, encabezados, ajustes y widgets en ese idioma. Eliminada la detección del idioma del sistema.
            - **Reproductor al bloquear**: Ajustes de permiso de Android en cada activación o desactivación y cierre inmediato al desactivarlo.
            """.trimIndent(),
        )
}
