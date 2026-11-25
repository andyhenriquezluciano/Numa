package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun GlassDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    val highlight = if (selected) Modifier.background(DesktopGlass.PickerSelectedFill, shape)
        else Modifier.desktopMenuHighlight(pressed || hovered || focused, shape)
    DropdownMenuItem(text, onClick,
        modifier = modifier.padding(horizontal = 5.dp).clip(shape).then(highlight),
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, enabled = enabled,
        interactionSource = source,
        colors = MenuDefaults.itemColors(textColor = DesktopGlass.Text,
            leadingIconColor = DesktopGlass.Text, trailingIconColor = DesktopGlass.Text))
}
