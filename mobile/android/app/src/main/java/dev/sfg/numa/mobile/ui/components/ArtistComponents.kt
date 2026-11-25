/*
 * Copyright (C) 2026 SFG545
 * Copyright (C) 2026 Convx Project contributors
 *
 * This file is part of Numa.
 *
 * Layout adapted from the artist header and sections in ArtistScreen
 * (app/src/main/kotlin/com/convx/music/ui/screens/artist/ArtistScreen.kt) in
 * Convx v1.5.2, https://github.com/cosmictaserdev-creator/Convx, licensed under the
 * GNU General Public License version 3. It is combined with Numa under section 13
 * of the GNU GPL v3 and GNU AGPL v3.
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

package dev.sfg.numa.mobile.ui.components

import dev.sfg.numa.mobile.ui.theme.currentRosePine


import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.model.BrowseDetail
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.playbackAccent
import dev.sfg.numa.mobile.ui.theme.trackControlInk

/** Artist hero: faded portrait, drop-cap name, shuffle / play / follow, listener chip, About. */
@Composable
fun ArtistHero(
    detail: BrowseDetail,
    onPlayAll: (List<Track>, String) -> Unit,
    onShuffle: (List<Track>, String) -> Unit,
    shuffleAvailable: Boolean,
    shuffleActive: Boolean,
    onSave: (BrowseDetail) -> Unit,
    isSaved: Boolean,
) {
    val rose = currentRosePine()
    val accent = playbackAccent()
    val name = detail.title.trim()
    DetailHeroFrame(artworkUrl = detail.artworkUrl, description = name) {
        Text(
            text = buildAnnotatedString {
                // Drop cap: illuminated manuscripts did it first, without a Compose dependency.
                if (name.isNotEmpty()) {
                    withStyle(SpanStyle(fontSize = 60.sp)) { append(name.first().uppercase()) }
                    append(name.drop(1))
                }
            },
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 44.sp,
                lineHeight = 52.sp,
                letterSpacing = (-1).sp,
            ),
            color = rose?.AccentInk ?: accent,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        HeroPlayRow(
            onPlay = { onPlayAll(detail.tracks, detail.title) },
            playEnabled = detail.tracks.isNotEmpty(),
            playFill = accent,
            playIconTint = accent.trackControlInk(),
            sideDrop = 8.dp,
            leading = {
                HeroShuffleButton(
                    onClick = { onShuffle(detail.tracks, detail.title) },
                    active = shuffleActive,
                    enabled = detail.tracks.isNotEmpty() && shuffleAvailable,
                )
            },
            trailing = {
                HeroActionButton(
                    onClick = { onSave(detail) },
                    icon = if (isSaved) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isSaved) uiText("Unfollow artist", "Dejar de seguir al artista") else uiText("Follow artist", "Seguir artista"),
                    tint = if (isSaved) rose?.FavoriteInk ?: dev.sfg.numa.mobile.ui.theme.playbackAccent() else rose?.SecondaryText ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        // Counts and bio sit under the controls so a long bio never pushes Play off the fold.
        if (detail.subtitle.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            HeroChip(text = detail.subtitle, icon = Icons.Rounded.GraphicEq)
        }
        if (detail.description.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            HeroAbout(title = uiText("About", "Acerca de"), text = detail.description)
        }
    }
}

@Composable
fun DetailBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier.padding(start = 8.dp, top = 8.dp), shape = MaterialTheme.shapes.small) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = uiText("Back", "Volver"), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
    }
}
