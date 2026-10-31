package it.fast4x.riplay.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.enums.ViewType
import it.fast4x.riplay.extensions.preferences.autosyncKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.viewTypeKey
import it.fast4x.riplay.ui.components.GlobalSheetState
import it.fast4x.riplay.ui.components.tab.ItemSize
import it.fast4x.riplay.ui.components.tab.toolbar.MenuIcon
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.components.themed.MenuToggleEntry
import it.fast4x.riplay.ui.components.themed.NoirChip
import it.fast4x.riplay.ui.components.themed.Search
import it.fast4x.riplay.ui.components.themed.SubMenuEntry
import it.fast4x.riplay.ui.screens.album.NoirListSearchField

/*
 * Noir tools row shared by the Albums, Artists and Playlists tabs. It replaces the old grey
 * icon toolbar: "Buscar" and "Aleatorio" chips plus a "Más" chip whose sheet holds the rest of
 * the actions with text labels (same pattern as the playlist page).
 */

/** Chips row: search toggle, shuffle and the "more" menu. */
@Composable
fun LibraryToolsRow(
    search: Search,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        NoirChip(
            text = stringResource(R.string.search),
            selected = false,
            icon = R.drawable.search,
            onClick = { search.onShortClick() }
        )
        NoirChip(
            text = stringResource(R.string.shuffle),
            selected = false,
            icon = R.drawable.shuffle,
            onClick = onShuffle
        )
        NoirChip(
            text = stringResource(R.string.library_tools_more),
            selected = false,
            icon = R.drawable.ellipsis_horizontal,
            onClick = onMore
        )
    }
}

/** Noir pill search field bound to the existing [Search] state (replaces Search.SearchBar). */
@Composable
fun LibrarySearchField(search: Search) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    AnimatedVisibility(visible = search.isVisible) {
        // Focus the field when it opens; drop focus when an item was picked.
        LaunchedEffect(search.isVisible, search.isFocused) {
            if (!search.isVisible) return@LaunchedEffect
            if (search.isFocused) focusRequester.requestFocus()
            else {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
        NoirListSearchField(
            value = search.input,
            onValueChange = { search.input = it },
            hint = stringResource(R.string.search),
            fieldModifier = Modifier.focusRequester(focusRequester),
            onDone = {
                search.isVisible = search.input.isNotBlank()
                search.isFocused = false
                keyboardController?.hide()
            }
        )
    }
}

/**
 * Body of the "more" sheet. [extra] goes first (screen specific actions), then the shared ones:
 * auto sync, random item (only when [onRandom] is given), item size and list view.
 */
@Composable
fun LibraryMoreMenu(
    menuState: GlobalSheetState,
    sync: MenuIcon,
    itemSize: ItemSize,
    viewType: MenuIcon,
    onRandom: (() -> Unit)? = null,
    randomEnabled: Boolean = true,
    extra: @Composable () -> Unit = {}
) {
    // Read inside the sheet so the switches follow the preference while it is open.
    val autoSync by rememberPreference(autosyncKey, false)
    val currentViewType by rememberPreference(viewTypeKey, ViewType.Grid)

    Menu {
        extra()
        MenuToggleEntry(
            icon = sync.iconId,
            text = sync.menuIconTitle,
            checked = autoSync,
            onClick = { sync.onShortClick() }
        )
        if (onRandom != null)
            MenuEntry(
                icon = R.drawable.dice,
                text = stringResource(R.string.library_tools_random_item),
                enabled = randomEnabled,
                onClick = {
                    menuState.hide()
                    onRandom()
                }
            )
        SubMenuEntry(
            icon = itemSize.iconId,
            text = itemSize.menuIconTitle,
            onClick = { itemSize.onShortClick() }
        )
        MenuToggleEntry(
            icon = R.drawable.list_view,
            text = stringResource(R.string.library_tools_list_view),
            checked = currentViewType == ViewType.List,
            onClick = { viewType.onShortClick() }
        )
    }
}
