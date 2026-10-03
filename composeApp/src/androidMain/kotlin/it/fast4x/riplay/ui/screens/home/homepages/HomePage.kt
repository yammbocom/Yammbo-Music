package it.fast4x.riplay.ui.screens.home.homepages

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.environment.models.bodies.NextBody
import it.fast4x.environment.requests.discoverPage
import it.fast4x.riplay.utils.contentCountryCode
import it.fast4x.environment.requests.relatedPage
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.LocalPlayerAwareWindowInsets
import it.fast4x.riplay.LocalPlayerServiceBinder
import com.yambo.music.R
import it.fast4x.riplay.enums.Countries
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PlayEventsType
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.enums.BlacklistType
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.ui.screens.home.HomePodcastsSection
import it.fast4x.riplay.ui.screens.home.HomeLiveRadioSection
import it.fast4x.riplay.extensions.listenerlevel.HomepageListenerLevelBadges
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.PullToRefreshBox
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.ui.screens.home.HomeGreetingHeader
import it.fast4x.riplay.ui.screens.home.JumpBackInSection
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.utils.isLandscape
import it.fast4x.riplay.extensions.preferences.playEventsTypeKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.selectedCountryCodeKey
import it.fast4x.riplay.extensions.preferences.showFloatingIconKey
import it.fast4x.riplay.extensions.preferences.showMoodsAndGenresKey
import it.fast4x.riplay.extensions.preferences.showNewAlbumsArtistsKey
import it.fast4x.riplay.extensions.preferences.showNewAlbumsKey
import it.fast4x.riplay.extensions.preferences.showSearchTabKey
import it.fast4x.riplay.extensions.preferences.showTipsKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.ui.screens.settings.isYtLoggedIn
import it.fast4x.riplay.extensions.preferences.showListenerLevelsKey
import it.fast4x.riplay.extensions.rewind.HomepageRewind
import it.fast4x.riplay.ui.components.themed.ChipItemColored
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.isRadio
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.components.themed.MoodItemColored
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.components.themed.TextPlaceholder
import it.fast4x.riplay.ui.components.themed.Title
import it.fast4x.riplay.ui.components.themed.Title2Actions
import it.fast4x.riplay.ui.components.themed.TitleMiniSection
import it.fast4x.riplay.ui.items.AlbumItem
import it.fast4x.riplay.ui.items.AlbumItemPlaceholder
import it.fast4x.riplay.ui.items.SongItemPlaceholder
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.ui.items.ArtistItem
import it.fast4x.riplay.ui.items.PlaylistItem
import it.fast4x.riplay.ui.items.HomeShelfCard
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.items.VideoItem
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.HomeDataCache
import it.fast4x.riplay.utils.SkeletonSwap
import it.fast4x.riplay.utils.resolveFallbackTopSongId
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.asVideoMediaItem
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.insertOrUpdateBlacklist
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.flow.first
import timber.log.Timber


