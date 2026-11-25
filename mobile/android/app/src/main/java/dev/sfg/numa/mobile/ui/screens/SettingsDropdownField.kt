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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.ui.glass.DesktopGlass
import dev.sfg.numa.mobile.ui.glass.GlassDropdownMenu
import dev.sfg.numa.mobile.ui.glass.GlassDropdownMenuItem
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.LocalGlass
import dev.sfg.numa.mobile.ui.theme.currentRosePine

@Composable
internal fun <T> SettingsDropdownField(
    options: List<Pair<T, String>>,
    selected: T,
    iconFor: ((T) -> ImageVector)? = null,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (T) -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val currentLabel = options.firstOrNull { it.first == selected }?.second ?: ""
    val rose = currentRosePine()
    val shape = if (rose == null) MaterialTheme.shapes.small else MaterialTheme.shapes.medium
    val accent = SettingsStyle.TileIcon
    val fieldFill by animateColorAsState(
        targetValue = rose?.Surface ?: if (expanded) DesktopGlass.PickerSelectedFill else DesktopGlass.PickerFieldFill,
        animationSpec = tween(120),
        label = "SettingsPickerFill",
    )
    val showBorder = rose == null && LocalGlass.current.borders == GlassBorders.PREDETERMINADO

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .onGloballyPositioned { coordinates ->
                anchorWidthPx = coordinates.size.width
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(fieldFill)
                .then(if (showBorder) Modifier.border(1.dp, DesktopGlass.ButtonBorder, shape) else Modifier)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = settingsText(currentLabel),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = SettingsStyle.Title,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp).padding(end = 12.dp),
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = SettingsStyle.Chevron,
                modifier = Modifier.size(24.dp),
            )
        }

        val menuModifier = if (anchorWidthPx > 0) {
            Modifier.width(with(density) { anchorWidthPx.toDp() })
        } else {
            Modifier.widthIn(min = 200.dp, max = 320.dp)
        }

        GlassDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier,
            offset = DpOffset(0.dp, 6.dp),
            tone = GlassTone.SETTINGS_PICKER,
            shape = if (rose == null) RoundedCornerShape(DesktopGlass.MenuRadius) else shape,
        ) {
            options.forEach { (value, optLabel) ->
                val isSelected = value == selected
                GlassDropdownMenuItem(
                    text = {
                        Text(
                            text = settingsText(optLabel),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected && rose != null) accent else SettingsStyle.Title,
                        )
                    },
                    enabled = isEnabled(value),
                    modifier = Modifier.semantics { this.selected = isSelected },
                    selected = isSelected && rose == null,
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                    leadingIcon = iconFor?.let { resolve -> { Icon(resolve(value), null, Modifier.size(20.dp), tint = accent) } },
                    trailingIcon = if (isSelected) {
                        {
                            if (rose == null) Box(Modifier.size(8.dp).background(accent, CircleShape))
                            else Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    } else null,
                )
            }
        }
    }
}
