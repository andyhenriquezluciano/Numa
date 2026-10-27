package dev.sfg.numa.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.local.isLocalTrackId
import dev.sfg.numa.mobile.ui.theme.LocalAccent
import dev.sfg.numa.mobile.ui.text.uiText

@Composable
internal fun AutomaticLyricsBadge(trackId: String) {
    if (!isLocalTrackId(trackId)) return
    val graph = NumaGraph.from(LocalContext.current)
    val snapshot by graph.localLibrary.library.collectAsState()
    if (snapshot.songsById[trackId]?.automaticLyrics == true) {
        Icon(Icons.Rounded.Lyrics,
            contentDescription = uiText("Automatically added lyrics", "Letra añadida automáticamente"),
            tint = LocalAccent.current, modifier = Modifier.size(16.dp))
    }
}
