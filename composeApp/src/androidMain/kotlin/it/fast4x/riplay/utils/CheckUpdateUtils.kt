package it.fast4x.riplay.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import okhttp3.Request
import timber.log.Timber
import java.io.File

/**
 * One-shot update check fired on cold start (see MainActivity).
 * Hits the GitHub Releases API and caches the latest version into
 * `UpdatedVersionCode.ver` so [getAvailableUpdateInfo] can pick it up.
 */
suspend fun checkAndDownloadNewVersionCode() {

    CoroutineScope(Dispatchers.Default).launch {
        val url = "https://api.github.com/repos/yammbocom/Yammbo-Music/releases/latest"
        val client = okHttpClient()

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Yammbo-Music-UpdateChecker")
                .build()

            val response = client.newCall(request).executeAsync()
            if (!response.isSuccessful) {
                Timber.d("UpdatedVersionCode GitHub API ${response.code}")
                return@launch
            }

            val body = response.body.string()
            val json = org.json.JSONObject(body)
            val tagName = json.optString("tag_name").ifBlank { return@launch }
            val remoteVersionName = tagName.removePrefix("v").trim()
            val remoteVersionCode = remoteVersionName.substringAfterLast('.')
                .toIntOrNull() ?: return@launch

            val content = "$remoteVersionCode-$remoteVersionName-Yammbo Music\n"

            withContext(Dispatchers.IO) {
                File(appContext().filesDir, "UpdatedVersionCode.ver").writeText(content)
            }

        } catch (e: IOException) {
            Timber.d("UpdatedVersionCode check network failure ${e.message}")
        } catch (e: Exception) {
            Timber.d("UpdatedVersionCode check error ${e.message}")
        }
    }

}