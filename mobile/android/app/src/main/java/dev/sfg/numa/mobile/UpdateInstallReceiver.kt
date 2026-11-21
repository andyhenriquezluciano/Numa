/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */

package dev.sfg.numa.mobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

internal fun installUpdateSession(context: Context, apk: File, version: String) {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
        setSize(apk.length())
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }
    val sessionId = installer.createSession(params)
    val callback = PendingIntent.getBroadcast(
        context, sessionId, UpdateInstallReceiver.callback(context).putExtra(UpdateInstallReceiver.VERSION, version),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    try {
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            session.commit(callback.intentSender)
        }
        apk.delete()
    } catch (error: Exception) {
        callback.cancel()
        runCatching { installer.abandonSession(sessionId) }
        throw error
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            PendingIntent.getBroadcast(context, sessionId, callback(context),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE)?.cancel()
        }
        NumaGraph.from(context).updates.onInstallStatus(intent)
    }

    internal companion object {
        const val VERSION = "numa.update.version"
        private const val ACTION = "dev.sfg.numa.mobile.UPDATE_INSTALL_RESULT"

        fun callback(context: Context) = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION)
    }
}
