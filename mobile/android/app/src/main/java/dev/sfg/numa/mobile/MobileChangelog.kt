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
            - **Lyrics**: Lyrics follow the selected language. When Google web translation is unavailable, Google’s on-device translator is used; models download over Wi-Fi. Failures are shown instead of staying silent.
            - **Lock-screen player**: Reads live playback directly, even without widgets. Includes a preview in Settings.
            - **Word highlighting**: Sung lines brighten immediately and respect words that start before the paragraph boundary.
            """.trimIndent(),
            """
            ### Corregido
            - **Carátulas**: Recuperación desde la biblioteca y reintento con otras fuentes de imagen.
            - **Letras**: Letras en el idioma elegido. Si Google en línea no está disponible, usa el traductor de Google en el dispositivo; los modelos se descargan por Wi-Fi. Los fallos se muestran.
            - **Reproductor al bloquear**: Consulta la reproducción directamente, incluso sin widgets. Incluye una vista previa en Ajustes.
            - **Brillo de palabras**: La línea se ilumina de inmediato y respeta las palabras que empiezan antes del inicio del párrafo.
            """.trimIndent(),
        )
}
