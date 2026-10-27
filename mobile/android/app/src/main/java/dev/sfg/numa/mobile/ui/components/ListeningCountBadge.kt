package dev.sfg.numa.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.ui.text.uiText
import dev.sfg.numa.mobile.ui.theme.LocalAccent

@Composable
internal fun ListeningCountBadge(count: Int) {
    val accent = LocalAccent.current
    Column(Modifier.widthIn(min = 54.dp).background(accent.copy(alpha = 0.13f), RoundedCornerShape(13.dp))
        .padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (count == 1) uiText("listen", "escucha") else uiText("listens", "escuchas"),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}
