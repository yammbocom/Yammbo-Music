package it.fast4x.riplay.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.utils.parseCookieString
import it.fast4x.riplay.LocalAudioTagger
import it.fast4x.riplay.enums.LastFmScrobbleType
import it.fast4x.riplay.enums.MusicIdentifierProvider
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.enums.ValidationType
import it.fast4x.riplay.extensions.discord.DiscordLoginAndGetToken
import it.fast4x.riplay.extensions.encryptedpreferences.rememberEncryptedPreference
import it.fast4x.riplay.extensions.lastfm.LastFmAuthScreen
import it.fast4x.riplay.extensions.listenbrainz.ListenBrainzClient
import it.fast4x.riplay.extensions.listenbrainz.isListenBrainzConnected
import it.fast4x.riplay.extensions.listenbrainz.listenBrainzEnabledKey
import it.fast4x.riplay.extensions.listenbrainz.listenBrainzUserName
import it.fast4x.riplay.extensions.preferences.discordAccountNameKey
import it.fast4x.riplay.extensions.preferences.discordPersonalAccessTokenKey
import it.fast4x.riplay.extensions.preferences.enableMusicIdentifierKey
import it.fast4x.riplay.extensions.preferences.enableYouTubeLoginKey
import it.fast4x.riplay.extensions.preferences.enableYouTubeSyncKey
import it.fast4x.riplay.extensions.preferences.isDiscordPresenceEnabledKey
import it.fast4x.riplay.extensions.preferences.isEnabledLastfmKey
import it.fast4x.riplay.extensions.preferences.lastfmScrobbleTypeKey
import it.fast4x.riplay.extensions.preferences.lastfmSessionTokenKey
import it.fast4x.riplay.extensions.preferences.musicIdentifierApiKey
import it.fast4x.riplay.extensions.preferences.musicIdentifierProviderKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import it.fast4x.riplay.extensions.preferences.ytAccountChannelHandleKey
import it.fast4x.riplay.extensions.preferences.ytAccountEmailKey
import it.fast4x.riplay.extensions.preferences.ytAccountNameKey
import it.fast4x.riplay.extensions.preferences.ytAccountThumbnailKey
import it.fast4x.riplay.extensions.preferences.ytCookieKey
import it.fast4x.riplay.extensions.youtubelogin.YouTubeLogin
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.service.PlayerService
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.themed.AccountInfoDialog
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.YtSyncManager
import it.fast4x.riplay.utils.YtSyncState
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.isAtLeastAndroid81
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.utils.ytSyncHistoryKey
import it.fast4x.riplay.utils.ytSyncLibraryKey
import it.fast4x.riplay.utils.ytSyncLikesKey
import it.fast4x.riplay.utils.ytSyncPlaylistsKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

// Plain (not secret) copy of the Discord avatar URL, so the card can show it.
private const val discordAvatarUrlKey = "accounts_discord_avatar"

