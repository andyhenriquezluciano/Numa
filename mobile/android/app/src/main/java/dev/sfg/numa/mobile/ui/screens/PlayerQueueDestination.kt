package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sfg.numa.connect.R
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun PlayerQueueDestination(count: Int, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp)) {
        IconButton(onClick, Modifier.matchParentSize()) {
            Icon(painterResource(R.drawable.ic_desktop_queue),
                if (active) uiText("Hide queue", "Ocultar cola") else uiText("Queue", "Cola"),
                Modifier.size(24.dp), tint = Color.White.copy(alpha = if (active) 1f else 0.75f))
        }
        if (count > 0) {
            Box(Modifier.align(Alignment.TopEnd).defaultMinSize(minWidth = 22.dp, minHeight = 20.dp)
                .background(Color(0xFF3A3A3C), CircleShape).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center) {
                Text(if (count > 99) "99+" else count.toString(), color = Color.White,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}
