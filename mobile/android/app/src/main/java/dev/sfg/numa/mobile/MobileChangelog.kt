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
            - **More resilient artwork**: Retry alternate sources and keep a placeholder when an image does not respond.
            - **Notifications**: Tapping the player opens NÜMA directly.
            - **Shared rooms**: Consistent button radii and a more comfortable layout.
            - **Appearance**: Removed playlist background effects and reduced background animation work.
            - **Local playlists**: Keep them device-only and recover their artwork more reliably.
            """.trimIndent(),
            """
            ### Corregido
            - **Carátulas más resistentes**: Reintenta fuentes alternativas y mantiene un marcador cuando una imagen no responde.
            - **Notificaciones**: Al tocar el reproductor, NÜMA abre directamente la aplicación.
            - **Salas compartidas**: Botones con radios coherentes y una distribución más cómoda.
            - **Apariencia**: Se retiró el fondo dinámico de listas y se redujo el trabajo de animaciones en segundo plano.
            - **Listas locales**: Se mantienen exclusivas del dispositivo y sus carátulas se recuperan con más fiabilidad.
            """.trimIndent(),
        )
}
