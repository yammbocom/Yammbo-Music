package it.fast4x.riplay.utils

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.FileProvider
import com.yambo.music.BuildConfig
import it.fast4x.riplay.extensions.preferences.dataSaverMobileKey
import it.fast4x.riplay.extensions.preferences.lastDismissedUpdateVersionCodeKey
import it.fast4x.riplay.extensions.preferences.preferences
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
 *
 * Updates are also fetched ahead of time ([autoStart], from the cold-start check and from the
 * periodic worker), so by the time the dialog opens the file is usually already there and the
 * button reads "Install". The install itself goes through a PackageInstaller session
 * ([installViaSession]) with the old ACTION_VIEW intent kept as a fallback.
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

    // Set by "Later": no automatic installer for the rest of this process. The dialog's
    // "Install" button still works. A new process starts clear, but autoStart skips snoozed versions.
    @Volatile private var autoInstallHeld = false

    fun holdAutoInstall() {
        autoInstallHeld = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private const val DIR = "updates"

    /** Broadcast action of the PackageInstaller status callback ([UpdateInstallReceiver]). */
    const val ACTION_INSTALL_STATUS = "com.yambo.music.UPDATE_INSTALL_STATUS"

    /**
     * Data saver is on by default; with it on, a metered connection (mobile data) never fetches
     * an update by itself. The user can still tap "Update now" in the dialog.
     */
    fun canAutoDownloadNow(context: Context): Boolean = runCatching {
        val saver = context.preferences.getBoolean(dataSaverMobileKey, true)
        !(saver && context.isConnectionMetered())
    }.getOrDefault(false)

    /**
     * Quietly fetches an announced update: nothing to do if it is installed already, if the user
     * snoozed it with "Later", or if this is not the self-updating build. If the file is already
     * on disk it is just offered; otherwise it is downloaded unless the data saver forbids it.
     * Returns true when a download was started or the file is ready.
     */
    fun autoStart(context: Context, versionName: String, versionCode: Int): Boolean {
        if (BuildConfig.BUILD_VARIANT != "full") return false
        val app = context.applicationContext
        val version = versionName.trim().removePrefix("v")
        if (version.isBlank() || versionCode <= installedVersionCode(app)) return false
        val snoozed = runCatching {
            app.preferences.getInt(lastDismissedUpdateVersionCodeKey, 0) >= versionCode
        }.getOrDefault(false)
        if (snoozed) return false
        if (restoreIfDownloaded(app, version)) return true
        if (!canAutoDownloadNow(app)) return false
        start(app, version)
        return true
    }

    /** Suspends until the download under way (if any) ends; for the background workers. */
    suspend fun awaitCurrent() {
        job?.join()
    }

    /** A finished download from an earlier session of the app: offer it again without refetching. */
    private fun restoreIfDownloaded(context: Context, version: String): Boolean {
        val current = _state.value
        if (current is State.Ready && current.version == version && current.file.exists()) return true
        if (job?.isActive == true) return false
        val file = File(File(context.cacheDir, DIR), "YammboMusic-v$version.apk")
        if (!file.exists()) return false
        val info = runCatching { context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull()
        if (info?.packageName != context.packageName || versionCodeOf(info) <= installedVersionCode(context)) {
            file.delete()
            return false
        }
        _state.value = State.Ready(version, file)
        return true
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()

    private fun installedVersionCode(context: Context): Long = runCatching {
        versionCodeOf(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    /**
     * Once the installed version has caught up with a downloaded file (the update went through),
     * the APK is dead weight: remove it. Meant for cold start, when no download is under way.
     */
    fun cleanupIfInstalled(context: Context) {
        if (job?.isActive == true) return
        val files = File(context.cacheDir, DIR).listFiles() ?: return
        val installed = installedVersionCode(context)
        files.forEach { file ->
            val stale = if (file.name.endsWith(".part")) true else {
                val info = runCatching {
                    context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                }.getOrNull()
                val code = info?.let { versionCodeOf(it) }
                code == null || code <= installed
            }
            if (stale) file.delete()
        }
        val ready = _state.value as? State.Ready
        if (ready != null && !ready.file.exists()) _state.value = State.Idle
    }

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
        // Android refuses a downgrade or the same version; don't offer an install that can only fail.
        if (versionCodeOf(info) <= installedVersionCode(context)) {
            Timber.e("UpdateDownloader: downloaded versionCode ${versionCodeOf(info)} is not newer than the installed one")
            partial.delete()
            return null
        }
        if (!partial.renameTo(target)) return null
        return target
    }

    /**
     * Called by MainActivity while it is resumed: opens the installer once per download, unless
     * something is playing (the update restarts the app and would cut the music off); then it
     * waits for the next time the app comes to the foreground, or for the dialog button.
     */
    fun installIfReady(context: Context, isPlaying: Boolean = false) {
        val ready = _state.value as? State.Ready ?: return
        if (installerShownFor == ready.file) return
        if (isPlaying || autoInstallHeld) return
        install(context)
    }

    /** Install the finished download. Must be called from the foreground. */
    fun install(context: Context) {
        val ready = _state.value as? State.Ready ?: return
        if (!ready.file.exists()) {
            _state.value = State.Idle
            return
        }
        installerShownFor = ready.file
        val started = runCatching { installViaSession(context.applicationContext, ready.file) }
            .onFailure { Timber.e("UpdateDownloader session install failed: ${it.message}") }
            .getOrDefault(false)
        if (started) AppEvents.log(AppEvents.UPDATE_INSTALL, detail = ready.version)
        else installViaView(context)
    }

    /**
     * Streams the APK into a PackageInstaller session and commits it; the result comes back to
     * [UpdateInstallReceiver].
     *
     * The first time, the app is not yet the "installer of record" of itself (it was installed by
     * a browser, a file manager or adb), so Android shows its confirmation screen once. After
     * that, on Android 12+ the session asks for USER_ACTION_NOT_REQUIRED and the update installs
     * without any prompt. On Android 14+ the app also asks for update ownership so that other
     * stores do not take that over.
     */
    private fun installViaSession(context: Context, file: File): Boolean {
        val installer = context.packageManager.packageInstaller
        // A session left half-way by an earlier attempt would hold on to the app's slot.
        runCatching { installer.mySessions.forEach { installer.abandonSession(it.sessionId) } }

        fun params(withOwnership: Boolean) = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (withOwnership && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                setRequestUpdateOwnership(true)
        }

        val sessionId = try {
            installer.createSession(params(true))
        } catch (e: SecurityException) {
            // Update ownership needs a permission some ROMs do not grant: go without it.
            installer.createSession(params(false))
        }
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("YammboMusic.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                    .setPackage(context.packageName)
                // Mutable on purpose: the installer fills in the status and the confirmation
                // intent as extras, and it cannot do that to an immutable PendingIntent.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
        return true
    }

    /** The old way: hand the file to the system installer with an ACTION_VIEW intent. */
    fun installViaView(context: Context) {
        val ready = _state.value as? State.Ready ?: return
        if (!ready.file.exists()) {
            _state.value = State.Idle
            return
        }
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
