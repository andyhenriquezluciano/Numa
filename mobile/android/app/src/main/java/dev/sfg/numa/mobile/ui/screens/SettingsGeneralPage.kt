package dev.sfg.numa.mobile.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import dev.sfg.numa.mobile.auth.AuthState
import dev.sfg.numa.mobile.model.NumaSettings

@Composable
internal fun AccountSettingsPage(
    settings: NumaSettings,
    auth: AuthState,
    onSettings: (NumaSettings) -> Unit,
    onSignIn: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    SectionLabel("Cuenta de NÜMA")
    NumaAccountSettingsCard()
    SectionLabel("YouTube Music")
    AccountCard(auth, onSignIn, onSwitchAccount, onSignOut)
    SectionLabel("Historial", 2)
    SettingsPanel(index = 3) {
        ToggleRow(
            title = "Guardar reproducciones en el historial de YouTube Music",
            subtitle = "Añade lo que escuchas al historial de YouTube Music.",
            icon = Icons.Rounded.History,
            checked = settings.sendYouTubeHistory,
            onChecked = { onSettings(settings.copy(sendYouTubeHistory = it)) },
        )
    }
    AccountSyncCard()
}
