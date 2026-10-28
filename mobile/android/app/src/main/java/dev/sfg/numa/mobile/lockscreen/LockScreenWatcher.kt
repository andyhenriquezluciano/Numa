package dev.sfg.numa.mobile.lockscreen

import kotlinx.coroutines.flow.collect

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.sfg.numa.mobile.NumaGraph
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Playback service owns screen monitoring while music is playing. */
object LockScreenWatcher {
    fun attach(context: Context, isPlaying: () -> Boolean): () -> Unit {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var pending: Job? = null
        scope.launch {
            NumaGraph.from(context).settings.settings.map { it.lockScreenPlayer }.distinctUntilChanged().collect {
                if (!it) { pending?.cancel(); LockScreenActivity.dismiss(context) }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) {
                    pending?.cancel(); LockScreenActivity.dismiss(context); return
                }
                val screenOff = intent.action == Intent.ACTION_SCREEN_OFF
                if (!screenOff && intent.action != Intent.ACTION_SCREEN_ON) return
                pending?.cancel()
                pending = scope.launch {
                    if (!screenOff) delay(150)
                    val enabled = NumaGraph.from(context).settings.lockScreenEnabled()
                    val playing = isPlaying()
                    val locked = screenOff || context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    android.util.Log.i("NumaLockScreen", "screenOff=$screenOff enabled=$enabled playing=$playing locked=$locked")
                    if (!LockScreenPolicy.autoOpen(enabled, playing, locked, LockScreenPermission.granted(context))) return@launch
                    runCatching {
                        context.startActivity(Intent(context, LockScreenActivity::class.java)
                            .putExtra("fromScreenOff", screenOff)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                    }.onFailure { android.util.Log.w("NumaLockScreen", "Unable to show lock-screen player", it) }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return {
            runCatching { context.unregisterReceiver(receiver) }
            scope.cancel()
        }
    }
}
