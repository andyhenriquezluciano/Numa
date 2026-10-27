package dev.sfg.numa.mobile.ui.screens

import androidx.compose.runtime.Composable
import dev.sfg.numa.mobile.ui.components.ThinGlassSlider
import kotlin.math.roundToInt

@Composable
internal fun CacheSizeSlider(index: Int, sizesMb: List<Int>, onSelect: (Int) -> Unit) {
    ThinGlassSlider(
        value = index.toFloat(),
        onValueChange = { onSelect(it.roundToInt().coerceIn(sizesMb.indices)) },
        valueRange = 0f..sizesMb.lastIndex.toFloat(),
        steps = sizesMb.size - 2,
        description = formatCacheSize(sizesMb[index]),
    )
}
