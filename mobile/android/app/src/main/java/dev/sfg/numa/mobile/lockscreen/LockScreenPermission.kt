/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.lockscreen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object LockScreenPermission {
    fun granted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun open(context: Context): Boolean = openLockScreenPermissionSettings(context.packageName) { action, packageName ->
        try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName != null) intent.data = Uri.parse("package:$packageName")
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) { false }
        catch (_: SecurityException) { false }
    }
}

internal fun openLockScreenPermissionSettings(packageName: String, launch: (String, String?) -> Boolean): Boolean {
    val targets = listOf(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION to packageName,
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION to null,
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS to packageName,
        Settings.ACTION_SETTINGS to null,
    )
    return targets.any { (action, target) -> launch(action, target) }
}
