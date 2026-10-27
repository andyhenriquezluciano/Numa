/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.model

internal fun playlistDisplayRank(id: String): Int = when (id.removePrefix("VL")) {
    "LM", "FEmusic_liked_videos" -> 1
    "SE" -> 3
    else -> if (id.startsWith("local-playlist:")) 0 else 2
}
