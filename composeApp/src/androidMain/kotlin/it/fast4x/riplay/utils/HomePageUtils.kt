package it.fast4x.riplay.utils

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import it.fast4x.environment.Environment
import it.fast4x.environment.requests.HomePage
import it.fast4x.environment.requests.chartsPageComplete
import com.yambo.music.R
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.MenuStyle
import it.fast4x.riplay.enums.PlayEventsType
import it.fast4x.riplay.enums.TopPlaylistPeriod
import it.fast4x.riplay.extensions.preferences.autoShuffleKey
import it.fast4x.riplay.extensions.preferences.menuStyleKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.topPlaylistPeriodKey
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.GlobalSheetState
import it.fast4x.riplay.ui.components.themed.PeriodMenu
import it.fast4x.riplay.ui.components.tab.toolbar.Descriptive
import it.fast4x.riplay.ui.components.tab.toolbar.DualIcon
import it.fast4x.riplay.ui.components.tab.toolbar.DynamicColor
import it.fast4x.riplay.ui.components.tab.toolbar.Menu
import it.fast4x.riplay.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class HiddenSongs private constructor(
    private val showHiddenState: MutableState<Boolean>
): MenuIcon, DualIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init() = HiddenSongs(
            rememberSaveable { mutableStateOf( false ) }
        )
    }

    override val iconId: Int = R.drawable.eye
    override val secondIconId: Int = R.drawable.eye_off
    override val messageId: Int = R.string.info_show_hide_hidden_songs
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isFirstIcon: Boolean = showHiddenState.value
        set(value) {
            showHiddenState.value = value
            field = value
        }

    fun isShown() = if( isFirstIcon ) 0 else 1

    override fun onShortClick() { isFirstIcon = !isFirstIcon }
}

@Composable
fun randomSort(): MenuIcon = object: MenuIcon, DynamicColor, Descriptive {

    override var isFirstColor: Boolean by rememberPreference(autoShuffleKey, false)
    override val iconId: Int = R.drawable.random
    override val messageId: Int = R.string.random_sorting
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() { isFirstColor = !isFirstColor }
}

class PeriodSelector private constructor(
    private val periodState: MutableState<TopPlaylistPeriod>,
    override val globalSheetState: GlobalSheetState,
    override val styleState: MutableState<MenuStyle>
):  MenuIcon, Descriptive, Menu {

    companion object {
        @JvmStatic
        @Composable
        fun init() = PeriodSelector(
            rememberPreference(topPlaylistPeriodKey, TopPlaylistPeriod.PastWeek),
            LocalGlobalSheetState.current,
            rememberPreference(menuStyleKey, MenuStyle.List)
        )
    }

    var period: TopPlaylistPeriod = periodState.value
        set(value) {
            periodState.value = value
            field = value
        }

    override val iconId: Int = period.iconId
    override val messageId: Int = R.string.statistics
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    fun onDismiss( period: TopPlaylistPeriod ) {
        this.period = period
        globalSheetState.hide()
    }

    @Composable
    override fun ListMenu() { /* Does nothing */ }

    @Composable
    override fun GridMenu() { /* Does nothing */ }

    @Composable
    override fun MenuComponent() = PeriodMenu(::onDismiss)

    override fun onShortClick() = super.onShortClick()
}

/**
 * What the last successful Home load put on disk. `trending` is a Room entity and is deliberately
 * not part of it: it is rebuilt from the database on every load.
 */
@Serializable
private data class HomeDiskSnapshot(
    val version: Int = 1,
    val key: String = "",
    val playEventType: String = "",
    val homePage: HomePage? = null,
    val discoverPage: Environment.DiscoverPage? = null,
    val relatedPage: Environment.RelatedPage? = null,
    val homeContinuation: String? = null
)

/**
 * Shows [skeleton] while [loading], then [content]. The content fades in (~200 ms) only when this
 * slot really drew a skeleton before: data that was already there (memory or disk cache) is
 * painted at once and silently replaced by the fresh answer.
 */
@Composable
fun SkeletonSwap(
    loading: Boolean,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    var sawSkeleton by remember { mutableStateOf(loading) }
    LaunchedEffect(loading) {
        if (loading) sawSkeleton = true
    }

    if (loading) skeleton() else FadeInContent(fade = sawSkeleton, content = content)
}

