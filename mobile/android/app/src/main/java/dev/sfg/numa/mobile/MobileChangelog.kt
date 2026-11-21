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
            ### What's new
            - **Rosé Pine and Dawn across the app.** The player, queue, Settings and navigation use the theme palettes, with improved contrast for text and icons.
            - **Consistent mini player.** All covers are square with rounded corners, and the transport buttons use the same radius as the full player.
            - **Clearer search and video.** Search shows the selected filter, and video mode uses the theme background with a more readable quality menu and queue count.
            """.trimIndent(),
            """
            ### Novedades
            - **Rosé Pine y Dawn en toda la app.** El reproductor, la cola, Ajustes y la navegación usan las paletas del tema, con más contraste en textos e iconos.
            - **Minirreproductor más uniforme.** Todas las carátulas son cuadradas con esquinas redondeadas y los controles usan el mismo radio que el reproductor principal.
            - **Búsqueda y vídeo más legibles.** La búsqueda destaca el filtro activo y el modo de vídeo usa el fondo del tema, con un selector de calidad y un contador de cola más claros.
            """.trimIndent(),
        )
}
