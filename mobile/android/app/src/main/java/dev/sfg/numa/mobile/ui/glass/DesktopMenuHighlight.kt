package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.desktopMenuHighlight(active: Boolean, shape: Shape = RoundedCornerShape(6.dp)): Modifier {
    if (!active) return this
    return background(DesktopGlass.ItemHighlight, shape)
}
