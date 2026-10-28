package dev.sfg.numa.mobile.lockscreen

import kotlinx.coroutines.flow.collect

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sfg.numa.connect.app.MainActivity
import dev.sfg.numa.mobile.NumaGraph
import dev.sfg.numa.mobile.playback.LocalPlaybackController
import dev.sfg.numa.mobile.ui.theme.NumaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Playback-only surface above keyguard; never dismisses authentication without a user action. */
class LockScreenActivity : FragmentActivity() {
    private var controller: LocalPlaybackController? = null
    private var interactive by mutableStateOf(false)
    private val displayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_DISMISS || intent.action == Intent.ACTION_USER_PRESENT) {
                finishAndRemoveTask(); return
            }
            interactive = intent.action == Intent.ACTION_SCREEN_ON
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(dev.sfg.numa.mobile.settings.NumaLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("NumaLockScreen", "Lock-screen player created")
        setShowWhenLocked(true)
        enableEdgeToEdge()
        interactive = getSystemService(PowerManager::class.java).isInteractive
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(ACTION_DISMISS)
        }
        ContextCompat.registerReceiver(this, displayReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            val graph = NumaGraph.from(this@LockScreenActivity)
            val enabled = graph.settings.lockScreenEnabled()
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (!LockScreenPermission.granted(this@LockScreenActivity) || !LockScreenPolicy.shouldShow(enabled, locked,
                    intent.getBooleanExtra("fromScreenOff", false), intent.getBooleanExtra("preview", false))) {
                finishAndRemoveTask()
                return@launch
            }
            lifecycleScope.launch {
                graph.settings.settings.collect {
                    if (!it.lockScreenPlayer) finishAndRemoveTask()
                }
            }
            val playback = LocalPlaybackController(this@LockScreenActivity, lifecycleScope)
            controller = playback
            setContent {
                val state by playback.snapshot.collectAsStateWithLifecycle()
                val preferences by graph.settings.settings.collectAsStateWithLifecycle()
                NumaTheme {
                    LockScreenContent(state, graph, interactive, preferences.animatedArtwork, preferences.expandedAnimatedArtwork, preferences.animatedBackground,
                        playback::toggle, playback::previous, playback::next, playback::seek,
                        onClose = { finish() }, onOpenApp = ::unlockAndOpen)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!LockScreenPermission.granted(this)) finishAndRemoveTask()
    }

    companion object {
        private const val ACTION_DISMISS = "dev.sfg.numa.mobile.DISMISS_LOCK_SCREEN"
        fun dismiss(context: Context) {
            context.sendBroadcast(Intent(ACTION_DISMISS).setPackage(context.packageName))
        }
    }

    private fun unlockAndOpen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) openApp()
        else keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = openApp()
        })
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(displayReceiver) }
        controller?.close()
        super.onDestroy()
    }
}
