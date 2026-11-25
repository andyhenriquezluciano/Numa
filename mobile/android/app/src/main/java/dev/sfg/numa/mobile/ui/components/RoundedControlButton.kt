package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Keeps the pressed highlight inside the same rounded outline as the control. */
@Composable
internal fun RoundedControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = LocalContentColor.current,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp),
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}
