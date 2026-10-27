package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sfg.numa.mobile.social.PartyState
import dev.sfg.numa.mobile.ui.components.NumaChromeHeight
import dev.sfg.numa.mobile.ui.scroll.numaVerticalScroll

@Composable
fun RoomsScreen(
    party: PartyState,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onTransferHost: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().numaVerticalScroll(rememberScrollState())
            .padding(PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = NumaChromeHeight + 32.dp)),
    ) {
        FrostedListeningPartyPanel(party, onCreate, onJoin, onLeave, onTransferHost)
    }
}
