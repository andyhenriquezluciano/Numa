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

package dev.sfg.numa.mobile.ui.components

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.model.CatalogItem
import dev.sfg.numa.mobile.model.Track
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import dev.sfg.numa.mobile.ui.theme.currentRosePine
import dev.sfg.numa.mobile.ui.glass.crystalSearchSurface
import java.text.Normalizer

private val combiningDiacritics = "\\p{InCombiningDiacriticalMarks}+".toRegex()

/**
 * Normalizes input string for search comparisons by stripping diacritics / accents
 * and converting to lowercase trimmed text.
 */
fun normalizeSearchText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(combiningDiacritics, "")
        .lowercase()
        .trim()
}

internal fun Track.searchFields(): List<String> = listOf(title, artist, album)

internal fun CatalogItem.searchFields(): List<String> = when (this) {
    is CatalogItem.Song -> track.searchFields()
    is CatalogItem.Record -> listOf(album.title, album.artist)
    is CatalogItem.Performer -> listOf(artist.name, artist.subtitle)
    is CatalogItem.Collection -> listOf(playlist.title, playlist.author, playlist.description)
    is CatalogItem.Category -> listOf(title)
}

private class CollectionSearchIndex<T>(private val values: List<T>, fields: (T) -> List<String>) {
    private val indexedFields by lazy(LazyThreadSafetyMode.NONE) {
        values.map { fields(it).map(::normalizeSearchText) }
    }

    fun matching(normalizedQuery: String): List<T> {
        if (normalizedQuery.isBlank()) return values
        return values.filterIndexed { index, _ -> indexedFields[index].any { it.contains(normalizedQuery) } }
    }
}

@Composable
internal fun <T> rememberMatching(values: List<T>, query: String, fields: (T) -> List<String>): List<T> {
    val normalized = remember(query) { normalizeSearchText(query) }
    val index = remember(values) { CollectionSearchIndex(values, fields) }
    if (normalized.isBlank()) return values
    return remember(index, normalized) { index.matching(normalized) }
}

fun filterTracks(tracks: List<Track>, query: String): List<Track> {
    val normalized = normalizeSearchText(query)
    return if (normalized.isBlank()) tracks else CollectionSearchIndex(tracks, Track::searchFields).matching(normalized)
}

fun filterCatalogItems(items: List<CatalogItem>, query: String): List<CatalogItem> {
    val normalized = normalizeSearchText(query)
    return if (normalized.isBlank()) items else CollectionSearchIndex(items, CatalogItem::searchFields).matching(normalized)
}

@Composable
fun CollectionTopSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = uiText("Find in playlist", "Buscar en la lista"),
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val rose = currentRosePine()
    val textColor = rose?.Text ?: Color.White
    val mutedColor = rose?.SecondaryText ?: Color.White.copy(alpha = 0.65f)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Back / Dismiss button
        Surface(
            onClick = onClose,
            shape = MaterialTheme.shapes.small,
            color = rose?.Overlay ?: Color.White.copy(alpha = 0.16f),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = uiText("Exit search", "Salir de la búsqueda"),
                    tint = textColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Frosted Search input container
        Surface(
            shape = if (rose == null) MaterialTheme.shapes.small else MaterialTheme.shapes.medium,
            color = rose?.Surface ?: Color.Transparent,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .crystalSearchSurface { this },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = mutedColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = mutedColor,
                            ),
                            maxLines = 1,
                        )
                    }

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                        ),
                        cursorBrush = SolidColor(LocalAccent.current),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )
                }

                if (query.isNotEmpty()) {
                    Surface(
                        onClick = { onQueryChange("") },
                        shape = MaterialTheme.shapes.extraSmall,
                        color = rose?.Overlay ?: Color.White.copy(alpha = 0.20f),
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = uiText("Clear search", "Borrar búsqueda"),
                                tint = textColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
