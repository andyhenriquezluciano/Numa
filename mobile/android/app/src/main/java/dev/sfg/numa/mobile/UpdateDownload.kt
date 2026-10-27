/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */

package dev.sfg.numa.mobile

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*

internal class UpdateDownload(
    context: Context,
    private val metadata: MobileUpdateMetadata,
    private val baseUrl: String,
    private val publish: (UpdateState) -> Unit,
    private val install: (File) -> Unit,
) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId = -1L
    private var registered = false
    private val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Numa-${metadata.version}.apk")
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
            unregister()
            val pending = goAsync()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val valid = withContext(Dispatchers.IO) {
                        val successful = manager.query(DownloadManager.Query().setFilterById(downloadId))?.use {
                            it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                        } == true
                        successful && destination.isFile && destination.length() > 0 &&
                            (metadata.sha256.isBlank() || sha256(destination).equals(metadata.sha256, true))
                    }
                    ensureActive()
                    if (valid) {
                        publish(UpdateState.ReadyToInstall(metadata.version))
                        install(destination)
                    } else {
                        withContext(Dispatchers.IO) { destination.delete() }
                        publish(UpdateState.Failed(metadata.version, "The download or checksum verification failed."))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    publish(UpdateState.Failed(metadata.version, error.message.orEmpty()))
                } finally { pending.finish(); scope.cancel() }
            }
        }
    }

    fun start() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (destination.exists()) destination.delete() }
                ContextCompat.registerReceiver(context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
                registered = true
                val url = metadata.apkUrl.ifBlank { "$baseUrl/Numa-Android-${metadata.version}.apk" }
                downloadId = manager.enqueue(DownloadManager.Request(url.toUri())
                    .setTitle("Numa Update").setDescription("Downloading version ${metadata.version}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(destination)).setAllowedOverMetered(true).setAllowedOverRoaming(true))
                publish(UpdateState.Downloading(metadata.version))
                delay(30 * 60 * 1000L)
                unregister()
                publish(UpdateState.Failed(metadata.version, "The update download timed out."))
                if (downloadId >= 0) manager.remove(downloadId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                unregister()
                publish(UpdateState.Failed(metadata.version, error.message.orEmpty()))
            }
        }
    }

    fun cancel() {
        unregister()
        scope.cancel()
        if (downloadId >= 0) manager.remove(downloadId)
    }

    private fun unregister() {
        if (registered) { registered = false; runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(bytes)
                if (read < 0) break
                digest.update(bytes, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
