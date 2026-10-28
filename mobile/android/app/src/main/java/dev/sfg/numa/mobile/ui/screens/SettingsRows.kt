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

import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.model.AudioQuality
import dev.sfg.numa.mobile.model.NonMusicSkipMode
import dev.sfg.numa.mobile.ui.motion.popOnChange
import dev.sfg.numa.mobile.ui.motion.riseIn
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.glass.glassPane

/** Small caps caption above a panel. */
@Composable
internal fun SectionLabel(value: String, index: Int = 0) {
    Text(
        settingsText(value).uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        ),
        color = SettingsStyle.Caption,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 6.dp, top = 26.dp, bottom = 8.dp).riseIn(index, distance = 14f),
    )
}

/** Translucent panel; rows inside separate themselves with [PanelDivider]. */
@Composable
internal fun SettingsPanel(index: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.Transparent,
        shape = SettingsStyle.PanelShape,
        border = BorderStroke(1.dp, SettingsStyle.PanelBorder),
        modifier = Modifier.fillMaxWidth().riseIn(index).glassPane(SettingsStyle.PanelShape),
    ) {
        Column(content = content)
    }
}

/** Hairline between rows in the same panel. */
@Composable
internal fun PanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(SettingsStyle.Divider),
    )
}

/** Icon in a neutral rounded tile; every row shares it so the left edge lines up. */
@Composable
internal fun RowIcon(icon: ImageVector, tint: Color = SettingsStyle.TileIcon, fill: Color = SettingsStyle.Tile) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(fill, RoundedCornerShape(10.dp))
            .border(1.dp, SettingsStyle.TileBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun RowTitle(text: String, enabled: Boolean = true, color: Color = SettingsStyle.Title,
    help: String? = settingsHelp(text)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(settingsText(text), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            color = if (enabled) color else SettingsStyle.Caption)
        if (!help.isNullOrBlank()) SettingsInfoButton(text, help)
    }
}

@Composable
internal fun RowSubtitle(text: String) {
    Text(
        settingsText(text),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = SettingsStyle.Description,
        modifier = Modifier.padding(top = 3.dp),
    )
}

/** Icon tile, title and description on the left, [trailing] control on the right. */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    titleColor: Color = SettingsStyle.Title,
    iconTint: Color = SettingsStyle.TileIcon,
    help: String? = settingsHelp(title),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 86.dp)
            .padding(horizontal = 20.dp, vertical = 19.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) RowIcon(icon, tint = iconTint)
        Column(Modifier.weight(1f)) {
            RowTitle(title, enabled, titleColor, help)
            if (subtitle.isNotEmpty()) RowSubtitle(subtitle)
        }
        trailing()
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        // Tapping anywhere on the row toggles, not just the switch.
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked),
    ) {
        SettingsSwitch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.popOnChange(checked, peak = 1.12f),
        )
    }
}

@Composable
internal fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    value: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // The chevron leans toward where the tap is about to take you.
    val nudge by animateDpAsState(
        if (pressed) 6.dp else 0.dp,
        spring(dampingRatio = 0.4f, stiffness = 700f),
        label = "ChevronNudge",
    )
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = Modifier.clickable(interactionSource = source, indication = ripple(), onClick = onClick),
        help = null,
    ) {
        if (value != null) {
            Text(value, color = SettingsStyle.SageSoft, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = SettingsStyle.Chevron,
            modifier = Modifier.offset { IntOffset(nudge.roundToPx(), 0) }.size(24.dp),
        )
    }
}

/** Neutral outlined pill; [destructive] swaps the label to the danger colour. */
@Composable
internal fun SettingsPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier.defaultMinSize(minHeight = 44.dp).desktopButtonSurface(),
    ) {
        Box(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                settingsText(label),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = if (destructive) CanopyColors.Danger else SettingsStyle.Title,
            )
        }
    }
}

/** Stream quality with a description of the selected tier. */
@Composable
internal fun QualityRow(
    stored: AudioQuality,
    onChange: (AudioQuality) -> Unit,
    title: String = "Calidad de transmisión",
    icon: ImageVector = Icons.Rounded.Headphones,
) {
    val value = if (stored == AudioQuality.MAX) AudioQuality.HIGH else stored
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RowIcon(icon)
            Column {
                RowTitle(title)
                RowSubtitle(
                    value.description,
                )
            }
        }
        SettingsDropdownField(
            options = listOf(AudioQuality.DATA_SAVER, AudioQuality.NORMAL, AudioQuality.HIGH).map { it to it.label },
            selected = value,
            onSelect = onChange,
        )
    }
}

/** Playback behavior for segments without music. */
@Composable
internal fun NonMusicSkipRow(value: NonMusicSkipMode, onChange: (NonMusicSkipMode) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RowIcon(Icons.Rounded.SkipNext)
            Column {
                RowTitle(uiText("Non-music parts", "Partes sin música"))
                RowSubtitle(
                    when (value) {
                        NonMusicSkipMode.OFF -> uiText("Play the entire song", "Reproduce la canción completa")
                        NonMusicSkipMode.BUTTON -> uiText("Show a button to skip non-music parts", "Muestra un botón para saltar partes habladas")
                        NonMusicSkipMode.AUTO -> uiText("Skip non-music parts automatically", "Salta las partes habladas automáticamente")
                    },
                )
            }
        }
        SettingsDropdownField(
            options = listOf(
                NonMusicSkipMode.OFF to uiText("Off", "Desactivado"),
                NonMusicSkipMode.BUTTON to uiText("Button", "Botón"),
                NonMusicSkipMode.AUTO to uiText("Auto skip", "Automático"),
            ),
            selected = value,
            onSelect = onChange,
        )
    }
}

internal val AudioQuality.label: String
    get() = when (this) { AudioQuality.DATA_SAVER -> "Ahorro"; AudioQuality.NORMAL -> "Normal"; AudioQuality.HIGH -> "Alta"; AudioQuality.MAX -> "Max" }

private val AudioQuality.description: String
    get() = when (this) { AudioQuality.DATA_SAVER -> "Menor consumo de datos"; AudioQuality.NORMAL -> "Equilibrio entre calidad y consumo"; AudioQuality.HIGH -> "Mayor calidad y consumo de datos"; AudioQuality.MAX -> "Audio sin pérdida y Hi-Res de Qobuz. Desactiva la mezcla adaptativa y el ecualizador" }
