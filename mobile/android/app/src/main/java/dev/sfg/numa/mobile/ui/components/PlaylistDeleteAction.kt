/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.ui.components

import dev.sfg.numa.mobile.ui.glass.GlassAlertDialog

import dev.sfg.numa.mobile.ui.glass.desktopButtonSurface

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sfg.numa.connect.R
import dev.sfg.numa.mobile.ui.glass.GlassTone
import dev.sfg.numa.mobile.ui.glass.glassPane
import dev.sfg.numa.mobile.ui.theme.CanopyColors

@Composable
internal fun GlassConfirmButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.desktopButtonSurface(),
        colors = ButtonDefaults.textButtonColors(contentColor = CanopyColors.Text, containerColor = Color.Transparent),
    ) { Text(text) }
}

@Composable
internal fun PlaylistDeleteAction(title: String, onDelete: () -> Unit) {
    var confirming by remember(title) { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        GlassConfirmButton(stringResource(R.string.qol_delete_playlist)) { confirming = true }
    }
    if (confirming) GlassAlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.qol_delete_title, title)) },
        text = { Text(stringResource(R.string.qol_delete_message)) },
        confirmButton = {
            GlassConfirmButton(stringResource(R.string.qol_delete)) {
                confirming = false
                onDelete()
            }
        },
        dismissButton = { GlassConfirmButton(stringResource(R.string.qol_cancel)) { confirming = false } },
    )
}
