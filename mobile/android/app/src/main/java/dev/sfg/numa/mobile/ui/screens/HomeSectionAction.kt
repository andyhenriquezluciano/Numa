package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.motion.pressScale
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.currentRosePine

@Composable
internal fun HomeSectionAction(label: String, onClick: () -> Unit) {
    val rose = currentRosePine()
    val shape = if (rose != null) RoundedCornerShape(10.dp) else MaterialTheme.shapes.medium
    val contrastFill = if (rose == null) Modifier.background(CanopyColors.SurfaceHover.copy(alpha = 0.64f)) else Modifier
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.heightIn(min = 48.dp).pressScale(interaction, 0.97f)
            .clip(shape).glassPane(shape, GlassTone.CONTROL)
            .then(contrastFill)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(),
                role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
