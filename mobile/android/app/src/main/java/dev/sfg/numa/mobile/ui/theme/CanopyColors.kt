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

package dev.sfg.numa.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Numa Mobile theme palette - inspired by SimpMusic visual design.
 * Rich dark AMOLED surfaces, vibrant accents, dynamic color overlays, and semantic tokens.
 */
object CanopyColors {
    /** Main app background (AMOLED dark tone). */
    val Chrome = Color(0xFF0B0E11)

    /** Secondary background / surface container low. */
    val Canvas = Color(0xFF13171F)

    /** Card / container background. */
    val Surface = Color(0xFF16181B)
    val SurfaceHover = Color(0xFF202326)
    val ReadoutBg = Color(0xFF222935)

    /** Borders and dividers. */
    val Rule = Color(0xFFFFFFFF).copy(alpha = 0.08f)
    val RuleStrong = Color(0xFFFFFFFF).copy(alpha = 0.18f)

    /** Muted labels and subtitles. */
    val Eyebrow = Color(0xFF9AA7B4)

    /** Primary NÜMA brand accent. */
    val Accent = Color(0xFFA78BFA)
    val AccentDeep = Color(0xFF6542A6)

    /** Secondary brand accent (soft electric blue). */
    val SecondaryAccent = Color(0xFFD8C5FF)

    /** Text colors. */
    val Text = Color(0xFFF0EEE7)
    val Muted = Color(0xFF9AA7B4)
    val MutedStrong = Color(0xFFC5CFD8)

    /** Semantic colors. */
    val Favorite = Color(0xFFFF4081)
    val LyricActive = Color(0xFFFFE500)
    val ShimmerBackground = Color(0x33FFFFFF)
    val ShimmerLine = Color(0x66FFFFFF)

    /** Overlays and backdrop colors. */
    val Overlay = Color(0x40000000)
    val PlayerBackdrop = Color(0xFF121212)

    /**
     * Frosted panes. [Glass] is the film laid over a pane's blurred backdrop, and is nearly white
     * on purpose — the film goes on at a fraction of full opacity, so anything darker just reads
     * as a grey card sitting on the blur rather than as frost in it. [GlassChrome] is the darker
     * film under bars, which have a scrolling list passing behind them to stay legible against.
     */
    val Glass = Color(0xFF14171A)
    val GlassSecondary = Color(0xFF14171A)
    val GlassChrome = Color(0xFF14171A)
    val GlassOverlay = Color(0xFF14171A)

    /** Subtle architectural glass perimeter borders. */
    val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.10f)
    val GlassBorderHighlight = Color(0xFFFFFFFF).copy(alpha = 0.22f)

    val Warning = Color(0xFFFFC46B)
    val Danger = Color(0xFFFF746D)
}

