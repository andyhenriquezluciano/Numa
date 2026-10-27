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

package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import dev.sfg.numa.mobile.ui.screens.SettingsSegmented
import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import dev.sfg.numa.mobile.ui.glass.GlassBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.sfg.numa.connect.R
import dev.sfg.numa.mobile.ui.theme.CanopyColors

/**
 * Names a new playlist, on YouTube Music or on this phone. [youtubeAvailable] is false when no
 * account is signed in, which leaves only the local choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPlaylistSheet(
    youtubeAvailable: Boolean,
    onDismiss: () -> Unit,
    /** [local] picks where it lives; [thenAddSongs] opens the file picker once a local playlist exists. */
    onCreate: (title: String, local: Boolean, thenAddSongs: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var local by remember { mutableStateOf(!youtubeAvailable) }
    val canCreate = title.isNotBlank()
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = CanopyColors.Muted.copy(alpha = 0.4f)) },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.qol_new_playlist), style = MaterialTheme.typography.titleLarge, color = CanopyColors.Text)
            Spacer(Modifier.height(14.dp))
            SettingsSegmented(
                options = listOf(false to "YouTube Music", true to "En este teléfono"),
                selected = local, onSelect = { local = it },
                isEnabled = { it || youtubeAvailable },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (local) R.string.qol_local_hint else R.string.qol_cloud_hint),
                style = MaterialTheme.typography.bodySmall,
                color = CanopyColors.Muted,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(150) },
                label = { Text(stringResource(R.string.qol_playlist_name)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CanopyColors.Text, unfocusedTextColor = CanopyColors.Text,
                    focusedBorderColor = CanopyColors.Accent,
                    unfocusedBorderColor = CanopyColors.Text.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            GlassConfirmButton(stringResource(R.string.qol_create), enabled = canCreate,
                modifier = Modifier.fillMaxWidth().height(44.dp)) { onCreate(title, local, false) }
        }
    }
}
