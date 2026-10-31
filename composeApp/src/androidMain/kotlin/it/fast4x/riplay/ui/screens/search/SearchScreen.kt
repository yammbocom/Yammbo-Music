package it.fast4x.riplay.ui.screens.search

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.riplay.extensions.persist.PersistMapCleanup
import com.yambo.music.R
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.SearchQuery
import it.fast4x.riplay.enums.ContentType
import it.fast4x.riplay.extensions.preferences.enableVoiceInputKey
import it.fast4x.riplay.extensions.preferences.pauseSearchHistoryKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.homeScreenTabIndexKey
import it.fast4x.riplay.enums.NavRoutes
import androidx.activity.compose.BackHandler
import it.fast4x.riplay.ui.components.themed.IconButton
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.components.ScreenContainer
import it.fast4x.riplay.ui.components.glassSurface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import it.fast4x.riplay.ui.components.themed.NoirChip
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.ui.styling.align
import it.fast4x.riplay.ui.styling.medium
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.StartVoiceInput
import it.fast4x.riplay.utils.typography
import kotlinx.serialization.ExperimentalSerializationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import android.content.Context

/**
 * Asks the Home screen to switch tab when it is the screen on show (header search icon).
 * Home consumes the value and clears it.
 */
object HomeTabRequest {
    var requested by mutableStateOf<Int?>(null)
}

/** Opens Home on the given tab: switches in place when Home is on screen, else goes back to it. */
fun openHomeTab(navController: NavController, context: Context, index: Int) {
    val route = navController.currentBackStackEntry?.destination?.route
    if (route == NavRoutes.home.name) {
        HomeTabRequest.requested = index
    } else {
        context.preferences.edit().putInt(homeScreenTabIndexKey, index).apply()
        navController.navigate(NavRoutes.home.name) {
            popUpTo(NavRoutes.home.name) { inclusive = true }
        }
    }
}

// Standalone route, only for deep links that carry text (the Search tab of Home is the usual way in).
@UnstableApi
@OptIn(ExperimentalSerializationApi::class, ExperimentalMaterialApi::class, ExperimentalTextApi::class,
    ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    initialTextInput: String = "",
) {
    val context = LocalContext.current
    var searchBackAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    BackHandler(enabled = searchBackAction != null) { searchBackAction?.invoke() }

    ScreenContainer(
        navController = navController,
        tabIndex = 3,
        miniPlayer = miniPlayer,
        // Yammbo Noir: the bar is the app's own (Search selected), the same pill as on Home;
        // the search modes and the result categories are chips in the page.
        navBarTabIndex = 3,
        onNavBarTabChanged = { index -> openHomeTab(navController, context, index) },
        sharedChrome = true,
        navBarContent = { item ->
            item(0, stringResource(R.string.home), R.drawable.home)
            item(1, stringResource(R.string.live_radio_tab), R.drawable.radio)
            item(5, stringResource(R.string.podcasts_tab_podcasts), R.drawable.podcasts_tab)
            item(2, stringResource(R.string.my_music), R.drawable.musical_notes)
            item(3, stringResource(R.string.search), R.drawable.search)
        }
    ) { _ ->
        SearchContent(
            navController = navController,
            initialTextInput = initialTextInput,
            onBackAction = { searchBackAction = it },
        )
    }
}

// Brings a chip into view only when it is not fully visible, so a tap on a visible chip never
// makes the row jump.
private suspend fun LazyListState.scrollToItemIfHidden(position: Int) {
    if (position < 0) return
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == position }
    val fullyVisible = info != null &&
            info.offset >= layoutInfo.viewportStartOffset &&
            info.offset + info.size <= layoutInfo.viewportEndOffset
    if (!fullyVisible) animateScrollToItem(position)
}

/**
 * The search page without a screen around it: the Search tab of Home and the standalone route
 * both host it. The field and the chips live outside the animated body, so changing the chip
 * only swaps the content below and keeps the chip row where it was.
 *
 * [onBackAction] receives the action that system back should run while a query is on screen
 * (null otherwise), so the host that owns the back button can run it.
 */
