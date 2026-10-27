package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/** Buttons use the same translucent finish as menus, with a non-recursive backdrop. */
fun Modifier.desktopButtonSurface(shape: Shape = CircleShape): Modifier = composed {
    clip(shape).glassPane(shape, LocalGlassButtonTone.current)
}
