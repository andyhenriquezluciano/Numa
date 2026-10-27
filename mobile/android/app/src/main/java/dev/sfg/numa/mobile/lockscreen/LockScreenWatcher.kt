package dev.sfg.numa.mobile.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.widget.NumaWidgetStateStore
import kotlinx.coroutines.*

/** Playback service owns screen monitoring, including when the main app is in the background. */
object LockScreenWatcher {
    fun attach(context: Context): () -> Unit {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_SCREEN_ON) return
                scope.launch {
                    delay(350)
                    val enabled = NumaGraph.from(context).settings.lockScreenEnabled()
                    val playing = NumaWidgetStateStore(context).load().isPlaying
                    val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    if (!LockScreenPolicy.autoOpen(enabled, playing, locked, Settings.canDrawOverlays(context))) return@launch
                    runCatching {
                        context.startActivity(Intent(context, LockScreenActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        return {
            runCatching { context.unregisterReceiver(receiver) }
            scope.cancel()
        }
    }
}
