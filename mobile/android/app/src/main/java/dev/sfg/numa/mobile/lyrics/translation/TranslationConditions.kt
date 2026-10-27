package dev.sfg.numa.mobile.lyrics.translation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dev.sfg.numa.mobile.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

fun observeTranslationConditions(context: Context, network: NetworkMonitor, scope: CoroutineScope): StateFlow<TranslationConditions> {
    val app = context.applicationContext
    val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val battery = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(power?.isPowerSaveMode == true) }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(power?.isPowerSaveMode == true)
        awaitClose { app.unregisterReceiver(receiver) }
    }
    return combine(network.isOnline, network.isMetered, battery) { online, metered, saving -> TranslationConditions(online, metered, saving) }
        .stateIn(scope, SharingStarted.Eagerly, TranslationConditions(network.isOnline.value,
            network.isMetered.value, power?.isPowerSaveMode == true))
}