@UnstableApi
@DelicateCoroutinesApi
@ExperimentalMaterial3Api
@SuppressLint("BatteryLife")
@ExperimentalAnimationApi
@Composable
fun AccountsSettings(
    authManager: YammboAuthManager? = null,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        /****** YAMMBO MUSIC ACCOUNT ******/
        if (authManager != null) {
            val yammboLoggedIn = authManager.isLoggedIn()
            val yammboWho = authManager.getUserName().ifBlank { authManager.getUserEmail() }
            AccountsCard(
                title = "Yammbo Music",
                monogram = "Y",
                connected = yammboLoggedIn,
                statusText = when {
                    yammboLoggedIn && yammboWho.isNotBlank() ->
                        stringResource(R.string.accounts_status_connected_as, yammboWho)
                    yammboLoggedIn -> stringResource(R.string.accounts_status_connected)
                    else -> stringResource(R.string.accounts_status_not_connected)
                },
                avatarUrl = authManager.getUserAvatar().takeIf { yammboLoggedIn },
            ) {
                if (yammboLoggedIn) {
                    Spacer(modifier = Modifier.height(14.dp))
                    BasicText(
                        text = stringResource(R.string.accounts_yammbo_sign_out_info),
                        style = typography().xxs.copy(color = colorPalette().textSecondary),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AccountsSecondaryButton(
                        text = stringResource(R.string.accounts_action_sign_out),
                        onClick = {
                            context.stopService(Intent(context, PlayerService::class.java))
                            val token = authManager.getAccessToken()
                            if (token != null) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    YammboApiService.logout(token)
                                }
                            }
                            authManager.logout()
                            onLogout?.invoke()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        /****** YAMMBO MUSIC ACCOUNT ******/

        /****** YOUTUBE MUSIC ******/
        var isYouTubeLoginEnabled by rememberPreference(enableYouTubeLoginKey, false)
        var isYouTubeSyncEnabled by rememberPreference(enableYouTubeSyncKey, false)
        var syncLikes by rememberPreference(ytSyncLikesKey, true)
        var syncPlaylists by rememberPreference(ytSyncPlaylistsKey, true)
        var syncLibrary by rememberPreference(ytSyncLibraryKey, true)
        var syncHistory by rememberPreference(ytSyncHistoryKey, true)
        var cookie by rememberPreference(ytCookieKey, "")
        var accountName by rememberPreference(ytAccountNameKey, "")
        var accountEmail by rememberPreference(ytAccountEmailKey, "")
        var accountChannelHandle by rememberPreference(ytAccountChannelHandleKey, "")
        var accountThumbnail by rememberPreference(ytAccountThumbnailKey, "")
        var loginYouTube by rememberSaveable { mutableStateOf(false) }
        var showUserInfoDialog by rememberSaveable { mutableStateOf(false) }

        val hasYtCookie = remember(cookie) {
            val parsed = parseCookieString(cookie)
            "SID" in parsed || "LOGIN_INFO" in parsed
        }
        val ytConnected = isYouTubeLoginEnabled && hasYtCookie

        var infoLoading by remember { mutableStateOf(false) }
        var infoError by remember { mutableStateOf(false) }
        // A failed lookup is shown as an error with a retry, never as an empty account.
        val loadAccountInfo: () -> Unit = {
            if (!infoLoading) {
                infoLoading = true
                infoError = false
                scope.launch {
                    Environment.accountInfo().onSuccess { info ->
                        if (info == null) {
                            infoError = true
                        } else {
                            accountName = info.name.orEmpty()
                            accountEmail = info.email.orEmpty()
                            accountChannelHandle = info.channelHandle.orEmpty()
                            accountThumbnail = info.thumbnailUrl.orEmpty()
                        }
                    }.onFailure {
                        infoError = true
                        Timber.e("AccountsSettings: account info failed: ${it.message}")
                    }
                    infoLoading = false
                }
            }
        }
        LaunchedEffect(ytConnected) {
            if (ytConnected && (accountName.isBlank() || accountThumbnail.isBlank())) loadAccountInfo()
        }

        val syncState by YtSyncManager.state.collectAsState()
        var lastSyncAt by remember { mutableStateOf(YtSyncState.lastSyncAt()) }
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(syncState.running) {
            lastSyncAt = YtSyncState.lastSyncAt()
            now = System.currentTimeMillis()
        }
        // Keeps the "hace X min" line fresh while the screen stays open.
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000L)
                now = System.currentTimeMillis()
            }
        }

        AccountsCard(
            title = "YouTube Music",
            icon = R.drawable.musical_notes,
            connected = ytConnected,
            statusText = when {
                ytConnected -> accountName.ifBlank { accountEmail }.let {
                    if (it.isBlank()) stringResource(R.string.accounts_status_connected)
                    else stringResource(R.string.accounts_status_connected_as, it)
                }
                else -> stringResource(R.string.accounts_status_not_connected)
            },
            avatarUrl = accountThumbnail.takeIf { ytConnected },
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            if (!ytConnected) {
                AccountsPrimaryButton(
                    text = stringResource(R.string.accounts_action_connect),
                    onClick = {
                        // The login sheet only stores the cookie; the login switch is ours to set.
                        isYouTubeLoginEnabled = true
                        loginYouTube = true
                    }
                )
            } else {
                AccountsSwitchRow(
                    title = stringResource(R.string.accounts_yt_sync_master_title),
                    subtitle = stringResource(R.string.accounts_yt_sync_master_subtitle),
                    checked = isYouTubeSyncEnabled,
                    onCheckedChange = { isYouTubeSyncEnabled = it },
                )

                if (isYouTubeSyncEnabled) {
                    AccountsDivider()
                    AccountsSwitchRow(
                        title = stringResource(R.string.accounts_yt_likes_title),
                        subtitle = stringResource(R.string.accounts_yt_likes_subtitle),
                        checked = syncLikes,
                        onCheckedChange = { syncLikes = it },
                    )
                    AccountsSwitchRow(
                        title = stringResource(R.string.accounts_yt_playlists_title),
                        subtitle = stringResource(R.string.accounts_yt_playlists_subtitle),
                        checked = syncPlaylists,
                        onCheckedChange = { syncPlaylists = it },
                    )
                    AccountsSwitchRow(
                        title = stringResource(R.string.accounts_yt_library_title),
                        subtitle = stringResource(R.string.accounts_yt_library_subtitle),
                        checked = syncLibrary,
                        onCheckedChange = { syncLibrary = it },
                    )
                    AccountsSwitchRow(
                        title = stringResource(R.string.accounts_yt_history_title),
                        subtitle = stringResource(R.string.accounts_yt_history_subtitle),
                        checked = syncHistory,
                        onCheckedChange = { syncHistory = it },
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    val syncLine = when {
                        syncState.running -> when (syncState.step) {
                            YtSyncManager.Step.LIBRARY -> stringResource(R.string.accounts_sync_step_library)
                            else -> stringResource(R.string.accounts_sync_step_lists)
                        }
                        syncState.failed -> stringResource(R.string.accounts_sync_failed)
                        lastSyncAt > 0L -> stringResource(
                            R.string.accounts_sync_last,
                            accountsSyncAgeText(lastSyncAt, now)
                        )
                        else -> stringResource(R.string.accounts_sync_never)
                    }
                    BasicText(
                        text = syncLine,
                        style = typography().xxs.copy(
                            color = if (syncState.failed && !syncState.running) colorPalette().text
                            else colorPalette().textSecondary
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AccountsPrimaryButton(
                        text = if (syncState.failed && !syncState.running)
                            stringResource(R.string.accounts_action_retry)
                        else stringResource(R.string.accounts_sync_now),
                        loading = syncState.running,
                        onClick = {
                            // Not tied to this screen: leaving it must not abort a running sync.
                            GlobalScope.launch(Dispatchers.IO) { YtSyncManager.syncNow() }
                        }
                    )
                }

                AccountsDivider(modifier = Modifier.padding(top = 8.dp))
                AccountsRow(
                    title = stringResource(R.string.accounts_action_account_info),
                    subtitle = when {
                        infoLoading -> stringResource(R.string.accounts_sync_running)
                        infoError -> stringResource(R.string.accounts_yt_info_error)
                        else -> accountEmail.ifBlank { null }
                    },
                    onClick = {
                        if (accountName.isBlank()) loadAccountInfo() else showUserInfoDialog = true
                    },
                    trailing = {
                        if (infoError && !infoLoading) {
                            BasicText(
                                text = stringResource(R.string.accounts_action_retry),
                                style = typography().xxs.semiBold.copy(color = colorPalette().text),
                            )
                        }
                    },
                )
                AccountsRow(
                    title = stringResource(R.string.accounts_action_switch_account),
                    onClick = {
                        // The likes bookkeeping belongs to the current account; start clean
                        // (runLikeSync also checks the owner, this covers a stale id).
                        YtSyncState.reset()
                        lastSyncAt = 0L
                        loginYouTube = true
                    },
                )

                Spacer(modifier = Modifier.height(6.dp))
                AccountsSecondaryButton(
                    text = stringResource(R.string.accounts_action_disconnect),
                    onClick = {
                        cookie = ""
                        accountName = ""
                        accountChannelHandle = ""
                        accountEmail = ""
                        accountThumbnail = ""
                        // Disconnecting must not leave sync armed for the next account.
                        isYouTubeSyncEnabled = false
                        isYouTubeLoginEnabled = false
                        loginYouTube = false
                        YtSyncState.reset()
                        lastSyncAt = 0L
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        WebStorage.getInstance().deleteAllData()
                    }
                )
            }
        }

        AccountsSheet(
            show = loginYouTube,
            onDismiss = { loginYouTube = false },
        ) {
            // The login screen stores the cookie itself and restarts the app: no callback work needed.
            YouTubeLogin(onLogin = {})
        }

        if (showUserInfoDialog) {
            AccountInfoDialog(
                accountName = accountName,
                accountEmail = accountEmail,
                accountChannelHandle = accountChannelHandle,
                onDismiss = { showUserInfoDialog = false }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        /****** YOUTUBE MUSIC ******/

        /****** DISCORD ******/
        var isDiscordPresenceEnabled by rememberPreference(isDiscordPresenceEnabledKey, false)
        var loginDiscord by rememberSaveable { mutableStateOf(false) }
        var discordPersonalAccessToken by rememberEncryptedPreference(
            key = discordPersonalAccessTokenKey,
            defaultValue = ""
        )
        var discordAccountName by rememberEncryptedPreference(
            key = discordAccountNameKey,
            defaultValue = ""
        )
        var discordAvatarUrl by rememberPreference(discordAvatarUrlKey, "")
        val discordConnected = discordPersonalAccessToken.isNotEmpty()

        AccountsCard(
            title = stringResource(R.string.social_discord),
            icon = R.drawable.logo_discord,
            connected = discordConnected,
            statusText = when {
                discordConnected && discordAccountName.isNotBlank() ->
                    stringResource(R.string.accounts_status_connected_as, discordAccountName)
                discordConnected -> stringResource(R.string.accounts_status_connected)
                else -> stringResource(R.string.accounts_status_not_connected)
            },
            avatarUrl = discordAvatarUrl.takeIf { discordConnected },
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            if (discordConnected) {
                AccountsSwitchRow(
                    title = stringResource(R.string.accounts_discord_presence_title),
                    subtitle = stringResource(R.string.accounts_discord_presence_subtitle),
                    checked = isDiscordPresenceEnabled,
                    onCheckedChange = { isDiscordPresenceEnabled = it },
                    enabled = isAtLeastAndroid81,
                )
                Spacer(modifier = Modifier.height(6.dp))
                AccountsSecondaryButton(
                    text = stringResource(R.string.accounts_action_disconnect),
                    onClick = {
                        discordPersonalAccessToken = ""
                        discordAccountName = ""
                        discordAvatarUrl = ""
                        isDiscordPresenceEnabled = false
                    }
                )
            } else {
                AccountsPrimaryButton(
                    text = stringResource(R.string.accounts_action_connect),
                    enabled = isAtLeastAndroid81,
                    onClick = { loginDiscord = true }
                )
            }
        }

        AccountsSheet(
            show = loginDiscord,
            onDismiss = { loginDiscord = false },
        ) {
            DiscordLoginAndGetToken(
                navController = rememberNavController(),
                onGetToken = { token, username, avatar ->
                    loginDiscord = false
                    discordPersonalAccessToken = token
                    discordAccountName = username
                    discordAvatarUrl = avatar
                    isDiscordPresenceEnabled = true
                    SmartMessage(
                        context.resources.getString(R.string.discord_connected_to_discord_account) + " $username",
                        type = PopupType.Info,
                        context = context
                    )
                }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        /****** DISCORD ******/

        /****** LASTFM ******/
        var isEnabledLastfm by rememberPreference(isEnabledLastfmKey, false)
        var lastFmSessionToken by rememberPreference(lastfmSessionTokenKey, "")
        var loginLastfm by rememberSaveable { mutableStateOf(false) }
        var lastfmScrobbleType by rememberPreference(
            lastfmScrobbleTypeKey,
            LastFmScrobbleType.Simple
        )
        val lastfmConnected = lastFmSessionToken.isNotEmpty()

        AccountsCard(
            title = stringResource(R.string.title_lastfm),
            icon = R.drawable.logo_lastfm,
            connected = lastfmConnected,
            statusText = if (lastfmConnected) stringResource(R.string.accounts_status_connected)
            else stringResource(R.string.accounts_status_not_connected),
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            if (lastfmConnected) {
                AccountsSwitchRow(
                    title = stringResource(R.string.accounts_lastfm_scrobble_title),
                    subtitle = stringResource(R.string.accounts_lastfm_scrobble_subtitle),
                    checked = isEnabledLastfm,
                    onCheckedChange = { isEnabledLastfm = it },
                )
                if (isEnabledLastfm) {
                    EnumValueSelectorSettingsEntry(
                        title = stringResource(R.string.lastfm_scrobble_type),
                        titleSecondary = "",
                        selectedValue = lastfmScrobbleType,
                        onValueSelected = { lastfmScrobbleType = it },
                        valueText = { it.textName },
                        offline = false
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                AccountsSecondaryButton(
                    text = stringResource(R.string.accounts_action_disconnect),
                    onClick = {
                        lastFmSessionToken = ""
                        isEnabledLastfm = false
                    }
                )
            } else {
                AccountsPrimaryButton(
                    text = stringResource(R.string.accounts_action_connect),
                    onClick = {
                        isEnabledLastfm = true
                        loginLastfm = true
                    }
                )
            }
        }

        AccountsSheet(
            show = loginLastfm,
            onDismiss = { loginLastfm = false },
        ) {
            LastFmAuthScreen(
                navController = rememberNavController(),
                onAuthSuccess = {
                    loginLastfm = false
                    lastFmSessionToken =
                        context.preferences.getString(lastfmSessionTokenKey, "") ?: ""
                    Timber.d("LastFmAuthScreen: Authentication complete")
                }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        /****** LASTFM ******/

        /****** LISTENBRAINZ ******/
        // State is read from storage (token is in the encrypted store) and refreshed on connect/disconnect.
        var lbConnected by remember { mutableStateOf(isListenBrainzConnected()) }
        var lbUser by remember { mutableStateOf(listenBrainzUserName()) }
        var lbScrobbleEnabled by rememberPreference(listenBrainzEnabledKey, true)
        var showListenBrainzDialog by rememberSaveable { mutableStateOf(false) }

        AccountsCard(
            title = stringResource(R.string.accounts_listenbrainz_title),
            monogram = "LB",
            connected = lbConnected,
            statusText = when {
                lbConnected && lbUser.isNotBlank() ->
                    stringResource(R.string.accounts_status_connected_as, lbUser)
                lbConnected -> stringResource(R.string.accounts_status_connected)
                else -> stringResource(R.string.accounts_status_not_connected)
            },
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            if (lbConnected) {
                AccountsSwitchRow(
                    title = stringResource(R.string.accounts_listenbrainz_scrobble_title),
                    subtitle = stringResource(R.string.accounts_listenbrainz_scrobble_subtitle),
                    checked = lbScrobbleEnabled,
                    onCheckedChange = { lbScrobbleEnabled = it },
                )
                Spacer(modifier = Modifier.height(6.dp))
                AccountsSecondaryButton(
                    text = stringResource(R.string.accounts_action_disconnect),
                    onClick = {
                        ListenBrainzClient.disconnect()
                        lbConnected = false
                        lbUser = ""
                        lbScrobbleEnabled = true
                    }
                )
            } else {
                AccountsPrimaryButton(
                    text = stringResource(R.string.accounts_action_connect),
                    onClick = { showListenBrainzDialog = true }
                )
            }
        }

        if (showListenBrainzDialog) {
            ListenBrainzTokenDialog(
                onDismiss = { showListenBrainzDialog = false },
                onConnected = { userName ->
                    showListenBrainzDialog = false
                    lbConnected = true
                    lbUser = userName
                    lbScrobbleEnabled = true
                }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        /****** LISTENBRAINZ ******/

        /**** MUSIC IDENTIFIER ******/
        var isEnabledMusicIdentifier by rememberPreference(enableMusicIdentifierKey, false)
        var musicIdentifierProvider by rememberPreference(
            musicIdentifierProviderKey,
            MusicIdentifierProvider.AudioTagInfo
        )
        var musicIdentifierApi by rememberPreference(musicIdentifierApiKey, "")

        AccountsCard(
            title = stringResource(R.string.accounts_identifier_title),
            icon = R.drawable.locate,
            connected = isEnabledMusicIdentifier,
            statusText = if (isEnabledMusicIdentifier) musicIdentifierProvider.title
            else stringResource(R.string.accounts_status_off),
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            AccountsSwitchRow(
                title = stringResource(R.string.accounts_identifier_enable_title),
                subtitle = stringResource(R.string.accounts_identifier_off_info),
                checked = isEnabledMusicIdentifier,
                onCheckedChange = { isEnabledMusicIdentifier = it },
            )

            if (isEnabledMusicIdentifier) {
                AccountsDivider()
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.music_identifier_provider),
                    titleSecondary = musicIdentifierProvider.info,
                    selectedValue = musicIdentifierProvider,
                    onValueSelected = { musicIdentifierProvider = it },
                    valueText = { it.title },
                    offline = false
                )
                AccountsRow(
                    title = musicIdentifierProvider.subtitle,
                    subtitle = musicIdentifierProvider.website,
                    onClick = { runCatching { uriHandler.openUri(musicIdentifierProvider.website) } },
                )

                if (musicIdentifierProvider == MusicIdentifierProvider.AudioTagInfo) {
                    TextDialogSettingEntry(
                        title = stringResource(R.string.api_key),
                        text = musicIdentifierApi.ifEmpty { stringResource(R.string.if_empty_system_api_key_will_be_used) },
                        currentText = musicIdentifierApi,
                        onTextSave = { musicIdentifierApi = it },
                        validationType = ValidationType.None,
                        offline = false,
                        online = false
                    )

                    val localAudioTagger = LocalAudioTagger.current
                    LaunchedEffect(Unit) {
                        localAudioTagger.stat()
                    }
                    val statState by localAudioTagger.statsState.collectAsState()
                    if (statState?.success == true) {
                        Spacer(modifier = Modifier.height(6.dp))
                        BasicText(
                            text = stringResource(
                                R.string.api_expiration,
                                statState?.expirationDate?.take(10).orEmpty()
                            ),
                            style = typography().xxs.semiBold.copy(color = colorPalette().text),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicText(
                            text = stringResource(
                                R.string.api_queries_count,
                                statState?.queriesCount ?: "0"
                            ),
                            style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicText(
                            text = stringResource(
                                R.string.music_identifier_free_identification_seconds_remaining,
                                statState?.identificationFreeSecRemainder ?: "0"
                            ),
                            style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                        )
                    }
                }
            }
        }
        /**** MUSIC IDENTIFIER ******/

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

/** Full-height bottom sheet shared by the web-based login flows of this screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val roundness by rememberPreference(thumbnailRoundnessKey, ThumbnailRoundness.Heavy)
    CustomModalBottomSheet(
        showSheet = show,
        onDismissRequest = onDismiss,
        containerColor = colorPalette().background0,
        contentColor = colorPalette().text,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {},
        shape = roundness.shape(),
        content = content,
    )
}

fun isYtLoginEnabled(): Boolean {
    val isLoginEnabled = appContext().preferences.getBoolean(enableYouTubeLoginKey, false)
    return isLoginEnabled
}

fun isYtSyncEnabled(): Boolean {
    val isSyncEnabled = appContext().preferences.getBoolean(enableYouTubeSyncKey, false)
    return isSyncEnabled && isYtLoggedIn() && isYtLoginEnabled()
}

fun isYtLoggedIn(): Boolean {
    val cookie = appContext().preferences.getString(ytCookieKey, "")
    val isLoggedIn = cookie?.let { parseCookieString(it) }?.contains("SAPISID") == true && isYtLoginEnabled()
    return isLoggedIn
}
