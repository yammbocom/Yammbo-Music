package it.fast4x.riplay.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

/**
 * Resolve the latest published version as (versionCode, versionName) WITHOUT hitting
 * api.github.com from the client.
 *
 * Primary source is our own VPS probe `music.yammbo.com/download/version.json` — a tiny
 * (~40 byte) JSON resolved server-side from the GitHub Releases API and cached 1h, served
 * through Cloudflare. GitHub's Releases API is only a fallback.
 *
 * Rationale: the unauthenticated GitHub API is capped at 60 req/h PER IP. On shared
 * mobile-carrier NAT IPs (CGNAT) that quota is routinely exhausted by other users → 403 →
 * update detection silently failed. The VPS probe has no such limit. No APK bytes touch
 * the VPS: the APK itself still downloads from GitHub's CDN via the redirect route.
 *
 * versionCode is the last dotted segment of the version name (project invariant:
 * "0.7.N" ↔ versionCode N). Returns null if neither source yields a valid version.
 */
fun fetchLatestVersion(client: OkHttpClient): Pair<Int, String>? {
    // 1) Primary: VPS version probe (tiny JSON, no rate limit).
    try {
        val request = Request.Builder()
            .url("https://music.yammbo.com/download/version.json")
            .header("Accept", "application/json")
            .header("User-Agent", "Yammbo-Music-UpdateChecker")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = org.json.JSONObject(body)
                    val code = json.optInt("versionCode", 0)
                    val name = json.optString("versionName").trim()
                    if (code > 0 && name.isNotBlank()) return Pair(code, name)
                }
            } else {
                Timber.d("version.json probe ${response.code}")
            }
        }
    } catch (e: Exception) {
        Timber.d("version.json probe error ${e.message}")
    }

    // 2) Fallback: GitHub Releases API (parses tag_name "v0.7.N").
    try {
        val request = Request.Builder()
            .url("https://api.github.com/repos/yammbocom/Yammbo-Music/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Yammbo-Music-UpdateChecker")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val name = org.json.JSONObject(body)
                        .optString("tag_name").removePrefix("v").trim()
                    val code = name.substringAfterLast('.').toIntOrNull() ?: 0
                    if (code > 0 && name.isNotBlank()) return Pair(code, name)
                }
            } else {
                Timber.d("GitHub releases fallback ${response.code}")
            }
        }
    } catch (e: Exception) {
        Timber.d("GitHub releases fallback error ${e.message}")
    }

    return null
}

/**
 * One-shot update check fired on cold start (see MainActivity/HomeScreen) and by the
 * manual "check now" button. Resolves the latest version via [fetchLatestVersion] and
 * caches it into `UpdatedVersionCode.ver` so [getAvailableUpdateInfo] can pick it up.
 *
 * IMPORTANT: this function MUST actually suspend until the network call completes and the
 * .ver file is written, so callers that immediately read `getAvailableUpdateInfo()`
 * afterwards see the updated values.
 */
suspend fun checkAndDownloadNewVersionCode() = withContext(Dispatchers.IO) {
    val (remoteVersionCode, remoteVersionName) =
        fetchLatestVersion(okHttpClient()) ?: return@withContext

    val content = "$remoteVersionCode-$remoteVersionName-Yammbo Music\n"
    File(appContext().filesDir, "UpdatedVersionCode.ver").writeText(content)
}
