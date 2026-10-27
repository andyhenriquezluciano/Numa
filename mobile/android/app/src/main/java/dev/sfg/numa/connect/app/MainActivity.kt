/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.sfg.numa.connect.app

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sfg.numa.mobile.app.NumaApp
import dev.sfg.numa.mobile.app.NumaViewModel
import dev.sfg.numa.mobile.auth.NumaAccountService
import dev.sfg.numa.mobile.ui.theme.NumaTheme

/** Thin Android entry point; app behavior lives in repositories and state holders. */
class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(dev.sfg.numa.mobile.settings.NumaLanguage.wrap(newBase))
    }

    private val viewModel by viewModels<NumaViewModel>()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Media3 remains usable in-app if the user declines notifications. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.sfg.numa.mobile.discord.DiscordNative.attach(this)
        enableEdgeToEdge()
        runCatching {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
        }
        handleIntent(intent)
        requestNotificationPermission()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            NumaTheme(settings) {
                NumaApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dev.sfg.numa.mobile.NumaGraph.from(this).downloads.resumeDownloads()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            // Assistant sends the spoken words as the query, and narrows them into the focus
            // extras when it recognised a specific artist or album. An empty query is the
            // documented "just play something" request.
            val query = intent.getStringExtra(SearchManager.QUERY).orEmpty().ifBlank {
                listOfNotNull(
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE),
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST),
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM),
                ).joinToString(" ")
            }
            viewModel.playFromSearch(query)
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (sharedText.isNotBlank()) {
                viewModel.handleIncomingLink(sharedText)
            }
            return
        }

        val uri = intent.data
        val dataString = uri?.toString().orEmpty()
        if (dataString.isBlank()) return
        if (uri?.scheme == packageName && uri.host == "account" && uri.path == "/callback") {
            NumaAccountService.get(this).handleAuthCallback(uri)
            return
        }
        viewModel.handleIncomingLink(dataString)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
