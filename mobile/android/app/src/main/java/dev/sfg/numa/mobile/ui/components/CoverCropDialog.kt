package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.sfg.numa.mobile.ui.glass.GlassDialog
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.glassPane

@Composable
internal fun CoverCropDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    GlassDialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
            .glassPane(RoundedCornerShape(28.dp), GlassTone.OVERLAY).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dismissButton()
                confirmButton()
            }
        }
    }
}
