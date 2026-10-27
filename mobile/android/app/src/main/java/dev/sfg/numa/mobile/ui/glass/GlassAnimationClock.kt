package dev.sfg.numa.mobile.ui.glass

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Invalidate both backdrop recordings and their consumers without recomposing screen content. */
@Composable
fun rememberGlassAnimationClock(active: Boolean): State<Long> {
    val frame = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wasActive = remember { mutableStateOf(false) }
    LaunchedEffect(active, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val settleUntil = if (!active && wasActive.value) SystemClock.uptimeMillis() + 3_000L else 0L
            wasActive.value = active
            while (isActive && (active || SystemClock.uptimeMillis() < settleUntil)) {
                withFrameNanos { frame.longValue = it }
            }
        }
    }
    return frame
}
