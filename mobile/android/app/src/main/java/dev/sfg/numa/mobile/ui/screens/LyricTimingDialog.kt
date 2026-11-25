package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun LyricTimingDialog(offsetMs: Int, onClose: () -> Unit, onSave: (Int) -> Unit) {
    var draft by rememberSaveable(offsetMs) { mutableIntStateOf(offsetMs) }
    val signedOffset = if (draft > 0) "+$draft" else draft.toString()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(uiText("Adjust lyric timing", "Ajustar sincronización")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(uiText("Applies to this song.", "Se aplica a esta canción."))
                Text("$signedOffset ms")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { draft -= 100 }, enabled = draft > -5_000) {
                        Text(uiText("Delay", "Atrasar"))
                    }
                    TextButton(onClick = { draft += 100 }, enabled = draft < 5_000) {
                        Text(uiText("Advance", "Adelantar"))
                    }
                }
                TextButton(onClick = { draft = 0 }, enabled = draft != 0) {
                    Text(uiText("Reset", "Restablecer"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(uiText("Save", "Guardar")) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(uiText("Cancel", "Cancelar")) }
        },
    )
}
