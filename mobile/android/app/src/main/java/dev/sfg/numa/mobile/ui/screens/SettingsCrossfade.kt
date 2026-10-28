/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.mobile.ui.screens

import dev.sfg.numa.mobile.ui.text.uiText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.*
import dev.sfg.numa.mobile.ui.components.ThinGlassSlider
import kotlin.math.roundToInt

@Composable
internal fun CrossfadeRow(settings: NumaSettings, onSettings: (NumaSettings) -> Unit) {
    val maxActive = LocalMaxActive.current
    val selected = settings.transitionPreference
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        RowTitle(uiText("Transition type", "Tipo de transición"))
        RowSubtitle(when (selected) {
            TransitionPreference.OFF -> uiText("Play songs one after another", "Reproduce una canción después de otra")
            TransitionPreference.CROSSFADE -> uiText("Blend the end of a song with the next", "Une el final de una canción con la siguiente")
            TransitionPreference.ADAPTIVE -> if (maxActive) uiText("Max audio uses regular crossfade", "Con audio Max se utiliza el fundido normal")
                else uiText("Adapt transitions to the rhythm of the music", "Adapta la transición al ritmo de la música")
        })
        SettingsDropdownField(options = TransitionPreference.entries.map { it to it.label }, selected = selected,
            isEnabled = { it != TransitionPreference.ADAPTIVE || !maxActive },
            onSelect = { onSettings(settings.withTransition(it)) })
        AnimatedVisibility(selected != TransitionPreference.OFF) {
            Column(Modifier.padding(top = 16.dp)) {
                RowTitle(uiText("Transition duration", "Duración de la transición"))
                RowSubtitle(uiText("${settings.crossfadeSeconds} seconds", "${settings.crossfadeSeconds} segundos"))
                ThinGlassSlider(value = settings.crossfadeSeconds.toFloat(),
                    onValueChange = { onSettings(settings.copy(crossfadeSeconds = it.roundToInt())) },
                    valueRange = NumaSettings.MIN_CROSSFADE_SECONDS.toFloat()..NumaSettings.MAX_CROSSFADE_SECONDS.toFloat(),
                    steps = NumaSettings.MAX_CROSSFADE_SECONDS - NumaSettings.MIN_CROSSFADE_SECONDS - 1,
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth()) {
                    Text("${NumaSettings.MIN_CROSSFADE_SECONDS} s", color = SettingsStyle.Caption, modifier = Modifier.weight(1f))
                    Text("${NumaSettings.MAX_CROSSFADE_SECONDS} s", color = SettingsStyle.Caption)
                }
            }
        }
    }
}

@Composable
internal fun PlaybackOrderRow(settings: NumaSettings, onSettings: (NumaSettings) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        RowTitle(uiText("Playback order", "Orden de reproducción"))
        RowSubtitle(uiText("Order for newly started playlists", "Orden para las nuevas listas"))
        SettingsDropdownField(options = PlaybackOrder.entries.map { it to it.label },
            selected = settings.playbackOrder, onSelect = { onSettings(settings.copy(playbackOrder = it)) })
        if (settings.playbackOrder == PlaybackOrder.BEST_MIX) {
            Row(Modifier.padding(top = 8.dp)) {
                RowTitle(uiText("Best Mix", "Mejor mezcla"))
            }
        }
    }
}
