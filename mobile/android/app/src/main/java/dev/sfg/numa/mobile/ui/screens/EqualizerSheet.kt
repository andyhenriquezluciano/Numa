package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.glass.GlassDropdownMenuItem

import dev.sfg.numa.mobile.ui.components.ThinGlassSlider

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import dev.sfg.numa.mobile.ui.scroll.numaVerticalScroll as verticalScroll
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.EQ_BANDS
import dev.sfg.numa.mobile.model.EQ_PRESETS
import dev.sfg.numa.mobile.model.EqualizerConfig
import dev.sfg.numa.mobile.model.LocalMaxActive
import dev.sfg.numa.mobile.model.NumaSettings
import dev.sfg.numa.mobile.ui.theme.CanopyColors
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.GlassBottomSheet
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerRow(settings: NumaSettings, onSettings: (NumaSettings) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val maxActive = LocalMaxActive.current

    SettingsRow(
        title = uiText("Audio Equalizer", "Ecualizador de audio"),
        enabled = !maxActive,
        subtitle = if (maxActive) {
            uiText("Unavailable while audio quality is Max", "No disponible con calidad de audio Max")
        } else if (settings.equalizerConfig.enabled) {
            uiText("Enabled", "Activado") + " (${equalizerPresetLabel(settings.equalizerConfig.presetId)})"
        } else {
            uiText("Disabled", "Desactivado")
        },
        icon = Icons.Rounded.Tune,
        modifier = Modifier.clickable(enabled = !maxActive) { showSheet = true },
    ) {
        SettingsSwitch(
            checked = settings.equalizerConfig.enabled && !maxActive,
            enabled = !maxActive,
            onCheckedChange = {
                onSettings(settings.copy(equalizerConfig = settings.equalizerConfig.copy(enabled = it)))
            },
        )
    }

    if (showSheet) {
        GlassBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EqualizerContent(
                config = settings.equalizerConfig,
                onConfigChange = { newConfig ->
                    onSettings(settings.copy(equalizerConfig = newConfig))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerContent(config: EqualizerConfig, onConfigChange: (EqualizerConfig) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            uiText("Equalizer", "Ecualizador"),
            style = MaterialTheme.typography.headlineMedium,
            color = CanopyColors.Text,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Presets Dropdown
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            val currentLabel = equalizerPresetLabel(config.presetId)
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedTextColor = CanopyColors.Text,
                    unfocusedTextColor = CanopyColors.Text,
                ),
                modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            dev.sfg.numa.mobile.ui.glass.GlassDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                EQ_PRESETS.forEach { preset ->
                    GlassDropdownMenuItem(
                        text = { Text(equalizerPresetLabel(preset.id)) },
                        onClick = {
                            onConfigChange(
                                config.copy(
                                    presetId = preset.id,
                                    gains = preset.gains
                                )
                            )
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Preamp Slider
        Text("Preamp: ${config.preampDb.roundToInt()} dB", color = CanopyColors.Text)
        ThinGlassSlider(
            value = config.preampDb,
            onValueChange = { 
                onConfigChange(config.copy(preampDb = it, presetId = "custom"))
            },
            valueRange = EqualizerConfig.MIN_PREAMP_DB..EqualizerConfig.MAX_PREAMP_DB,
        )
        Spacer(Modifier.height(16.dp))
        
        // Bass Boost Slider
        Text(uiText("Bass Boost: ${(config.bassBoost * 100).roundToInt()}%", "Refuerzo de graves: ${(config.bassBoost * 100).roundToInt()}%"), color = CanopyColors.Text)
        ThinGlassSlider(
            value = config.bassBoost,
            onValueChange = { 
                onConfigChange(config.copy(bassBoost = it, presetId = "custom"))
            },
            valueRange = 0f..1f,
        )
        Spacer(Modifier.height(24.dp))

        // 10 Band EQ Sliders
        EQ_BANDS.forEachIndexed { index, band ->
            val gain = config.clampedGains.getOrNull(index) ?: 0f
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = band.label,
                    color = CanopyColors.Text,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
                Spacer(Modifier.width(16.dp))
                ThinGlassSlider(
                    value = gain,
                    onValueChange = { newGain ->
                        val newGains = config.gains.toMutableList()
                        if (newGains.size < 10) {
                            newGains.addAll(List(10 - newGains.size) { 0f })
                        }
                        newGains[index] = newGain
                        onConfigChange(config.copy(gains = newGains, presetId = "custom"))
                    },
                    valueRange = EqualizerConfig.MIN_GAIN_DB..EqualizerConfig.MAX_GAIN_DB,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "${if (gain > 0) "+" else ""}${gain.roundToInt()} dB",
                    color = CanopyColors.Muted,
                    modifier = Modifier.width(52.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
