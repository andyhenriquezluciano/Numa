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

package dev.sfg.numa.mobile.app

import android.app.Application
import android.os.Build
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.audio.selfDeviceLabel
import dev.sfg.numa.mobile.model.DeviceAvailability
import dev.sfg.numa.mobile.model.DeviceType
import dev.sfg.numa.mobile.model.PlaybackDevice
import dev.sfg.numa.mobile.model.PlaybackTarget
import dev.sfg.numa.mobile.model.PlaybackTargetState
import dev.sfg.numa.mobile.playback.ListeningPartyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Local output identity and the name shared with listening-party guests. */
internal class PlaybackTargets(
    private val application: Application,
    private val graph: NumaGraph,
    private val scope: CoroutineScope,
    private val party: ListeningPartyManager,
) {
    private val localName = graph.settings.settings.map { it.customDeviceName }.distinctUntilChanged()

    val state: StateFlow<PlaybackTargetState> = localName.map { custom ->
        PlaybackTargetState(devices = listOf(localDevice(custom, true)))
    }.stateIn(scope, SharingStarted.Eagerly, PlaybackTargetState(devices = listOf(localDevice("", true))))

    private fun baseDeviceName() = Build.MODEL.takeIf(String::isNotBlank) ?: application.selfDeviceLabel()

    private fun localDevice(custom: String, active: Boolean) = PlaybackDevice(
        id = LOCAL_ID,
        name = baseDeviceName(),
        customName = custom,
        type = DeviceType.PHONE,
        availability = DeviceAvailability.ONLINE,
        isLocal = true,
        isActive = active,
    )

    fun select(target: PlaybackTarget) = Unit

    fun renameLocal(newName: String) {
        graph.settings.updateSettings(graph.settings.settings.value.copy(customDeviceName = newName.trim()))
    }

    fun observe() {
        scope.launch {
            localName.collect { custom -> party.updateDisplayName(custom.ifBlank { baseDeviceName() }) }
        }
    }

    companion object {
        const val LOCAL_ID = "local-phone"
    }
}
