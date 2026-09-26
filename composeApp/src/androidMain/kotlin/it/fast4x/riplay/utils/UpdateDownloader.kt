package it.fast4x.riplay.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import it.fast4x.riplay.extensions.yammboapi.AppEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a new release inside the app and hands it to the system installer.
 *
 * It replaced a DownloadManager download whose completion broadcast tried to open the installer
 * from the application context: Android does not let an app start an activity from the
 * background, so the installer only ever appeared once the user tapped the notification. Here the
 * dialog shows the progress, and the installer is opened by MainActivity while it is on screen
 * (at once if it is, or as soon as the user comes back to the app).
 */
object UpdateDownloader {

    sealed interface State {
        data object Idle : State
        data class Downloading(val version: String, val progress: Float?) : State
        data class Ready(val version: String, val file: File) : State
        data class Failed(val version: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    // The installer is opened on its own once per finished download; after that it is the
    // dialog's "Install" button, so dismissing the installer does not make it pop up again.
    @Volatile private var installerShownFor: File? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private const val DIR = "updates"

    fun start(context: Context, versionName: String) {
        val version = versionName.trim().removePrefix("v")
        val current = _state.value
        if (job?.isActive == true && current is State.Downloading && current.version == version) return
        // Already here: the dialog offers to install it.
        if (current is State.Ready && current.version == version && current.file.exists()) return
        val appContext = context.applicationContext
        job?.cancel()
        val gen = ++generation
        _state.value = State.Downloading(version, null)
        job = scope.launch {
            publish(gen, runCatching { download(appContext, version, gen) }
                .onFailure { Timber.e("UpdateDownloader failed: ${it.message}") }
                .getOrNull()
                ?.let { State.Ready(version, it) }
                ?: State.Failed(version))
        }
    }

    // Which download is the current one. A cancelled download can still be finishing a read, and
    // its "failed" (its file is gone) must not overwrite the progress of the one that replaced it.
    @Volatile private var generation = 0

    private fun publish(gen: Int, state: State) {
        if (gen == generation) _state.value = state
    }

    private suspend fun download(context: Context, version: String, gen: Int): File? {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        // Only one release is ever worth keeping.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "YammboMusic-v$version.apk")
        val partial = File(dir, "${target.name}.part")

        var url = URL(getUpdateApkDirectUrl(version))
        var connection: HttpURLConnection
        // The mirror redirects to GitHub and GitHub to its asset storage; followed by hand so a
        // redirect between hosts is never dropped.
        var hops = 0
        while (true) {
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val code = connection.responseCode
            if (code in 300..399 && hops++ < 6) {
                val location = connection.getHeaderField("Location") ?: return null
                connection.disconnect()
                url = URL(url, location)
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                Timber.e("UpdateDownloader HTTP $code for $url")
                connection.disconnect()
                return null
            }
            break
        }

        val total = connection.contentLengthLong.takeIf { it > 0 }
        connection.inputStream.use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var lastReported = -1
                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total != null) {
                        val percent = (done * 100 / total).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            publish(gen, State.Downloading(version, done.toFloat() / total))
                        }
                    }
                }
            }
        }
        connection.disconnect()
        if (total != null && partial.length() != total) return null

        // Never hand the installer a file that is not this app: a captive portal or an error
        // page saved as .apk would otherwise surface as a baffling "problem parsing the package".
        val info = context.packageManager.getPackageArchiveInfo(partial.absolutePath, 0)
        if (info?.packageName != context.packageName) {
            Timber.e("UpdateDownloader: downloaded file is not ${context.packageName} (${info?.packageName})")
            partial.delete()
            return null
        }
        if (!partial.renameTo(target)) return null
        return target
    }

    /** Called by MainActivity while it is resumed: opens the installer once per download. */
    fun installIfReady(context: Context) {
        val ready = _state.value as? State.Ready ?: return
        if (installerShownFor == ready.file) return
        install(context)
    }

    /** Open the system installer for the finished download. Must be called from the foreground. */
    fun install(context: Context) {
        val ready = _state.value as? State.Ready ?: return
        if (!ready.file.exists()) {
            _state.value = State.Idle
            return
        }
        installerShownFor = ready.file
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                ready.file,
            )
            // Without "install unknown apps" granted, the system itself asks for it on the way
            // and then carries on with the install.
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppEvents.log(AppEvents.UPDATE_INSTALL, detail = ready.version)
        }.onFailure { Timber.e("UpdateDownloader install failed: ${it.message}") }
    }

    /** Last resort when the download itself keeps failing: let the browser fetch it. */
    fun openInBrowser(context: Context, versionName: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(getUpdateApkDirectUrl(versionName)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
