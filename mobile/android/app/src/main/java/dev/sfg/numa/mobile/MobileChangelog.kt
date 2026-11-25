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
            - Crystal matches controls and icons to the song's artwork.
            - Best Mix is available from playlists. Turning off Best Mix or Shuffle restores alphabetical queue order.
            - Lyrics follow vocals more closely.
            - Loading placeholders match the playlist and lyrics layouts.
            - Cover cropping has a larger preview and a grid.
            """.trimIndent(),
            """
            ### Novedades
            - Cristal adapta los controles y los iconos al color de la canción.
            - Mejor mezcla se activa desde la lista. Al salir de ese modo o de Aleatorio, la cola vuelve al orden alfabético.
            - Las letras siguen más de cerca la voz.
            - Los esqueletos de carga coinciden con el diseño de las listas y las letras.
            - Recortar carátulas es más fácil con una vista más grande y una cuadrícula.
            """.trimIndent(),
        )
}
