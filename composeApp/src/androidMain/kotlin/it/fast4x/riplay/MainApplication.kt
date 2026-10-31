package it.fast4x.riplay

import com.yambo.music.R
import android.content.ComponentName
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.multidex.MultiDexApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.memory.MemoryCache
import coil.request.CachePolicy
import it.fast4x.riplay.data.DatabaseInitializer
import it.fast4x.riplay.enums.CoilDiskCacheMaxSize
import it.fast4x.riplay.utils.applyLogTree
import it.fast4x.riplay.extensions.preferences.coilCustomDiskCacheKey
import it.fast4x.riplay.extensions.preferences.coilDiskCacheMaxSizeKey
import it.fast4x.riplay.extensions.preferences.getEnum
import it.fast4x.riplay.extensions.preferences.logDebugEnabledKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.transitionEffectKey
import it.fast4x.riplay.enums.TransitionEffect
import it.fast4x.riplay.extensions.preferences.usePlaceholderInImageLoaderKey
import it.fast4x.riplay.commonutils.ThumbnailSizing
import it.fast4x.riplay.commonutils.initializeEnvironment
import it.fast4x.riplay.extensions.preferences.dataSaverMobileKey
import it.fast4x.riplay.utils.isConnectionMetered
import it.fast4x.riplay.extensions.crashreporter.CrashReporter
import it.fast4x.riplay.extensions.appviewmodel.AppViewModel
import it.fast4x.riplay.extensions.appviewmodel.models.NetworkConnectivity
import it.fast4x.riplay.extensions.appviewmodel.observeNetworkType
import it.fast4x.riplay.service.PlayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

private const val NOIR_MOTION_MIGRATED_KEY = "noirMotionMigrated"

class MainApplication : MultiDexApplication(), ImageLoaderFactory {

    // Stato condiviso accessibile ovunque, anche dai Service
    val networkConnectivity: StateFlow<NetworkConnectivity>
        get() = _networkConnectivity

    private val _networkConnectivity = MutableStateFlow<NetworkConnectivity>(
        NetworkConnectivity.Disconnected
    )

    private val appScopeMain = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val appViewModelFactory by lazy {
        AppViewModel.factory(this)
    }

    // One-time: move users still on the old default (Scale) to the Noir shared axis transition.
    // Done here, before any Activity, because MainActivity recreates itself when the key changes.
    private fun migrateNoirMotion() {
        val prefs = preferences
        if (prefs.getBoolean(NOIR_MOTION_MIGRATED_KEY, false)) return
        val current = prefs.getString(transitionEffectKey, null)
        prefs.edit().apply {
            if (current == null || current == TransitionEffect.Scale.name) {
                putString(transitionEffectKey, TransitionEffect.SharedAxis.name)
            }
            putBoolean(NOIR_MOTION_MIGRATED_KEY, true)
        }.apply()
    }

    // Data saver = preference on AND the default network is metered. Kept in ThumbnailSizing
    // (read by thumbnail() and the Home feed). One NetworkCallback at most; the prefs listener
    // is a field because SharedPreferences only holds listeners weakly.
    @Volatile private var networkMetered = true
    private var dataSaverNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val dataSaverPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == dataSaverMobileKey) refreshDataSaver()
    }

    private fun refreshDataSaver() {
        ThumbnailSizing.dataSaver = runCatching {
            preferences.getBoolean(dataSaverMobileKey, true)
        }.getOrDefault(true) && networkMetered
    }

    private fun initThumbnailSizing() {
        runCatching {
            ThumbnailSizing.densityFactor = (resources.displayMetrics.density / 3f).coerceIn(0.5f, 1f)
        }
        runCatching { networkMetered = isConnectionMetered() }
        refreshDataSaver()
        runCatching { preferences.registerOnSharedPreferenceChangeListener(dataSaverPrefsListener) }
        runCatching {
            if (dataSaverNetworkCallback != null) return@runCatching
            val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    networkMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    refreshDataSaver()
                }
            }
            manager.registerDefaultNetworkCallback(callback)
            dataSaverNetworkCallback = callback
        }.onFailure { Timber.e("MainApplication data saver callback ${it.message}") }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        migrateNoirMotion()
        initThumbnailSizing()

        appScopeMain.launch {
            observeNetworkType(this@MainApplication).collect {
                _networkConnectivity.value = it
            }
        }

        val receiver = ComponentName(this, PlayerService::class.java)
        val pm = packageManager

        pm.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        Dependencies.init(this)

        /***** CRASH LOG ALWAYS ENABLED *****/
        val dir = filesDir.resolve("logs").also {
            if (it.exists()) return@also
            it.mkdir()
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashReporter(dir.absolutePath))
        /***** CRASH LOG ALWAYS ENABLED *****/

        /**** LOG *********/
        applyLogTree(this, preferences.getBoolean(logDebugEnabledKey, false))
        /**** LOG *********/
    }

    override fun newImageLoader(): ImageLoader {
        val coilCustomDiskCache = preferences.getInt(coilCustomDiskCacheKey, 128) * 1000 * 1000L
        val coilDiskCacheMaxSize = preferences.getEnum(coilDiskCacheMaxSizeKey,CoilDiskCacheMaxSize.`128MB`)
        val usePlaceholder = preferences.getBoolean(usePlaceholderInImageLoaderKey, true)
        val coilCacheSize = when (coilDiskCacheMaxSize) {
            CoilDiskCacheMaxSize.Custom -> coilCustomDiskCache
            else -> coilDiskCacheMaxSize.bytes
        }


        // Own client: OkHttp's default of 5 requests per host serializes Home covers, which
        // all come from lh3.googleusercontent.com.
        val coilHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            })
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(coilHttpClient)
            .components {
                // hq720.jpg does not exist for every video; mqdefault.jpg always does.
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        val result = chain.proceed(chain.request)
                        val data = chain.request.data
                        if (result is ErrorResult && data is String &&
                            data.startsWith("https://i.ytimg.com/vi/") && data.contains("/hq720.jpg")
                        ) {
                            return chain.proceed(
                                chain.request.newBuilder()
                                    .data(data.replace("/hq720.jpg", "/mqdefault.jpg"))
                                    .build()
                            )
                        }
                        return result
                    }
                })
            }
            .crossfade(true)
            //.allowHardware(if (isAtLeastAndroid8) true else false)
            //.bitmapConfig(if (isAtLeastAndroid8) Bitmap.Config.HARDWARE else Bitmap.Config.ARGB_8888)
            //.networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .error(R.drawable.noimage)
            .fallback(R.drawable.noimage)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache(
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            )
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache(
                DiskCache.Builder()
                    .directory(filesDir.resolve("coil"))
                    .maxSizeBytes(
                        if (coilCacheSize == 0L) CoilDiskCacheMaxSize.`128MB`.bytes
                        else coilCacheSize
                    )
                    .build()
            )
            .apply {
                if (usePlaceholder) {
                    placeholder(R.drawable.loader)
                }
            }
            .build()
    }

}

object Dependencies {
    lateinit var application: MainApplication
        private set

    internal fun init(application: MainApplication) {
        this.application = application
        DatabaseInitializer()
        initializeEnvironment() // multiplatform initialization
    }
}