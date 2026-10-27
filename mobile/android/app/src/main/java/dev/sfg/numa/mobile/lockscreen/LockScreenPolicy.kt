package dev.sfg.numa.mobile.lockscreen

internal object LockScreenPolicy {
    fun framedArtwork(animated: Boolean, expanded: Boolean, hasMotion: Boolean): Boolean =
        !animated || !expanded || !hasMotion
    fun autoOpen(enabled: Boolean, playing: Boolean, locked: Boolean, permission: Boolean): Boolean =
        enabled && playing && locked && permission
    fun shouldShow(enabled: Boolean, locked: Boolean, fromScreenOff: Boolean, preview: Boolean): Boolean =
        enabled && (locked || fromScreenOff || preview)

    fun animate(interactive: Boolean, playing: Boolean): Boolean = interactive && playing
}
