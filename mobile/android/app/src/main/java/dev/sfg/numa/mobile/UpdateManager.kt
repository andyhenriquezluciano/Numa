package dev.sfg.numa.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dev.sfg.numa.connect.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class MobileUpdateMetadata(
    val version: String,
    val codename: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val sha256: String = "",
    val publishedAt: String = "",
    val releaseNotes: String = "",
)

/** What the update flow is currently doing, for the in-app prompt to render. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val metadata: MobileUpdateMetadata) : UpdateState
    data class Downloading(val version: String) : UpdateState
    data class Failed(val version: String, val reason: String) : UpdateState
    data class ReadyToInstall(val version: String) : UpdateState
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

    fun checkForUpdates() {
        // Debug and canary builds carry their own versionNameSuffix and applicationId; the
        // published release APK is neither an upgrade nor installable over them.
        if (!BuildConfig.UPDATER_ENABLED) return

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
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
            }
        }
    }

    private fun fetchStableUpdateMetadata(): MobileUpdateMetadata? = runCatching {
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
        mutableState.value = UpdateState.Idle
    }

    companion object {
        private const val TAG = "UpdateManager"

        fun isNewerUpdate(metadata: MobileUpdateMetadata, installedVersion: String, installedCode: Long): Boolean {
            val comparison = compareVersions(metadata.version, installedVersion)
            return comparison > 0 || (comparison == 0 && metadata.versionCode.toLong() > installedCode)
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

                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.equals("latest-android.json", ignoreCase = true)) {
                        manifestUrl = downloadUrl
                    } else if (name.endsWith(".apk", ignoreCase = true) && apkUrl == null) {
                        apkUrl = downloadUrl
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
                    return MobileUpdateMetadata(
                        version = tagName,
                        apkUrl = apkUrl,
                        releaseNotes = body,
                        publishedAt = publishedAt,
                    )
                }
            }
            return null
        }

        fun compareVersions(latest: String, current: String?): Int {
            if (current == null) return 1

            fun parseSemver(value: String): Pair<List<Int>, String?> {
                val clean = value.trim().removePrefix("v").removePrefix("V")
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
        activeDownload?.cancel()
        activeDownload = UpdateDownload(context, metadata, baseUrl,
            { mutableState.value = it }, ::installApk).also { it.start() }
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
