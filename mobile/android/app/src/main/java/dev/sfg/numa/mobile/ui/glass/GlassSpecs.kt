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

package dev.sfg.numa.mobile.ui.glass

import androidx.compose.ui.graphics.Color
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.model.GlassTheme
import dev.sfg.numa.mobile.ui.theme.RosePineColors

/**
 * PopupBloomSurface film and SettingsButton film, including the first frame without a capture.
 */
internal class GlassSpec(
    val base: Color,
    val film: Float,
    val tintMix: Float,
    val contrastUndercoat: Float,
    val solidBase: Color,
    val solidFilm: Float,
    val solidTintMix: Float,
    val solidUndercoat: Float,
    val borderAlpha: Float = 1f,
)

private fun buildSpec(
    theme: GlassTheme,
    borders: GlassBorders,
    isControl: Boolean,
): GlassSpec {
    val borderAlpha = if (borders == GlassBorders.LIMPIO) 0f else 1f
    return when (theme) {
        GlassTheme.CRISTAL -> {
            val film = 0.12f
            val undercoat = 0.04f
            val solidFilm = 0.25f
            val solidUndercoat = 0.10f
            GlassSpec(
                base = Color(0xFF14171A), film = film, tintMix = 0.02f, contrastUndercoat = undercoat,
                solidBase = Color(0xFF14171A), solidFilm = solidFilm, solidTintMix = 0.02f, solidUndercoat = solidUndercoat,
                borderAlpha = borderAlpha,
            )
        }
        GlassTheme.CRISTAL_OSCURO -> {
            val film = 0.40f
            val undercoat = 0.14f
            val solidFilm = 0.60f
            val solidUndercoat = 0.22f
            GlassSpec(
                base = Color(0xFF0C0E11), film = film, tintMix = 0.04f, contrastUndercoat = undercoat,
                solidBase = Color(0xFF0C0E11), solidFilm = solidFilm, solidTintMix = 0.04f, solidUndercoat = solidUndercoat,
                borderAlpha = borderAlpha,
            )
        }
        GlassTheme.ROSE_PINE -> {
            val base = if (isControl) RosePineColors.Overlay else RosePineColors.Surface
            GlassSpec(
                base = base, film = 1f, tintMix = 0f, contrastUndercoat = 0f,
                solidBase = RosePineColors.Base, solidFilm = 1f, solidTintMix = 0f, solidUndercoat = 0f,
                borderAlpha = borderAlpha,
            )
        }
    }
}

internal fun GlassTone.spec(
    theme: GlassTheme = GlassTheme.CRISTAL,
    borders: GlassBorders = GlassBorders.PREDETERMINADO,
): GlassSpec = buildSpec(theme, borders, isControl = this == GlassTone.CONTROL)
