package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    preview: @Composable (Modifier) -> Unit,
    controls: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    GlassDialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val landscape = maxWidth > maxHeight * 1.3f
            val panel = Modifier.widthIn(max = if (landscape) 860.dp else 560.dp)
                .fillMaxWidth().heightIn(max = 676.dp).fillMaxHeight()
                .glassPane(RoundedCornerShape(28.dp), GlassTone.OVERLAY).padding(16.dp)
            val heading: @Composable () -> Unit = {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
            }
            val actions: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    dismissButton()
                    confirmButton()
                }
            }
            if (landscape) {
                Row(panel, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        heading()
                        controls()
                        actions()
                    }
                }
            } else {
                Column(panel, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    heading()
                    preview(Modifier.weight(1f).fillMaxWidth())
                    controls()
                    actions()
                }
            }
        }
    }
}
