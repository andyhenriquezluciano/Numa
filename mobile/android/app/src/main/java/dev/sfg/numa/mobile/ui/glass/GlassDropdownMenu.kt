package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.GlassTheme
import dev.sfg.numa.mobile.ui.theme.RosePineColors

/** Floating menus sample the scene in screen coordinates, including across popup windows. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(DesktopGlass.MenuRadius)
    val snapshot = rememberModalGlassSnapshot(expanded)
    val style = LocalGlass.current
    val isRosePine = style.theme == GlassTheme.ROSE_PINE

    CompositionLocalProvider(LocalGlassSnapshot provides snapshot) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            modifier = modifier
                .clip(shape)
                .then(
                    if (isRosePine) {
                        Modifier
                            .background(RosePineColors.Surface, shape)
                    } else {
                        Modifier
                            .glassPane(shape, GlassTone.OVERLAY, borderless = true)
                    }
                ),
            shape = shape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            content = content,
        )
    }
}