@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalMaterialApi
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation", "UnusedBoxWithConstraintsScope")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomePage(
    navController: NavController,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onMoodAndGenresClick: (mood: Environment.Mood.Item) -> Unit,
    onChipClick: (chip: Environment.Chip) -> Unit,
    onSettingsClick: () -> Unit,
    onLiveRadioClick: () -> Unit = {}
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalGlobalSheetState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    var playEventType by rememberPreference(playEventsTypeKey, PlayEventsType.CasualPlayed)

    var trending by remember { mutableStateOf(HomeDataCache.trending) }
    var relatedPage by remember { mutableStateOf(HomeDataCache.relatedPage) }
    var discoverPage by remember { mutableStateOf(HomeDataCache.discoverPage) }
    var homePage by remember { mutableStateOf(HomeDataCache.homePage) }

    // A null `relatedPage` on its own doesn't mean "still loading": the request can
    // finish empty (no network, blacklisted results, Environment returning null) and
    // then the spinner would never stop. Track the attempt separately.
    var quickPicksLoading by remember { mutableStateOf(HomeDataCache.relatedPage == null) }

    var preferitesArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }

    val showNewAlbumsArtists by rememberPreference(showNewAlbumsArtistsKey, true)
    val showMoodsAndGenres by rememberPreference(showMoodsAndGenresKey, true)
    val showNewAlbums by rememberPreference(showNewAlbumsKey, true)

    val showTips by rememberPreference(showTipsKey, true)
    val showListenerLevels by rememberPreference(showListenerLevelsKey, true)
    val refreshScope = rememberCoroutineScope()

    var selectedCountryCode by rememberPreference(selectedCountryCodeKey, Countries.ZZ)

    val blacklisted = remember {
        Database.blacklisted(listOf(BlacklistType.Song.name, BlacklistType.Video.name))
    }.collectAsState(initial = null, context = Dispatchers.IO)

    //var loadedData by rememberPreference(loadedDataKey, false)

    val context = LocalContext.current

    // `homeLoading` / `discoverLoading` tell "request still running" (skeleton) apart from "answered
    // empty" (nothing to draw). Both are cleared in a finally, so a failure never leaves a skeleton.
    var homeLoading by remember { mutableStateOf(HomeDataCache.homePage == null) }
    var discoverLoading by remember { mutableStateOf(HomeDataCache.discoverPage == null) }

    // Walks the rest of the Home feed after the first page. Lives in refreshScope: it dies with the
    // tab and is resumed from HomeDataCache.homeContinuation when the tab is entered again.
    var continuationJob by remember { mutableStateOf<Job?>(null) }

    // blacklist == null means the Flow has not answered yet: treat it as "no entries"
    fun isNotBlacklisted(key: String): Boolean =
        blacklisted.value?.none { bl -> bl.path == key } ?: true

    // The disk snapshot is only valid for the same country, content country and login state
    fun homeDiskKey(): String =
        selectedCountryCode.name + "|" + (contentCountryCode() ?: "") + "|" + isYtLoggedIn()

    suspend fun saveHomeToDisk() {
        HomeDataCache.saveToDisk(context, homeDiskKey(), playEventType)
    }

    suspend fun guarded(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "HomePage $name failed")
        }
    }

    fun startHomeContinuations() {
        if (continuationJob?.isActive == true || HomeDataCache.homeContinuation == null) return

        continuationJob = refreshScope.launch(Dispatchers.IO) {
            // Seen tokens guard against a feed that points back to a page already walked
            val seen = mutableSetOf<String>()
            var token = HomeDataCache.homeContinuation
            while (true) {
                val currentToken = token ?: break
                if (!seen.add(currentToken)) break
                val next = withTimeoutOrNull(20_000L) {
                    EnvironmentExt.getHomePageContinuation(currentToken)
                }?.getOrNull() ?: break
                // A refresh or a tab change may have cancelled us while the request was in flight
                ensureActive()
                val current = HomeDataCache.homePage ?: break
                val merged = current.copy(
                    sections = (current.sections + next.sections).distinctBy { s -> s.title },
                    continuation = next.continuation
                )
                homePage = merged
                HomeDataCache.homePage = merged
                HomeDataCache.homeContinuation = next.continuation
                token = next.continuation
            }
            saveHomeToDisk()
        }
    }

    suspend fun loadHome() {
        // Already fresh in memory (tab revisit): only the rest of the feed may be missing
        if (homePage != null && !HomeDataCache.homeFromDisk) {
            homeLoading = false
            startHomeContinuations()
            return
        }

        try {
            // The first request right after a cold start (or a network change) often comes back
            // empty and left the Home blank until a manual refresh: retry up to 3 times with a
            // growing backoff (500, 1000, 2000 ms). Not worth it when old data is already painted.
            val maxRetries = if (homePage == null) 3 else 0

            // First page only: the continuations are walked afterwards, see startHomeContinuations
            suspend fun fetchFirstPage() = withTimeoutOrNull(20_000L) {
                EnvironmentExt.getHomePage(setLogin = isYtLoggedIn(), followContinuations = false)
            }?.getOrNull()

            var result = fetchFirstPage()
            var retry = 0
            while (result == null && retry < maxRetries) {
                delay(500L shl retry)
                retry++
                Timber.d("HomePage loadData home empty, retry $retry")
                result = fetchFirstPage()
            }
            // On failure keep what is on screen (cached copy or a page a newer load already filled)
            if (result != null) {
                homePage = result
                HomeDataCache.homePage = result
                HomeDataCache.homeContinuation = result.continuation
                HomeDataCache.homeFromDisk = false
                saveHomeToDisk()
            }
        } finally {
            homeLoading = false
        }

        startHomeContinuations()
    }

    suspend fun loadDiscover() {
        if (!(showNewAlbums || showNewAlbumsArtists || showMoodsAndGenres)
            || (discoverPage != null && !HomeDataCache.discoverFromDisk)
        ) {
            discoverLoading = false
            return
        }

        try {
            val result = withTimeoutOrNull(20_000L) {
                Environment.discoverPage(contentCountryCode())
            }?.getOrNull()
            // On failure keep what is on screen (cached copy)
            if (result != null) {
                discoverPage = result
                HomeDataCache.discoverPage = result
                HomeDataCache.discoverFromDisk = false
                saveHomeToDisk()
            }
        } finally {
            discoverLoading = false
        }
    }

    suspend fun loadRelated() {
        try {
            val song: Song? = when (playEventType) {
                PlayEventsType.MostPlayed -> {
                    val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
                    // More than 3 rows: a listener whose top plays are live stations must still get
                    // a song to seed from (a station is not a YouTube video, nor something to "play all").
                    Database.trending(
                        limit = 10,
                        period = thirtyDaysMs
                    ).distinctUntilChanged().first().firstOrNull { item ->
                        !item.isRadio && isNotBlacklisted(item.id)
                    }
                }

                PlayEventsType.LastPlayed, PlayEventsType.CasualPlayed -> {
                    val numSongs = if (playEventType == PlayEventsType.LastPlayed) 10 else 50
                    val songs = Database.lastPlayed(numSongs).distinctUntilChanged().first()
                    (if (playEventType == PlayEventsType.LastPlayed) songs else songs.shuffled())
                        .firstOrNull { item ->
                            // Never seed (nor "play all") from a live station: it never ends
                            !item.isRadio && isNotBlacklisted(item.id)
                        }
                }
            }
            val songId = if (song?.isLocal == true) song.mediaId else song?.id

            // Fallback: if user has no recent plays, seed Selecciones Rapidas
            // with the current top-charts track so the section is never empty.
            // Cached at process level -> chartsPage fetched at most once.
            val effectiveSongId = songId ?: resolveFallbackTopSongId(
                preferredCountry = selectedCountryCode.name,
                homePage = homePage
            )

            if (effectiveSongId != null) {
                // Only update `trending` (= "play all" anchor) when we have a real
                // user-trending song; don't pin it to a chart fallback. Set before the
                // network call so the first quick pick paints without waiting for it.
                if (song != null) {
                    trending = song
                    HomeDataCache.trending = song
                }

                // Kept from the cache while the app lives: re-seeding these on every visit made Home
                // look half empty each time the listener came back from another screen.
                // Pull to refresh clears the cache when new picks are actually wanted. A copy read
                // from disk is painted at once and replaced here by the fresh answer.
                if (relatedPage == null || HomeDataCache.relatedFromDisk) {
                    val fetched = withTimeoutOrNull(20_000L) {
                        Environment.relatedPage(NextBody(videoId = effectiveSongId))
                    }?.getOrNull()?.let {
                        it.copy(
                            songs = it.songs?.filter { item -> isNotBlacklisted(item.key) },
                            artists = it.artists?.filter { item -> isNotBlacklisted(item.key) },
                            playlists = it.playlists?.filter { item -> isNotBlacklisted(item.key) },
                            albums = it.albums?.filter { item -> isNotBlacklisted(item.key) }
                        )
                    }
                    if (fetched != null) {
                        relatedPage = fetched
                        HomeDataCache.relatedPage = fetched
                        HomeDataCache.relatedFromDisk = false
                        saveHomeToDisk()
                    }
                }
            }
        } finally {
            quickPicksLoading = false
        }
    }

    suspend fun loadData() {

        runCatching {
            // Awaited (join below): callers flip quickPicksLoading off right after this returns,
            // which used to happen before the request had even run, so the loader vanished and
            // Quick picks showed an empty gap until the related page arrived.
            // The three requests are independent, so they run in parallel and each one publishes
            // its result as soon as it arrives instead of waiting for the others.
            refreshScope.launch(Dispatchers.IO) {
                listOf(
                    async { guarded("home") { loadHome() } },
                    async { guarded("discover") { loadDiscover() } },
                    async { guarded("related") { loadRelated() } }
                ).awaitAll()
            }.join()
        }.onFailure {
            Timber.e("HomePage loadData failed")
        }
    }

    var refreshing by remember { mutableStateOf(false) }

    fun refresh() {
        if (refreshing) return

        continuationJob?.cancel()
        continuationJob = null

        // Memory and disk: the stale snapshot must not come back on the next start
        HomeDataCache.clearAll(context)

        homePage = null
        discoverPage = null
        relatedPage = null
        trending = null

        homeLoading = true
        discoverLoading = true
        quickPicksLoading = true

        refreshScope.launch(Dispatchers.IO) {
            refreshing = true
            loadData()
            quickPicksLoading = false
            delay(500)
            refreshing = false
        }
    }

    LaunchedEffect(Unit, playEventType, selectedCountryCode) {

        // The charts country and the content country (Settings) both shape the home feed
        val countryKey = selectedCountryCode.name + "|" + (contentCountryCode() ?: "")
        // A null "last" value is the first run of the process (or right after a refresh): there is
        // nothing to invalidate, and clearing here would throw away what the disk cache gives us.
        val countryChanged = HomeDataCache.lastCountryCode != null && HomeDataCache.lastCountryCode != countryKey
        val playEventChanged = HomeDataCache.lastPlayEventType != null && HomeDataCache.lastPlayEventType != playEventType

        if (countryChanged) {
            continuationJob?.cancel()
            continuationJob = null

            HomeDataCache.homePage = null
            HomeDataCache.discoverPage = null
            HomeDataCache.homeContinuation = null
            HomeDataCache.homeFromDisk = false
            HomeDataCache.discoverFromDisk = false

            homePage = null
            discoverPage = null
            homeLoading = true
            discoverLoading = true
        }
        HomeDataCache.lastCountryCode = countryKey

        if (playEventChanged) {
            HomeDataCache.relatedPage = null
            HomeDataCache.relatedFromDisk = false
            HomeDataCache.trending = null

            relatedPage = null
            trending = null
            quickPicksLoading = true
        }
        HomeDataCache.lastPlayEventType = playEventType

        // Last session's Home, read once per process: painted right away (no fade, it is not new
        // content) while the network answer below replaces it.
        HomeDataCache.loadFromDisk(context, homeDiskKey(), playEventType)

        if (HomeDataCache.homePage != null) homePage = HomeDataCache.homePage
        if (HomeDataCache.discoverPage != null) discoverPage = HomeDataCache.discoverPage
        if (HomeDataCache.relatedPage != null) relatedPage = HomeDataCache.relatedPage
        if (HomeDataCache.trending != null) trending = HomeDataCache.trending
        homeLoading = homePage == null
        discoverLoading = discoverPage == null
        quickPicksLoading = relatedPage == null

        loadData()

        if (HomeDataCache.homePage != null) homePage = HomeDataCache.homePage
        if (HomeDataCache.discoverPage != null) discoverPage = HomeDataCache.discoverPage
        if (HomeDataCache.relatedPage != null) relatedPage = HomeDataCache.relatedPage
        if (HomeDataCache.trending != null) trending = HomeDataCache.trending

        quickPicksLoading = false
    }


    LaunchedEffect(Unit) {
        Database.preferitesArtistsByName().collect { preferitesArtists = it }
    }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val scrollState = rememberScrollState()
    val quickPicksLazyGridState = rememberLazyGridState()
    val moodAngGenresLazyGridState = rememberLazyGridState()
    val chipsLazyGridState = rememberLazyGridState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val showSearchTab by rememberPreference(showSearchTabKey, false)

    val hapticFeedback = LocalHapticFeedback.current

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)


    PullToRefreshBox(
        refreshing = refreshing,
        onRefresh = { refresh() }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(
                    if (NavigationBarPosition.Right.isCurrent())
                        Dimensions.contentWidthRightBar
                    else
                        1f
                )

        ) {
            val quickPicksLazyGridItemWidthFactor =
                if (isLandscape && maxWidth * 0.475f >= 320.dp) {
                    0.475f
                } else {
                    0.9f
                }
            val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

            val moodItemWidthFactor =
                if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val itemWidth = maxWidth * moodItemWidthFactor

            Column(
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {

                if (UiType.ViMusic.isCurrent())
                    HeaderWithIcon(
                        title = if (!isYtLoggedIn()) stringResource(R.string.quick_picks)
                        else stringResource(R.string.home),
                        iconId = R.drawable.search,
                        enabled = true,
                        showIcon = !showSearchTab,
                        modifier = Modifier,
                        onClick = onSearchClick,
                        navController = navController
                    )

                HomeGreetingHeader(
                    navController = navController,
                    onSettingsClick = onSettingsClick
                )

                JumpBackInSection()

                // Listener levels (Monthly/Annual badges) hidden — Yammbo Music customization
                // if (showListenerLevels)
                //     HomepageListenerLevelBadges(navController)

                HomepageRewind(
                    showIfEndOfYear = true,
                    navController = navController,
                    playlistThumbnailSizeDp = playlistThumbnailSizeDp,
                    endPaddingValues = endPaddingValues,
                    disableScrollingText = disableScrollingText
                )

                // Nothing to show once loading is over (e.g. a brand-new account whose seed
                // found no picks): hide the whole section instead of a header over an empty gap.
                val hasQuickPicks = trending != null || !relatedPage?.songs.isNullOrEmpty()
                if (showTips && (hasQuickPicks || quickPicksLoading)) {
                    Title2Actions(
                        title = stringResource(R.string.quick_picks),
                        onClick1 = {
                            menuState.display {
                                Menu {
                                    MenuEntry(
                                        icon = R.drawable.chevron_up,
                                        text = stringResource(R.string.by_most_played_song),
                                        onClick = {
                                            playEventType = PlayEventsType.MostPlayed
                                            menuState.hide()
                                        }
                                    )
                                    MenuEntry(
                                        icon = R.drawable.chevron_down,
                                        text = stringResource(R.string.by_last_played_song),
                                        onClick = {
                                            playEventType = PlayEventsType.LastPlayed
                                            menuState.hide()
                                        }
                                    )
                                    MenuEntry(
                                        icon = R.drawable.random,
                                        text = stringResource(R.string.by_casual_played_song),
                                        onClick = {
                                            playEventType = PlayEventsType.CasualPlayed
                                            menuState.hide()
                                        }
                                    )
                                }
                            }
                        },
                        icon2 = R.drawable.play_now,
                        onClick2 = {
                            //trending?.let { fastPlay(it.asMediaItem, binder, relatedInit?.songs?.map { it.asMediaItem }) }
                            binder?.stopRadio()
                            trending?.let { binder?.player?.forcePlay(it.asMediaItem) }
                            binder?.player?.addMediaItems(relatedPage?.songs?.map { it.asMediaItem }
                                ?: emptyList())
                        }

                        //modifier = Modifier.fillMaxWidth(0.7f)
                    )

                    BasicText(
                        text = when (playEventType) {
                            PlayEventsType.MostPlayed -> stringResource(R.string.by_most_played_song)
                            PlayEventsType.LastPlayed -> stringResource(R.string.by_last_played_song)
                            PlayEventsType.CasualPlayed -> stringResource(R.string.by_casual_played_song)
                        },
                        style = typography().xxs.secondary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )




                    // While the picks are still loading the final 3 rows are already reserved, so the
                    // sections below do not jump down when they arrive
                    val quickPicksRows = if (relatedPage != null || quickPicksLoading) 3 else 1
                    val quickPicksPending = relatedPage == null && quickPicksLoading

                    SkeletonSwap(
                        loading = quickPicksPending && trending == null,
                        skeleton = {
                            QuickPicksSkeleton(
                                rows = 3,
                                thumbnailSizeDp = songThumbnailSizeDp,
                                rowWidth = itemInHorizontalGridWidth
                            )
                        }
                    ) {
                        Box {
                            LazyHorizontalGrid (
                                state = quickPicksLazyGridState,
                                rows = GridCells.Fixed(quickPicksRows),
                                flingBehavior = ScrollableDefaults.flingBehavior(),
                                contentPadding = endPaddingValues,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimensions.itemsVerticalPadding * quickPicksRows * 9)
                                //.height((songThumbnailSizeDp + Dimensions.itemsVerticalPadding * 2) * 4)
                            ) {
                                trending?.let { song ->
                                    item(key = "trending#${song.id}", contentType = "song") {
                                        //val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }
                                        //var forceRecompose by remember { mutableStateOf(false) }
                                        SongItem(
                                            song = song,
                                            thumbnailSizePx = songThumbnailSizePx,
                                            thumbnailSizeDp = songThumbnailSizeDp,
                                            trailingContent = {
                                                Image(
                                                    painter = painterResource(R.drawable.star),
                                                    contentDescription = null,
                                                    colorFilter = ColorFilter.tint(colorPalette().accent),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onLongClick = {
                                                        menuState.display {
                                                            NonQueuedMediaItemMenu(
                                                                navController = navController,
                                                                onDismiss = {
                                                                    menuState.hide()
                                                                    //forceRecompose = true
                                                                },
                                                                mediaItem = song.asMediaItem,
                                                                onRemoveFromQuickPicks = {
                                                                    Database.asyncTransaction {
                                                                        clearEventsFor(song.id)
                                                                    }
                                                                },
                                                                onInfo = {
                                                                    navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.id}")
                                                                },
                                                                disableScrollingText = disableScrollingText,
                                                                onBlacklist = {
                                                                    insertOrUpdateBlacklist(song)
                                                                },
                                                            )
                                                        }
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    },
                                                    onClick = {

                                                        val mediaItem = if (song.isAudioOnly == 1)
                                                            song.asMediaItem
                                                        else
                                                            song.asVideoMediaItem

                                                        binder?.stopRadio()
                                                        binder?.player?.forcePlay(mediaItem)
                                                        //binder?.player?.playOnline(mediaItem)
                                                        //fastPlay(mediaItem, binder)
                                                        binder?.setupRadio(
                                                            NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                                        )
                                                    }
                                                )
                                                .animateItem(
                                                    fadeInSpec = null,
                                                    fadeOutSpec = null
                                                )
                                                .width(itemInHorizontalGridWidth),

                                            )
                                    }
                                }

                                items(
                                    items = relatedPage?.songs?.distinctBy { it.key }
                                        ?.dropLast(if (trending == null) 0 else 1)
                                        ?: emptyList(),
                                    key = Environment.SongItem::key,
                                    contentType = { "song" }
                                ) { song ->
                                    Timber.d("HomePage RELATED Environment.SongItem duration ${song.durationText}")
                                    SongItem(
                                        song = song,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = null,
                                                fadeOutSpec = null
                                            )
                                            .width(itemInHorizontalGridWidth)
                                            .combinedClickable(
                                                onLongClick = {
                                                    menuState.display {
                                                        NonQueuedMediaItemMenu(
                                                            navController = navController,
                                                            onDismiss = {
                                                                menuState.hide()
                                                                //forceRecompose = true
                                                            },
                                                            mediaItem = song.asMediaItem,
                                                            onInfo = {
                                                                navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.key}")
                                                            },
                                                            disableScrollingText = disableScrollingText,
                                                            onBlacklist = {
                                                                insertOrUpdateBlacklist(song.asSong)
                                                            },
                                                        )
                                                    }
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                },
                                                onClick = {
                                                    Timber.d("HomePage Clicked on song")
                                                    val mediaItem = if (song.isAudioOnly)
                                                        song.asMediaItem
                                                    else
                                                        song.asVideoMediaItem

                                                    binder?.stopRadio()
                                                    binder?.player?.forcePlay(mediaItem)
                                                    //fastPlay(mediaItem, binder)
                                                    binder?.setupRadio(
                                                        NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                                    )
                                                }
                                            ),

                                        )
                                }

                            }

                            // The trending song paints first: the rows still to come are drawn over the empty
                            // part of the grid until the related page arrives
                            if (quickPicksPending) {
                                Box(modifier = Modifier.padding(top = Dimensions.itemsVerticalPadding * 9)) {
                                    QuickPicksSkeleton(
                                        rows = 2,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        rowWidth = itemInHorizontalGridWidth
                                    )
                                }
                            }
                        }
                    }

                }

                SkeletonSwap(
                    loading = discoverPage == null && discoverLoading && showNewAlbums,
                    skeleton = {
                        Title(title = stringResource(R.string.new_albums))
                        HomeShelfSkeleton(
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            contentPadding = endPaddingValues,
                            withHeader = false
                        )
                    }
                ) {
                    discoverPage?.let { page ->

                        // Computed once per page / favourite artists change, not on every recomposition
                        val preferredNames = remember(preferitesArtists) {
                            preferitesArtists.map { it.name }.toSet()
                        }
                        val newReleaseAlbumsFiltered = remember(page, preferredNames) {
                            page.newReleaseAlbums.filter { album ->
                                val apiAuthorsNames = album.authors?.map { it.name } ?: emptyList()

                                apiAuthorsNames.any { apiName ->

                                    preferredNames.any { dbName ->
                                        apiName?.contains(dbName.toString(), ignoreCase = true) == true
                                    }
                                }
                            }
                        }
                        val newReleaseAlbumsDistinct = remember(page) {
                            page.newReleaseAlbums.distinctBy { it.key }
                        }
                        val newReleaseAlbumsFilteredDistinct = remember(newReleaseAlbumsFiltered) {
                            newReleaseAlbumsFiltered.distinctBy { it.key }
                        }

                        if (showNewAlbumsArtists)
                            if (newReleaseAlbumsFiltered.isNotEmpty() && preferitesArtists.isNotEmpty()) {

                                BasicText(
                                    text = stringResource(R.string.new_albums_of_your_artists),
                                    style = typography().l.semiBold,
                                    modifier = sectionTextModifier
                                )

                                LazyRow(contentPadding = endPaddingValues) {
                                    items(
                                        items = newReleaseAlbumsFilteredDistinct,
                                        key = { it.key },
                                        contentType = { "album" }) {
                                        AlbumItem(
                                            album = it,
                                            thumbnailSizePx = albumThumbnailSizePx,
                                            thumbnailSizeDp = albumThumbnailSizeDp,
                                            alternative = true,
                                            modifier = Modifier.clickable(onClick = {
                                                onAlbumClick(it.key)
                                            }),
                                            disableScrollingText = disableScrollingText,
                                            shelfCard = true
                                        )
                                    }
                                }

                            }

                        if (showNewAlbums) {
                            Title(
                                title = stringResource(R.string.new_albums),
                                onClick = { navController.navigate(NavRoutes.newAlbums.name) },
                            )

                            LazyRow(contentPadding = endPaddingValues) {
                                items(
                                    items = newReleaseAlbumsDistinct,
                                    key = { it.key },
                                    contentType = { "album" }) {
                                    AlbumItem(
                                        album = it,
                                        thumbnailSizePx = albumThumbnailSizePx,
                                        thumbnailSizeDp = albumThumbnailSizeDp,
                                        alternative = true,
                                        modifier = Modifier.clickable(onClick = {
                                            onAlbumClick(it.key)
                                        }),
                                        disableScrollingText = disableScrollingText,
                                        shelfCard = true
                                    )
                                }
                            }
                        }

                    }
                }

                // Outside the discover block: it has its own request and skeleton
                HomePodcastsSection(
                    navController = navController,
                    thumbnailSizeDp = playlistThumbnailSizeDp,
                    thumbnailSizePx = playlistThumbnailSizePx,
                    disableScrollingText = disableScrollingText,
                    contentPadding = endPaddingValues
                )

                HomeLiveRadioSection(
                    thumbnailSizeDp = albumThumbnailSizeDp,
                    onOpenAll = onLiveRadioClick
                )

                SkeletonSwap(
                    loading = homePage == null && homeLoading,
                    skeleton = {
                        repeat(2) {
                            HomeShelfSkeleton(
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                contentPadding = endPaddingValues,
                                withHeader = true
                            )
                        }
                    }
                ) {
                    homePage?.let { page ->

                        page.sections.forEach {
                            // A shelf whose first entry failed to parse is still a shelf; judge it by what survived
                            if (it.items.filterNotNull().none { item -> item.key.isNotEmpty() }) return@forEach

                            TitleMiniSection(
                                it.label ?: "", modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 14.dp, bottom = 4.dp)
                            )

                            BasicText(
                                text = it.title,
                                style = typography().l.semiBold.color(colorPalette().text),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(vertical = 4.dp)
                            )
                            // Filtered once per page/blacklist change, not on every recomposition
                            val shelfItems = remember(it.items, blacklisted.value) {
                                it.items.filterNotNull().filter { item -> isNotBlacklisted(item.key) }
                            }
                            LazyRow(contentPadding = endPaddingValues) {
                                // Index in the key: a shelf can repeat a key (or have an empty one)
                                itemsIndexed(
                                    items = shelfItems,
                                    key = { index, item -> "${item.key}#$index" },
                                    contentType = { _, item -> item::class }
                                ) { _, item ->
                                    when (item) {
                                        is Environment.SongItem -> {
                                            Timber.d("Environment homePage SongItem: ${item.info?.name}")
                                            HomeShelfCard(
                                                thumbnailUrl = item.thumbnail?.url,
                                                title = item.info?.name,
                                                subtitle = item.authors?.joinToString(", ") { a -> a.name ?: "" },
                                                thumbnailSizePx = albumThumbnailSizePx,
                                                thumbnailSizeDp = albumThumbnailSizeDp,
                                                disableScrollingText = disableScrollingText,
                                                modifier = Modifier.clickable(onClick = {
                                                    binder?.stopRadio()
                                                    binder?.player?.forcePlay(item.asMediaItem)
                                                    binder?.setupRadio(
                                                        item.info?.endpoint
                                                            ?: NavigationEndpoint.Endpoint.Watch(videoId = item.key)
                                                    )
                                                    //fastPlay(item.asMediaItem, binder)
                                                })
                                            )
                                        }

                                        is Environment.AlbumItem -> {
                                            Timber.d("Environment homePage AlbumItem: ${item.info?.name}")
                                            AlbumItem(
                                                album = item,
                                                alternative = true,
                                                thumbnailSizePx = albumThumbnailSizePx,
                                                thumbnailSizeDp = albumThumbnailSizeDp,
                                                disableScrollingText = disableScrollingText,
                                                shelfCard = true,
                                                modifier = Modifier.clickable(onClick = {
                                                    navController.navigate("${NavRoutes.album.name}/${item.key}")
                                                })

                                            )
                                        }

                                        is Environment.ArtistItem -> {
                                            Timber.d("Environment homePage ArtistItem: ${item.info?.name}")
                                            ArtistItem(
                                                artist = item,
                                                thumbnailSizePx = artistThumbnailSizePx,
                                                thumbnailSizeDp = artistThumbnailSizeDp,
                                                alternative = true,
                                                disableScrollingText = disableScrollingText,
                                                modifier = Modifier.clickable(onClick = {
                                                    navController.navigate("${NavRoutes.artist.name}/${item.key}")
                                                })
                                            )
                                        }

                                        is Environment.PlaylistItem -> {
                                            Timber.d("Environment homePage PlaylistItem: ${item.info?.name}")
                                            PlaylistItem(
                                                playlist = item,
                                                alternative = true,
                                                thumbnailSizePx = playlistThumbnailSizePx,
                                                thumbnailSizeDp = playlistThumbnailSizeDp,
                                                disableScrollingText = disableScrollingText,
                                                shelfCard = true,
                                                modifier = Modifier.clickable(onClick = {
                                                    // A podcast show is not a playlist: browsing it as one gives an empty list
                                                                                    // A mix (radio-style auto playlist) has no browsable page: play it instead of opening one
                                                    if (item.key.startsWith("RD")) {
                                                        binder?.stopRadio()
                                                        binder?.playRadio(NavigationEndpoint.Endpoint.Watch(playlistId = item.key))
                                                    } else if (item.key.startsWith("MPSP"))
                                                                                        navController.navigate("${NavRoutes.podcast.name}/${item.key}")
                                                                                    else
                                                                                        navController.navigate("${NavRoutes.playlist.name}/${item.key}")
                                                })
                                            )
                                        }

                                        is Environment.VideoItem -> {
                                            Timber.d("Environment homePage VideoItem: ${item.info?.name}")
                                            HomeShelfCard(
                                                thumbnailUrl = item.thumbnail?.url,
                                                title = item.info?.name,
                                                subtitle = item.authors?.joinToString(", ") { a -> a.name ?: "" },
                                                thumbnailSizePx = albumThumbnailSizePx,
                                                thumbnailSizeDp = albumThumbnailSizeDp,
                                                disableScrollingText = disableScrollingText,
                                                modifier = Modifier.clickable(onClick = {
                                                    binder?.stopRadio()
    //                                                if (isVideoEnabled())
    //                                                    binder?.player?.playOnline(item.asMediaItem)
    //                                                else
                                                    binder?.player?.forcePlay(item.asMediaItem)
                                                    binder?.setupRadio(
                                                        item.info?.endpoint
                                                            ?: NavigationEndpoint.Endpoint.Watch(videoId = item.key)
                                                    )
                                                    //fastPlay(item.asMediaItem, binder)
                                                })
                                            )
                                        }

                                        null -> {}
                                    }

                                }
                            }
                        }

                        if (showMoodsAndGenres) {
                            // Sorted once per data change instead of on every recomposition
                            val sortedChips = remember(page.chips) {
                                page.chips?.sortedBy { it.title } ?: emptyList()
                            }
                            if (page.chips?.isNotEmpty() == true) {
                                Title(
                                    title = stringResource(R.string.mood),
                                    //onClick = { navController.navigate(NavRoutes.moodsPage.name) },
                                    //modifier = Modifier.fillMaxWidth(0.7f)
                                )

                                LazyHorizontalGrid(
                                    state = chipsLazyGridState,
                                    rows = GridCells.Fixed(4),
                                    flingBehavior = ScrollableDefaults.flingBehavior(),
                                    //flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                    contentPadding = endPaddingValues,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        //.height((thumbnailSizeDp + Dimensions.itemsVerticalPadding * 8) * 8)
                                        .height(Dimensions.itemsVerticalPadding * 4 * 8)
                                ) {
                                    items(
                                        items = sortedChips,
                                        key = { it.endpoint?.params.toString() },
                                        contentType = { "chip" }
                                    ) {
                                        ChipItemColored(
                                            chip = it,
                                            onClick = { it.endpoint?.browseId?.let { _ -> onChipClick(it) } },
                                            modifier = Modifier
                                                //.width(itemWidth)
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }




                            discoverPage?.let { page ->

                                if (page.moods.isNotEmpty()) {

                                    val sortedMoods = remember(page.moods) { page.moods.sortedBy { it.title } }

                                    Title(
                                        title = stringResource(R.string.genres),
                                        onClick = { navController.navigate(NavRoutes.moodsPage.name) },
                                        //modifier = Modifier.fillMaxWidth(0.7f)
                                    )

                                    LazyHorizontalGrid(
                                        state = moodAngGenresLazyGridState,
                                        rows = GridCells.Fixed(4),
                                        flingBehavior = ScrollableDefaults.flingBehavior(),
                                        //flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                        contentPadding = endPaddingValues,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            //.height((thumbnailSizeDp + Dimensions.itemsVerticalPadding * 8) * 8)
                                            .height(Dimensions.itemsVerticalPadding * 4 * 8)
                                    ) {
                                        items(
                                            items = sortedMoods,
                                            key = { it.endpoint.params ?: it.title },
                                            contentType = { "mood" }
                                        ) {
                                            MoodItemColored(
                                                mood = it,
                                                onClick = {
                                                    it.endpoint.browseId?.let { _ ->
                                                        onMoodAndGenresClick(
                                                            it
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    //.width(itemWidth)
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }

                            }
                        }
                    }
                }

                /****** END HOMEPAGE CONTENT *******/

                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
            }





            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if (UiType.ViMusic.isCurrent() && showFloatingIcon)
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )

        }

    }
}


/** [rows] song rows with the same size as the real Quick picks grid rows (itemsVerticalPadding * 9). */
@Composable
private fun QuickPicksSkeleton(
    rows: Int,
    thumbnailSizeDp: Dp,
    rowWidth: Dp
) {
    ShimmerHost {
        repeat(rows) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .width(rowWidth)
                    .height(Dimensions.itemsVerticalPadding * 9)
            ) {
                SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
            }
        }
    }
}

/** A shelf-shaped placeholder: optional label + title lines and one row of cards (not scrollable). */
@Composable
private fun HomeShelfSkeleton(
    thumbnailSizeDp: Dp,
    contentPadding: PaddingValues,
    withHeader: Boolean
) {
    ShimmerHost {
        if (withHeader) {
            TextPlaceholder(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .width(72.dp)
            )
            TextPlaceholder(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(180.dp)
            )
        }

        LazyRow(
            userScrollEnabled = false,
            contentPadding = contentPadding
        ) {
            items(6) {
                AlbumItemPlaceholder(
                    thumbnailSizeDp = thumbnailSizeDp,
                    alternative = true
                )
            }
        }
    }
}
