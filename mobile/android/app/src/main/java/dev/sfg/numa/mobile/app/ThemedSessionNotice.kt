package dev.sfg.numa.mobile.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import dev.sfg.numa.mobile.ui.components.WarningBanner
import dev.sfg.numa.mobile.ui.glass.GlassScene
import dev.sfg.numa.mobile.ui.glass.LocalGlassScene

/** Keep the current screen's GPU backdrop available only while the notice is visible. */
@Composable
internal fun ThemedSessionNotice(message: String, atTop: Boolean, scene: GlassScene,
    onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val visible = message.isNotBlank()
    DisposableEffect(scene, visible) {
        if (visible) scene.snapshotConsumers++
        onDispose { if (visible) scene.snapshotConsumers-- }
    }
    CompositionLocalProvider(LocalGlassScene provides scene) {
        WarningBanner(message, onDismiss, modifier, isSuccess = true, topInset = atTop)
    }
}
