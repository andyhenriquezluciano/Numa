package dev.sfg.numa.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.auth.NumaAccountService

@Composable
internal fun AccountSyncCard() {
    val context = LocalContext.current
    val graph = NumaGraph.from(context)
    val account = NumaAccountService.get(context)
    val session by account.state.collectAsStateWithLifecycle()
    val sync by graph.accountSync.state.collectAsStateWithLifecycle()
    SectionLabel("Sincronización")
    SettingsPanel {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            RowTitle(if (session.signedIn) "Sincronización automática" else "Sincroniza tu biblioteca")
            RowSubtitle("Tus listas, carátulas y métricas en PC y Android.")
            if (sync.failed || sync.busy) RowSubtitle(sync.message)
            else if (session.signedIn && sync.lastSynced > 0) RowSubtitle("Actualizada: " +
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(java.util.Date(sync.lastSynced)))
            Spacer(Modifier.height(20.dp))
            if (sync.busy) CircularProgressIndicator()
            if (session.signedIn) SettingsPill(if (sync.busy) "Sincronizando…" else "Sincronizar ahora", onClick = graph.accountSync::syncNow, modifier = Modifier.fillMaxWidth())
            else SettingsPill("Entrar con Google", onClick = account::signIn, modifier = Modifier.fillMaxWidth())
        }
    }
}