@UnstableApi
@OptIn(ExperimentalSerializationApi::class, ExperimentalMaterialApi::class, ExperimentalTextApi::class,
    ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchContent(
    navController: NavController,
    initialTextInput: String = "",
    onBackAction: (action: (() -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val saveableStateHolder = rememberSaveableStateHolder()

    val (textFieldValue, onTextFieldValueChanged) = rememberSaveable(
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    PersistMapCleanup(tagPrefix = "search/")

    // A non-empty initial input (e.g. music identifier, go-to-link, audio tagger)
    // lands directly on the results view.
    var submittedQuery by rememberSaveable { mutableStateOf(initialTextInput) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current


    val isEnabledVoiceInput by rememberPreference(enableVoiceInputKey, true)
    var startVoiceInput by remember { mutableStateOf(false) }

    if (startVoiceInput) {
        StartVoiceInput(
            onTextRecognized = {
                onTextFieldValueChanged(TextFieldValue(it, selection = TextRange(it.length)))
                submittedQuery = it
                startVoiceInput = false
            },
            onRecognitionError = { startVoiceInput = false },
            onListening = {}
        )
    }

    var filterContentType by remember { mutableStateOf(ContentType.All) }

    val isSearchActive = submittedQuery.isNotEmpty()

    // Back from the results goes back to the search page (the typed text stays), not away.
    // The host runs it (Home's single back handler, or SearchScreen's), so it never jumps
    // ahead of closing the player sheet.
    val (baseTabIndex, onBaseTabChanged) = rememberSaveable { mutableStateOf(0) }
    val (resultTabIndex, onResultTabChanged) = rememberSaveable { mutableStateOf(0) }

    DisposableEffect(isSearchActive) {
        onBackAction(if (isSearchActive) ({ submittedQuery = "" }) else null)
        onDispose { onBackAction(null) }
    }

    // Where to search: what the old tab bar offered, now chips over the field.
    val searchModes = listOf(
        Triple(0, stringResource(R.string.online), R.drawable.globe),
        Triple(1, stringResource(R.string.library), R.drawable.library),
        Triple(2, stringResource(R.string.noir_link_mode), R.drawable.link)
    )
    // Result categories: the order the chips show, with the indexes the results use.
    val resultCategories = listOf(
        0 to stringResource(R.string.songs),
        6 to stringResource(R.string.podcasts),
        1 to stringResource(R.string.albums),
        2 to stringResource(R.string.artists),
        3 to stringResource(R.string.videos),
        4 to stringResource(R.string.playlists),
        5 to stringResource(R.string.featured)
    )
    // Hoisted so the rows keep their scroll when the body below changes.
    val baseChipsState = rememberLazyListState()
    val resultChipsState = rememberLazyListState()
    LaunchedEffect(baseTabIndex, isSearchActive) {
        if (!isSearchActive) baseChipsState.scrollToItemIfHidden(searchModes.indexOfFirst { it.first == baseTabIndex })
    }
    LaunchedEffect(resultTabIndex, isSearchActive) {
        if (isSearchActive) resultChipsState.scrollToItemIfHidden(resultCategories.indexOfFirst { it.first == resultTabIndex })
    }

    // History is saved on explicit submit (keyboard action / results view),
    // NOT while typing: a per-keystroke insert here was persisting every
    // prefix ("madison", "madison b", "madison be"...) as its own entry.

    // Yammbo Noir: before searching the field is the screen's one solid white element (black
    // text); with results on screen it turns into a dark field so the results lead.
    val fieldBg = if (isSearchActive) colorPalette().background1 else colorPalette().text
    val fieldContent = if (isSearchActive) colorPalette().text else colorPalette().background0

    val decorationBox: @Composable (@Composable () -> Unit) -> Unit = { innerTextField ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(all = 10.dp)
                .fillMaxWidth()
        ) {


            IconButton(
                onClick = {},
                icon = R.drawable.search,
                color = fieldContent.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )


            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.padding(horizontal = 10.dp).weight(1f)
            ) {
                if (textFieldValue.text.isEmpty())
                    BasicText(
                        text = stringResource(R.string.noir_search_hint),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = typography().l.copy(color = fieldContent.copy(alpha = 0.5f))
                    )

                innerTextField()
            }
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 5.dp)
                ) {
                    if (isEnabledVoiceInput) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(fieldContent)
                                .clickable { startVoiceInput = true }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.mic),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(fieldBg),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    if (textFieldValue.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onTextFieldValueChanged(TextFieldValue(""))
                                submittedQuery = ""
                                focusRequester.requestFocus()
                            },
                            icon = R.drawable.close,
                            color = fieldContent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }


    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSearchActive) {
            // No big page title: the bar already says where the user is.
            LazyRow(
                state = baseChipsState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                items(searchModes, key = { it.first }) { (index, label, icon) ->
                    NoirChip(
                        text = label,
                        selected = baseTabIndex == index,
                        icon = icon,
                        onClick = { onBaseTabChanged(index) }
                    )
                }
            }
        }
        if (baseTabIndex < 2 || isSearchActive) {
            // === Search bar (Yammbo redesigned style) ===
            // Pill-shape container with a thin border that lights up to the
            // theme's accent color when the field is focused (220 ms easing).
            val searchInteractionSource = remember { MutableInteractionSource() }
            val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
            val animatedBorderAlpha by animateFloatAsState(
                targetValue = if (isSearchFocused) 0.55f else 0.10f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "search-border-alpha",
            )
            val animatedBgAlpha by animateFloatAsState(
                targetValue = if (isSearchFocused) 1f else 0.94f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "search-bg-alpha",
            )
            // Full pill, as in the reference: half the field height.
            val searchBarShape = RoundedCornerShape(28.dp)

            BasicTextField(
                value = textFieldValue,
                onValueChange = { newTextFieldValue ->
                    onTextFieldValueChanged(newTextFieldValue)

                    if (newTextFieldValue.text.isEmpty()) {
                        submittedQuery = ""
                    }
                },
                interactionSource = searchInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // Full pill on glass. The accent ring still marks focus, drawn
                    // over the glass border so the field reads as active.
                    .clip(searchBarShape)
                    .background(fieldBg)
                    .border(
                        width = 1.dp,
                        color = colorPalette().text.copy(alpha = if (isSearchActive) animatedBorderAlpha * 0.5f else 0f),
                        shape = searchBarShape,
                    )
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (!it.hasFocus) {
                            keyboardController?.hide()
                        }
                    },
                singleLine = true,
                textStyle = typography().l.medium.align(TextAlign.Start).copy(color = fieldContent),
                cursorBrush = SolidColor(fieldContent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (textFieldValue.text.isNotEmpty()) {
                            submittedQuery = textFieldValue.text
                            keyboardController?.hide()

                            if (!context.preferences.getBoolean(pauseSearchHistoryKey, false)) {
                                Database.asyncTransaction {
                                    insert(SearchQuery(query = textFieldValue.text))
                                }
                            }
                        }
                    }
                ),
                decorationBox = decorationBox
            )
        }

        if (isSearchActive) {
            LazyRow(
                state = resultChipsState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                items(resultCategories, key = { it.first }) { (index, label) ->
                    NoirChip(
                        text = label,
                        selected = resultTabIndex == index,
                        onClick = { onResultTabChanged(index) }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            // Only this part animates: results get +100 so the same tab number in the two
            // modes is a different page.
            AnimatedContent(
                targetState = (if (isSearchActive) 100 else 0) + (if (isSearchActive) resultTabIndex else baseTabIndex),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(90)) },
                label = "search-body",
                modifier = Modifier.fillMaxSize()
            ) { bodyKey ->
                val showingResults = bodyKey >= 100
                val currentTabIndex = bodyKey % 100
                saveableStateHolder.SaveableStateProvider(bodyKey) {
                    if (showingResults) {
                        SearchResultsContent(
                            query = submittedQuery,
                            tabIndex = currentTabIndex,
                            filterContentType = filterContentType,
                            onFilterChanged = { filterContentType = it },
                            navController = navController,
                            onSaveHistory = {
                                if (!context.preferences.getBoolean(
                                        pauseSearchHistoryKey,
                                        false
                                    )
                                ) {
                                    Database.asyncTransaction { insert(SearchQuery(query = textFieldValue.text)) }
                                }
                            },
                            focusRequester = focusRequester,
                            keyboardController = keyboardController
                        )
                    } else {
                        when (currentTabIndex) {
                            0 -> OnlineSearch(
                                navController = navController,
                                textFieldValue = textFieldValue,
                                onTextFieldValueChanged = onTextFieldValueChanged,
                                onSearch = { query ->
                                    submittedQuery = query
                                    keyboardController?.hide()
                                },
                                decorationBox = decorationBox
                            )

                            1 -> LocalSongSearch(
                                navController = navController,
                                textFieldValue = textFieldValue,
                                onTextFieldValueChanged = onTextFieldValueChanged,
                                decorationBox = decorationBox,
                                onAction1 = { onBaseTabChanged(0) },
                                onAction2 = { onBaseTabChanged(1) },
                                onAction3 = { onBaseTabChanged(2) },
                                onAction4 = {}
                            )

                            2 -> GoToLink(
                                navController = navController,
                                textFieldValue = textFieldValue,
                                onTextFieldValueChanged = onTextFieldValueChanged,
                                decorationBox = decorationBox,
                                onAction1 = { onBaseTabChanged(0) },
                                onAction2 = { onBaseTabChanged(1) },
                                onAction3 = { onBaseTabChanged(2) },
                                onAction4 = {}
                            )
                        }
                    }
                }
            }
        }
    }
}