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

package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.app.MusicVideoState
import dev.sfg.numa.mobile.ui.glass.GlassDropdownMenu
import dev.sfg.numa.mobile.ui.glass.DesktopGlass
import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.CanopyColors

@Composable
internal fun VideoQualityButton(
    state: MusicVideoState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMenuOpenChange: (Boolean) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { onMenuOpenChange(open) }
    DisposableEffect(Unit) { onDispose { onMenuOpenChange(false) } }
    val choices = listOf(0) + state.heights.ifEmpty { CommonHeights }.distinct().sortedDescending()
    Box(modifier) {
        Row(Modifier.desktopButtonSurface()
            .clickable(onClickLabel = uiText("Video quality", "Calidad de vídeo")) { open = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, null, tint = Color.White, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            Text(if (state.height > 0) heightLabel(state.height) else uiText("Quality", "Calidad"),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        GlassDropdownMenu(open, { open = false }, modifier = Modifier.width(280.dp).heightIn(max = 280.dp)) {
            Text(uiText("Video quality", "Calidad de vídeo"), color = DesktopGlass.Text,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            choices.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { height ->
                        QualityChoice(
                            label = if (height == 0) uiText("Automatic", "Automática") else heightLabel(height),
                            chosen = state.maxHeight == height,
                            modifier = Modifier.weight(1f),
                        ) { open = false; onSelect(height) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QualityChoice(label: String, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
        .background(if (chosen) DesktopGlass.ItemHighlight else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = DesktopGlass.Text, fontSize = 14.sp,
            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        if (chosen) Icon(Icons.Rounded.Check, uiText("Selected", "Seleccionado"),
            modifier = Modifier.size(18.dp), tint = CanopyColors.Accent)
    }
}

private fun heightLabel(height: Int): String = if (height >= 2160) "4K" else "${height}p"
private val CommonHeights = listOf(2160, 1440, 1080, 720, 480, 360)
