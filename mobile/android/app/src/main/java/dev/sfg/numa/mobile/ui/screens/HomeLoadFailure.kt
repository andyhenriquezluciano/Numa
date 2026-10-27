package dev.sfg.numa.mobile.ui.screens

import androidx.compose.runtime.Composable
import dev.sfg.numa.mobile.auth.AuthState
import dev.sfg.numa.mobile.ui.components.MessagePanel
import dev.sfg.numa.mobile.ui.text.uiText

internal fun requiresHomeSignIn(auth: AuthState, message: String): Boolean =
    auth !is AuthState.SignedIn || listOf("authenticated youtube cookies", "authentication required",
        "sign in", "unauthorized", "401").any { message.contains(it, ignoreCase = true) }

@Composable
internal fun HomeLoadFailure(auth: AuthState, message: String, onSignIn: () -> Unit, onRetry: () -> Unit) {
    val signIn = requiresHomeSignIn(auth, message)
    MessagePanel(
        if (signIn) uiText("Sign in to YouTube Music", "Inicia sesión en YouTube Music")
        else uiText("Could not load Home", "No se pudo cargar Inicio"),
        if (signIn) uiText("Connect your Google account to load your music and recommendations.",
            "Conecta tu cuenta de Google para cargar tu música y recomendaciones.")
        else uiText("We couldn't load your music. Please try again.", "No se pudo cargar tu música. Inténtalo de nuevo."),
        if (signIn) uiText("Sign in with Google", "Iniciar sesión con Google") else uiText("Try again", "Reintentar"),
        if (signIn) onSignIn else onRetry,
    )
}
