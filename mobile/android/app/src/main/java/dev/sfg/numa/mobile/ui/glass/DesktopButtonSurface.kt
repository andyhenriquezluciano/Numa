package dev.sfg.numa.mobile.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/** Buttons use the same translucent finish as menus, with a non-recursive backdrop. */
fun Modifier.desktopButtonSurface(shape: Shape? = null): Modifier = composed {
    val outline = shape ?: MaterialTheme.shapes.small
    clip(outline).glassPane(outline, LocalGlassButtonTone.current)
}
