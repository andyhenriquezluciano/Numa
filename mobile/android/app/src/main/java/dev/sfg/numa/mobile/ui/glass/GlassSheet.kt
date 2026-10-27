package dev.sfg.numa.mobile.ui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.GlassBorders
import dev.sfg.numa.mobile.model.GlassTheme
import dev.sfg.numa.mobile.ui.theme.RosePineColors

/** The finish belongs to the moving sheet content, not its pre-offset layout bounds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val snapshot = rememberModalGlassSnapshot(true)
    val style = LocalGlass.current
    val showBorders = style.borders != GlassBorders.LIMPIO
    val isRosePine = style.theme == GlassTheme.ROSE_PINE
    val isDark = style.theme == GlassTheme.CRISTAL_OSCURO
    val shape = RoundedCornerShape(topStart = DesktopGlass.MenuRadius, topEnd = DesktopGlass.MenuRadius)

    CompositionLocalProvider(LocalGlassSnapshot provides snapshot) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shape = shape,
            scrimColor = DesktopGlass.ModalScrim,
            dragHandle = null,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .then(
                        if (isRosePine) {
                            Modifier
                                .background(RosePineColors.Surface, shape)
                                .then(if (showBorders) Modifier.border(1.dp, RosePineColors.Rose, shape) else Modifier)
                        } else {
                            Modifier
                                .glassPane(shape, GlassTone.OVERLAY)
                                .then(
                                    if (showBorders) Modifier.border(
                                        1.dp,
                                        if (isDark) Color(0x28FFFFFF) else Color(0x30FFFFFF),
                                        shape,
                                    ) else Modifier
                                )
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                dragHandle?.invoke()
                content()
            }
        }
    }
}
