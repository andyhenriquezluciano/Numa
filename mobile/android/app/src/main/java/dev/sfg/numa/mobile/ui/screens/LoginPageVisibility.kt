package dev.sfg.numa.mobile.ui.screens

import java.net.URI

internal fun hideYouTubeLandingPage(url: String?, switchingAccount: Boolean): Boolean {
    val uri = runCatching { URI(url.orEmpty()) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return false
    if (switchingAccount && (uri.fragment.orEmpty().contains("channel_switcher") ||
            uri.path.orEmpty().contains("select", ignoreCase = true))) return false
    return host == "music.youtube.com" || uri.path.isNullOrBlank() || uri.path == "/"
}
