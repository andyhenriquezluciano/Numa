package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.ui.theme.CanopyColors

/** White rounded rail without a separate thumb; retains Material input and accessibility. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThinGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    description: String = "",
    compact: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val rail = 4.dp
    Slider(
        value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.fillMaxWidth().semantics { if (description.isNotEmpty()) stateDescription = description },
        thumb = { Box(Modifier.size(0.dp)) },
        track = { state ->
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
            val fraction = ((state.value - valueRange.start) / span).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(rail).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.24f else 0.12f))) {
                Box(Modifier.fillMaxWidth(fraction).height(rail)
                    .clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 1f else 0.4f)))
            }
        },
    )
}
