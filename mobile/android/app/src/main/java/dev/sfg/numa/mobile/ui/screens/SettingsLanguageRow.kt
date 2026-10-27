package dev.sfg.numa.mobile.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sfg.numa.connect.R
import dev.sfg.numa.mobile.settings.NumaLanguage

@Composable
internal fun SettingsLanguageRow() {
    val context = LocalContext.current
    val selected = NumaLanguage.selected(context).substringBefore('-')
    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
    SettingsPanel {
        Column(Modifier.padding(20.dp)) {
            RowTitle(stringResource(R.string.interface_language), help = null)
            SettingsDropdownField(
                options = listOf("" to stringResource(R.string.interface_system), "es" to "Español", "en" to "English"),
                selected = selected,
                onSelect = { tag ->
                    var owner = context
                    while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
                    (owner as? Activity)?.let { NumaLanguage.apply(it, tag) }
                },
            )
        }
    }
}
