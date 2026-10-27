package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun Modifier.desktopMenuHighlight(active: Boolean): Modifier {
    if (!active) return this
    val shape = RoundedCornerShape(6.dp)
    return background(DesktopGlass.ItemHighlight, shape)
}
