package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface

/** Single-choice pill; the selected segment is a lighter glass chip. */
@Composable
internal fun <T> SettingsSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (T) -> Boolean = { true },
) {
    BoxWithConstraints(modifier.padding(top = 16.dp).fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val minWidth = ((options.maxOfOrNull { it.second.length } ?: 1) * 7 * fontScale + 24).dp
        val columns = (maxWidth / minWidth).toInt().coerceIn(1, options.size)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { (value, label) ->
                        val active = value == selected
                        val enabled = isEnabled(value)
                        Box(Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                            .desktopButtonSurface()
                            .background(if (active) SettingsStyle.Selected else Color.Transparent, CircleShape)
                            .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(value) }
                            .alpha(if (enabled) 1f else 0.4f)
                            .padding(horizontal = 9.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center) {
                            Text(label, textAlign = TextAlign.Center, maxLines = 1,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                color = if (active) SettingsStyle.Title else SettingsStyle.SegmentIdle)
                        }
                    }
                    repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
