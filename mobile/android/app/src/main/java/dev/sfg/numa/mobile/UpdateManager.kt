package dev.sfg.numa.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import dev.sfg.numa.connect.BuildConfig
import dev.sfg.numa.mobile.ui.text.uiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean

data class MobileUpdateMetadata(
    val version: String,
    val codename: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val sha256: String = "",
    val publishedAt: String = "",
    val releaseNotes: String = "",
    val isPreview: Boolean = false,
)

/** What the update flow is currently doing, for the in-app prompt to render. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val metadata: MobileUpdateMetadata) : UpdateState
    data class Downloading(val version: String) : UpdateState
    data class Failed(val version: String, val reason: String) : UpdateState
    data class Installing(val version: String) : UpdateState
    data class ConfirmInstall(val version: String, val sessionId: Int, val confirmation: Intent) : UpdateState
}

class UpdateManager(
    private val context: Context,
    private val isBetaEnabled: () -> Boolean = { false },
) {

    private val baseUrl = "https://github.com/andyhenriquezluciano/Numa/releases/latest/download"
    private val jsonUrl = "$baseUrl/latest-android.json"
    private val githubReleasesUrl = "https://api.github.com/repos/andyhenriquezluciano/Numa/releases"

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()
    private val checking = AtomicBoolean(false)
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun checkForUpdates() {
        // Debug and canary builds carry their own versionNameSuffix and applicationId; the
        // published release APK is neither an upgrade nor installable over them.
        if (!BuildConfig.UPDATER_ENABLED) return
        when (mutableState.value) {
            is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.ConfirmInstall -> return
            else -> Unit
        }
        if (!checking.compareAndSet(false, true)) return
        mutableState.value = UpdateState.Checking

        thread {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = packageInfo.versionName

                val metadata: MobileUpdateMetadata? = if (isBetaEnabled()) {
                    fetchBetaUpdateMetadata()
                } else {
                    fetchStableUpdateMetadata()
                }

                if (metadata != null && isNewerUpdate(metadata, currentVersion, packageInfo.longVersionCode)) {
                    // Surface it and let the user decide; downloading and firing the system
                    // installer unprompted is what made this feel like a random intrusion.
                    mutableState.value = UpdateState.Available(metadata)
                } else {
                    mutableState.value = if (metadata == null) UpdateState.CheckFailed else UpdateState.UpToDate
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                mutableState.value = UpdateState.CheckFailed
            } finally {
                checking.set(false)
            }
        }
    }

    private fun fetchStableUpdateMetadata(): MobileUpdateMetadata? {
        val release = runCatching {
            val connection = URL("$githubReleasesUrl/latest").openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Numa-Android")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) null
                else connection.inputStream.bufferedReader().use {
                    parseGitHubReleasesMetadata(JSONArray().put(JSONObject(it.readText())).toString())
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        return release ?: fetchLegacyUpdateMetadata()
    }

    private fun fetchLegacyUpdateMetadata(): MobileUpdateMetadata? = runCatching {
        val connection = URL(jsonUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseUpdateMetadata(response)
        } else {
            null
        }
    }.getOrNull()

    private fun fetchBetaUpdateMetadata(): MobileUpdateMetadata? = runCatching {
        val connection = URL(githubReleasesUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Numa-Android")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val meta = parseGitHubReleasesMetadata(response)
            if (meta != null) return@runCatching meta
        }

        // If GitHub releases did not return beta metadata, check if there's a newer stable release.
        fetchStableUpdateMetadata()
    }.getOrNull()

    fun dismiss() {
        val confirmation = mutableState.value as? UpdateState.ConfirmInstall
        if (confirmation != null) {
            runCatching { context.packageManager.packageInstaller.abandonSession(confirmation.sessionId) }
        }
        mutableState.value = UpdateState.Idle
    }

    fun previewUpdate() {
        mutableState.value = UpdateState.Available(MobileUpdateMetadata(
            version = BuildConfig.VERSION_NAME,
            codename = "Vista previa",
            releaseNotes = MobileChangelog.CURRENT_RELEASE_NOTES,
            isPreview = true,
        ))
    }

    companion object {
        private const val TAG = "UpdateManager"

        fun isNewerUpdate(metadata: MobileUpdateMetadata, installedVersion: String?, installedCode: Long): Boolean {
            if (metadata.isPreview) return false
            if (metadata.versionCode > 0 && installedCode > 0) {
                return metadata.versionCode.toLong() > installedCode
            }
            val comparison = compareVersions(metadata.version, installedVersion)
            return comparison > 0
        }


        fun parseUpdateMetadata(jsonString: String): MobileUpdateMetadata {
            val json = JSONObject(jsonString)
            return MobileUpdateMetadata(
                version = json.getString("version"),
                codename = json.optString("codename", ""),
                versionCode = json.optInt("versionCode", 0),
                apkUrl = json.optString("apkUrl", ""),
                sha256 = json.optString("sha256", ""),
                publishedAt = json.optString("publishedAt", ""),
                releaseNotes = json.optString("releaseNotes", ""),
            )
        }

        fun parseGitHubReleasesMetadata(
            releasesJson: String,
            fetchManifest: (String) -> String? = { url ->
                runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else null
                }.getOrNull()
            },
        ): MobileUpdateMetadata? {
            val array = JSONArray(releasesJson)
            for (i in 0 until array.length()) {
                val releaseObj = array.getJSONObject(i)
                val tagName = releaseObj.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
                if (tagName.isEmpty()) continue

                val assets = releaseObj.optJSONArray("assets") ?: JSONArray()
                var manifestUrl: String? = null
                var apkUrl: String? = null
                var apkVersion: String? = null
                var apkDigest = ""

                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.equals("latest-android.json", ignoreCase = true)) {
                        manifestUrl = downloadUrl
                    } else if (name.endsWith(".apk", ignoreCase = true) && apkUrl == null) {
                        apkUrl = downloadUrl
                        apkVersion = Regex("^Numa-(?:Android-)?(\\d+(?:\\.\\d+)+(?:-[A-Za-z0-9.]+)?)\\.apk$", RegexOption.IGNORE_CASE)
                            .matchEntire(name)?.groupValues?.get(1)
                        apkDigest = asset.optString("digest", "").removePrefix("sha256:")
                    }
                }

                if (manifestUrl != null) {
                    val manifestContent = fetchManifest(manifestUrl)
                    if (manifestContent != null) {
                        return parseUpdateMetadata(manifestContent)
                    }
                }

                if (apkUrl != null) {
                    val body = releaseObj.optString("body", "")
                    val publishedAt = releaseObj.optString("published_at", "")
                    val buildMarker = Regex("<!--\\s*android-version-code:\\s*(\\d+)\\s*-->")
                    val versionCode = buildMarker.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    return MobileUpdateMetadata(
                        version = apkVersion ?: tagName.substringBefore('+'),
                        versionCode = versionCode,
                        apkUrl = apkUrl,
                        sha256 = apkDigest.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }.orEmpty(),
                        releaseNotes = body.replace(buildMarker, "").trim(),
                        publishedAt = publishedAt,
                    )
                }
            }
            return null
        }

        fun compareVersions(latest: String, current: String?): Int {
            if (current == null) return 1

            fun parseSemver(value: String): Pair<List<Int>, String?> {
                val clean = value.trim().removePrefix("v").removePrefix("V").substringBefore('+')
                val dashIndex = clean.indexOf('-')
                val (base, pre) = if (dashIndex >= 0) {
                    clean.substring(0, dashIndex) to clean.substring(dashIndex + 1)
                } else {
                    clean to null
                }
                val numbers = base.split(".").map { segment ->
                    segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0
                }
                return numbers to pre
            }

            val (lParts, lPre) = parseSemver(latest)
            val (cParts, cPre) = parseSemver(current)

            val length = maxOf(lParts.size, cParts.size)
            for (i in 0 until length) {
                val l = lParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (l > c) return 1
                if (l < c) return -1
            }

            if (lPre == null && cPre == null) return 0
            if (lPre == null && cPre in listOf("debug", "canary", "rc1")) return 0
            if (cPre == null && lPre in listOf("debug", "canary", "rc1")) return 0

            if (lPre == null && cPre != null) return 1
            if (lPre != null && cPre == null) return -1

            if (lPre != null && cPre != null) {
                if (lPre == cPre) return 0
                fun preParts(s: String) = s.split(".").map { seg ->
                    seg.toIntOrNull()?.let { Pair(true, it) } ?: Pair(false, seg)
                }
                val lp = preParts(lPre)
                val cp = preParts(cPre)
                val pLen = maxOf(lp.size, cp.size)
                for (i in 0 until pLen) {
                    val lSeg = lp.getOrNull(i)
                    val cSeg = cp.getOrNull(i)
                    if (lSeg == null) return -1
                    if (cSeg == null) return 1
                    if (lSeg.first && cSeg.first) {
                        val lNum = lSeg.second as Int
                        val cNum = cSeg.second as Int
                        if (lNum != cNum) return lNum.compareTo(cNum)
                    } else {
                        val lStr = lSeg.second.toString()
                        val cStr = cSeg.second.toString()
                        if (lStr != cStr) return lStr.compareTo(cStr)
                    }
                }
            }

            return 0
        }
    }

    private var activeDownload: UpdateDownload? = null

    fun downloadAndInstallUpdate(metadata: MobileUpdateMetadata) {
        if (metadata.isPreview) {
            dismiss()
            return
        }
        if (mutableState.value !is UpdateState.Available) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                mutableState.value = UpdateState.Failed(metadata.version, it.message.orEmpty())
            }
            return
        }
        activeDownload?.cancel()
        mutableState.value = UpdateState.Downloading(metadata.version)
        activeDownload = UpdateDownload(context, metadata, baseUrl,
            { mutableState.value = it }, { installApk(it, metadata.version) }).also { it.start() }
    }

    private fun installApk(apkFile: File, version: String) {
        mutableState.value = UpdateState.Installing(version)
        installScope.launch {
            try {
                installUpdateSession(context, apkFile, version)
            } catch (error: Exception) {
                mutableState.value = UpdateState.Failed(version, error.message.orEmpty())
            }
        }
    }

    internal fun onInstallStatus(intent: Intent) {
        val version = intent.getStringExtra(UpdateInstallReceiver.VERSION) ?: return
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        mutableState.value = when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> if (confirmation != null) {
                UpdateState.ConfirmInstall(version, sessionId, confirmation)
            } else {
                UpdateState.Failed(version, uiText("Android did not provide an installation confirmation.", "Android no proporcionó una confirmación de instalación."))
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Idle
            else -> UpdateState.Failed(version, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
        }
    }
}
