package dev.sfg.numa.mobile.ui.glass

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val snapshot = rememberModalGlassSnapshot(true)
    CompositionLocalProvider(LocalGlassSnapshot provides snapshot, LocalContentColor provides DesktopGlass.Text) {
        Dialog(onDismissRequest, properties) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setDimAmount(0.4f) }
            content()
        }
    }
}