@Composable
private fun FadeInContent(
    fade: Boolean,
    content: @Composable () -> Unit
) {
    // Starting value is decided once: later recompositions must not restart the fade
    val alpha = remember { Animatable(if (fade) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (alpha.value < 1f) alpha.animateTo(1f, tween(200))
    }

    Column(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) {
        content()
    }
}

object HomeDataCache {
    var homePage: HomePage? = null
    var discoverPage: Environment.DiscoverPage? = null
    var relatedPage: Environment.RelatedPage? = null
    var trending: Song? = null

    // Token of the next Home page when the continuation chain has not finished yet
    var homeContinuation: String? = null

    // True while the value in memory is what was read from disk (painted, but still to be
    // replaced by the network answer).
    var homeFromDisk = false
    var discoverFromDisk = false
    var relatedFromDisk = false

    // videoId of a current top-charts track. Used as seed for Selecciones Rapidas
    // when the user has no recent plays so the section never stays empty.
    var fallbackTopSongId: String? = null

    var lastCountryCode: String? = null
    var lastPlayEventType: PlayEventsType? = null

    // The disk snapshot is read once per process, and never after the user asked for fresh data
    private var diskChecked = false

    private const val DISK_FILE = "home_cache_v1.json"
    private const val DISK_VERSION = 1
    private val diskJson = Json { ignoreUnknownKeys = true }

    fun clear() {
        homePage = null
        discoverPage = null
        relatedPage = null
        trending = null
        homeContinuation = null
        homeFromDisk = false
        discoverFromDisk = false
        relatedFromDisk = false
        fallbackTopSongId = null
        lastCountryCode = null
        lastPlayEventType = null
    }

    /**
     * Pull to refresh: forget memory AND disk, and never read the old file again in this process.
     */
    fun clearAll(context: Context) {
        clear()
        diskChecked = true
        runCatching { File(context.cacheDir, DISK_FILE).delete() }
    }

    /**
     * Fills the empty slots of the memory cache from the last snapshot on disk, if it was taken
     * for the same [key] (country, content country, login). Does nothing after the first call.
     * Returns true when something was loaded.
     */
    suspend fun loadFromDisk(
        context: Context,
        key: String,
        playEventType: PlayEventsType
    ): Boolean = withContext(Dispatchers.IO) {
        if (diskChecked) return@withContext false
        diskChecked = true
        runCatching {
            val file = File(context.cacheDir, DISK_FILE)
            if (!file.exists()) return@runCatching false
            val snapshot = diskJson.decodeFromString(HomeDiskSnapshot.serializer(), file.readText())
            if (snapshot.version != DISK_VERSION || snapshot.key != key) return@runCatching false

            var loaded = false
            if (homePage == null && snapshot.homePage != null) {
                homePage = snapshot.homePage
                homeContinuation = snapshot.homeContinuation
                homeFromDisk = true
                loaded = true
            }
            if (discoverPage == null && snapshot.discoverPage != null) {
                discoverPage = snapshot.discoverPage
                discoverFromDisk = true
                loaded = true
            }
            // Quick picks depend on what the listener played: only reuse them for the same mode
            if (relatedPage == null && snapshot.relatedPage != null
                && snapshot.playEventType == playEventType.name
            ) {
                relatedPage = snapshot.relatedPage
                relatedFromDisk = true
                loaded = true
            }
            loaded
        }.getOrDefault(false)
    }

    /** Writes what is in memory to disk. Call it from any thread: it hops to IO itself. */
    suspend fun saveToDisk(
        context: Context,
        key: String,
        playEventType: PlayEventsType
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = HomeDiskSnapshot(
                version = DISK_VERSION,
                key = key,
                playEventType = playEventType.name,
                homePage = homePage,
                discoverPage = discoverPage,
                relatedPage = relatedPage,
                homeContinuation = homeContinuation
            )
            // Nothing worth keeping
            if (snapshot.homePage == null && snapshot.discoverPage == null && snapshot.relatedPage == null)
                return@runCatching
            writeSnapshot(File(context.cacheDir, DISK_FILE), diskJson.encodeToString(HomeDiskSnapshot.serializer(), snapshot))
        }
        Unit
    }

    // Tmp file + rename so a process killed mid-write never leaves a half written snapshot
    @Synchronized
    private fun writeSnapshot(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}

/**
 * Resolve a "top of the moment" videoId to use as Selecciones Rápidas seed when the
 * user has no recent plays. Tries multiple sources in order so we never get stuck on
 * an infinite loader:
 *   1. chartsPage(preferredCountry) — exact match if user set a country
 *   2. chartsPage("")               — let YT auto-detect via IP
 *   3. chartsPage("US")             — large catalog, reliable fallback
 *   4. homePage tracks              — last resort, region-aware content already fetched
 *
 * `Countries.ZZ` ("Global") is mapped to "" because YT does not recognise ZZ and
 * returns empty charts. Result is cached for the session lifetime.
 */
suspend fun resolveFallbackTopSongId(
    preferredCountry: String,
    homePage: HomePage?
): String? {
    HomeDataCache.fallbackTopSongId?.let { return it }

    val normalised = if (preferredCountry.isBlank() || preferredCountry == "ZZ") "" else preferredCountry
    val attempts = listOf(normalised, "", "US").distinct()

    for (cc in attempts) {
        val charts = Environment.chartsPageComplete(countryCode = cc).getOrNull() ?: continue
        val pick = charts.songs?.firstOrNull { it.key.isNotEmpty() }?.key
            ?: charts.trending?.firstOrNull { it.key.isNotEmpty() }?.key
            ?: charts.videos?.firstOrNull { it.key.isNotEmpty() }?.key
        if (!pick.isNullOrEmpty()) {
            HomeDataCache.fallbackTopSongId = pick
            return pick
        }
    }

    // Final fallback: pluck a videoId from the already-fetched HomePage payload.
    val fromHome = homePage?.sections
        ?.flatMap { it.items.filterNotNull() }
        ?.filterIsInstance<Environment.SongItem>()
        ?.firstOrNull { it.key.isNotEmpty() }
        ?.key
    if (!fromHome.isNullOrEmpty()) {
        HomeDataCache.fallbackTopSongId = fromHome
        return fromHome
    }

    return null
}