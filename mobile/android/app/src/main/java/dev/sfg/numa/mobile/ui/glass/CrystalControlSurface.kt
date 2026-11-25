package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.ui.theme.currentRosePine

@Composable
internal fun Modifier.crystalControlSurface(
    shape: Shape = MaterialTheme.shapes.small,
    fill: Color = DesktopGlass.ButtonFill,
    roseSurface: @Composable Modifier.() -> Modifier,
): Modifier {
    if (currentRosePine() != null) return roseSurface(this)

    val outline = if (LocalGlass.current.borders == GlassBorders.PREDETERMINADO)
        Modifier.border(1.dp, DesktopGlass.ButtonBorder, shape) else Modifier
    return clip(shape).background(fill).then(outline)
}

@Composable
internal fun Modifier.crystalSearchSurface(roseSurface: @Composable Modifier.() -> Modifier): Modifier =
    crystalControlSurface(MaterialTheme.shapes.medium, DesktopGlass.TransportButtonFill, roseSurface)
