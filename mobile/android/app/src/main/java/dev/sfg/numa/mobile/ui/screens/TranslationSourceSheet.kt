package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.model.LyricTranslationProvider
import dev.sfg.numa.mobile.ui.glass.GlassBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranslationSourceSheet(
    selected: LyricTranslationProvider,
    onDismiss: () -> Unit,
    onSelect: (LyricTranslationProvider) -> Unit,
) {
    GlassBottomSheet(onDismiss, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RowTitle("Fuente de traducción")
            RowSubtitle("Elige dónde se traducen las letras al español.")
            listOf(LyricTranslationProvider.LOCAL, LyricTranslationProvider.GOOGLE).forEach { choice ->
                ActionRow(
                    title = choice.label,
                    subtitle = if (choice == LyricTranslationProvider.LOCAL)
                        "En tu teléfono. Descarga el modelo la primera vez."
                    else "Traducción en línea. Las letras se envían a Google.",
                    value = if (choice == selected) "Seleccionado" else null,
                    onClick = { onSelect(choice) },
                )
            }
        }
    }
}
