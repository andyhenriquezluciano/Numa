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
            - **Controls with rounded corners.** Play, Pause, Shuffle and Back use the same corner radius as NÜMA's main controls. The selection bar and track menu actions follow the same style.
            """.trimIndent(),
            """
            ### Novedades
            - **Controles con esquinas redondeadas.** Reproducir, Pausar, Aleatorio y Volver usan el mismo radio que los controles principales de NÜMA. La barra de selección y las acciones del menú de canciones siguen ese estilo.
            """.trimIndent(),
        )
}
