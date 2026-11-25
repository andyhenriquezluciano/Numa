package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.MotionMode
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun MotionSettings(settings: NumaSettings, onSettings: (NumaSettings) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        RowTitle(uiText("Media motion", "Movimiento de fondos y carátulas"))
        RowSubtitle(uiText(
            "Automatic follows battery saver and Android animation preferences.",
            "Automático respeta el ahorro de batería y las preferencias de animación de Android.",
        ))
        SettingsDropdownField(
            options = listOf(
                MotionMode.AUTOMATIC to uiText("Automatic", "Automático"),
                MotionMode.FULL to uiText("Full", "Completo"),
                MotionMode.REDUCED to uiText("Reduced", "Reducido"),
            ),
            selected = settings.motionMode,
            onSelect = { onSettings(settings.copy(motionMode = it, animatedBackground = true)) },
        )
    }
}

@Composable
internal fun AdvancedSettings(showInitially: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(showInitially) { mutableStateOf(showInitially) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) uiText("Hide advanced settings", "Ocultar ajustes avanzados")
            else uiText("Advanced settings", "Ajustes avanzados"))
    }
    if (expanded) content()
}

@Composable
internal fun ArtworkSizePreview(expanded: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.size(56.dp, 76.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                (if (expanded) Modifier.fillMaxSize() else Modifier.size(36.dp))
                    .clip(if (expanded) shape else RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            RowTitle(uiText("Animated artwork size", "Tamaño de la carátula animada"))
            SettingsDropdownField(
                options = listOf(true to uiText("Full", "Completa"), false to uiText("Normal size", "Tamaño normal")),
                selected = expanded,
                onSelect = onSelect,
            )
        }
    }
}
