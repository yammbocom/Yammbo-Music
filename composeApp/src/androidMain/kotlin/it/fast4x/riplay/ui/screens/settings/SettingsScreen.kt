package it.fast4x.riplay.ui.screens.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.ValidationType
import it.fast4x.riplay.ui.components.themed.DialogColorPicker
import it.fast4x.riplay.ui.components.themed.InputTextDialog
import it.fast4x.riplay.ui.components.themed.LocalSettingsCard
import it.fast4x.riplay.ui.components.themed.Slider
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.components.themed.StringListDialog
import it.fast4x.riplay.ui.components.themed.Switch
import it.fast4x.riplay.ui.components.themed.ValueSelectorDialog
import it.fast4x.riplay.ui.components.SubscriptionGateOverlay
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.ui.components.PageContainer
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.ui.components.themed.IDialog
import it.fast4x.riplay.utils.typography

private const val SettingsHomeIndex = -1

@ExperimentalMaterial3Api
@ExperimentalMaterialApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SettingsScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    // -1 = settings home (list of sections), otherwise the open section.
    var section by rememberSaveable { mutableStateOf(SettingsHomeIndex) }

    // System back goes from a section to the list before leaving settings.
    BackHandler(enabled = section != SettingsHomeIndex) { section = SettingsHomeIndex }

    PageContainer(
        navController = navController,
        miniPlayer = miniPlayer,
        // A section draws its own header (back + title); the app bar would double it.
        showTopBar = section == SettingsHomeIndex
    ) {
        AnimatedContent(
            targetState = section,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "settingsSection"
        ) { current ->
            if (current == SettingsHomeIndex) {
                SettingsHome(onOpen = { section = it })
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    SettingsSectionHeader(
                        title = settingsSectionTitle(current),
                        onBack = { section = SettingsHomeIndex }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        saveableStateHolder.SaveableStateProvider(current) {
                            SettingsSectionContent(current, navController)
                        }
                    }
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@ExperimentalMaterialApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
private fun SettingsSectionContent(index: Int, navController: NavController) {
    when (index) {
        0 -> SubscriptionGateOverlay { GeneralSettings(navController = navController) }
        1 -> SubscriptionGateOverlay { UiSettings(navController = navController) }
        2 -> SubscriptionGateOverlay { AppearanceSettings(navController = navController) }
        3 -> SubscriptionGateOverlay { HomeSettings(navController = navController) }
        4 -> DataSettings()
        5 -> {
            val activity = navController.context as? it.fast4x.riplay.MainActivity
            AccountsSettings(
                authManager = activity?.authManager,
                onLogout = {
                    navController.navigate(it.fast4x.riplay.enums.NavRoutes.login.name) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        6 -> MiscSettings()
        7 -> About()
    }
}

@Composable
private fun settingsSectionTitle(index: Int): String = when (index) {
    0 -> stringResource(R.string.tab_general)
    1 -> stringResource(R.string.ui_tab)
    2 -> stringResource(R.string.player_appearance)
    3 -> stringResource(R.string.home)
    4 -> stringResource(R.string.settings_title_data)
    5 -> stringResource(R.string.tab_accounts)
    6 -> stringResource(R.string.settings_title_misc)
    else -> stringResource(R.string.about)
}

@Composable
private fun SettingsSectionHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val backDescription = stringResource(R.string.back)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .glassSurface(CircleShape, elevation = 6.dp)
                .clickable(onClickLabel = backDescription, role = Role.Button, onClick = onBack)
        ) {
            Image(
                painter = painterResource(R.drawable.chevron_back),
                contentDescription = backDescription,
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(20.dp)
            )
        }
        BasicText(
            text = title,
            style = typography().l.semiBold.copy(color = colorPalette().text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private class SettingsHubRow(val index: Int, val icon: Int, val title: String, val subtitle: String)

@Composable
private fun SettingsHome(onOpen: (Int) -> Unit) {
    val rows = listOf(
        SettingsHubRow(0, R.drawable.grid_view, stringResource(R.string.tab_general), stringResource(R.string.settings_sub_general)),
        SettingsHubRow(1, R.drawable.ui, stringResource(R.string.ui_tab), stringResource(R.string.settings_sub_ui)),
        SettingsHubRow(2, R.drawable.color_palette, stringResource(R.string.player_appearance), stringResource(R.string.settings_sub_appearance)),
        SettingsHubRow(3, R.drawable.home, stringResource(R.string.home), stringResource(R.string.settings_sub_home)),
        SettingsHubRow(4, R.drawable.server, stringResource(R.string.settings_title_data), stringResource(R.string.settings_sub_data)),
        SettingsHubRow(5, R.drawable.person, stringResource(R.string.tab_accounts), stringResource(R.string.settings_sub_accounts)),
        SettingsHubRow(6, R.drawable.ellipsis_horizontal, stringResource(R.string.settings_title_misc), stringResource(R.string.settings_sub_misc)),
        SettingsHubRow(7, R.drawable.information, stringResource(R.string.about), stringResource(R.string.settings_sub_about)),
    )
    // Visual groups of the list, by row index.
    val groups = listOf(listOf(0, 1, 2, 3), listOf(4, 5), listOf(6, 7))

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        BasicText(
            text = stringResource(R.string.settings),
            style = typography().xxl.semiBold.copy(color = colorPalette().text),
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
        )

        groups.forEach { group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(RoundedCornerShape(20.dp), elevation = 4.dp)
            ) {
                group.forEachIndexed { position, rowIndex ->
                    val row = rows[rowIndex]
                    if (position > 0) {
                        // Inset past the icon circle, like a native list.
                        Box(
                            modifier = Modifier
                                .padding(start = 70.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colorPalette().background2)
                        )
                    }
                    SettingsHubRowItem(row, onClick = { onOpen(row.index) })
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

@Composable
private fun SettingsHubRowItem(row: SettingsHubRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colorPalette().background2)
        ) {
            Image(
                painter = painterResource(row.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                text = row.title,
                style = typography().s.semiBold.copy(color = colorPalette().text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = row.subtitle,
                style = typography().xxs.copy(color = colorPalette().textSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Image(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().textSecondary),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
inline fun StringListValueSelectorSettingsEntry(
    title: String,
    text: String,
    addTitle: String,
    addPlaceholder: String,
    conflictTitle: String,
    removeTitle: String,
    context: Context,
    list: List<String>,
    crossinline add: (String) -> Unit,
    crossinline remove: (String) -> Unit,
    online: Boolean = true,
    offline: Boolean = true
) {
    var showStringListDialog by remember {
        mutableStateOf(false)
    }


    if (showStringListDialog) {
        StringListDialog(
            title = title,
            addTitle = addTitle,
            addPlaceholder = addPlaceholder,
            removeTitle = removeTitle,
            conflictTitle = conflictTitle,
            list = list,
            add = add,
            remove = remove,
            onDismiss = { showStringListDialog = false },
        )
    }
    SettingsEntry(
        title = title,
        text = text,
        onClick = {
            showStringListDialog = true
        },
        online = online,
        offline = offline
    )
}



@Composable
inline fun <reified T : Enum<T>> EnumValueSelectorSettingsEntry(
    title: String,
    titleSecondary: String? = null,
    text: String? = null,
    selectedValue: T,
    noinline onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    noinline valueText: @Composable (T) -> String  = { it.name },
    noinline trailingContent: (@Composable () -> Unit) = {},
    online: Boolean = true,
    offline: Boolean = true
) {
    ValueSelectorSettingsEntry(
        title = title,
        titleSecondary = titleSecondary,
        text = text,
        selectedValue = selectedValue,
        values = enumValues<T>().toList(),
        onValueSelected = onValueSelected,
        modifier = modifier,
        isEnabled = isEnabled,
        valueText = valueText,
        trailingContent = trailingContent,
        online = online,
        offline = offline
    )
}

@Composable
fun <T> ValueSelectorSettingsEntry(
    title: String,
    titleSecondary: String? = null,
    text: String? = null,
    selectedValue: T,
    values: List<T>,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    valueText: @Composable (T) -> String = { it.toString() },
    trailingContent: (@Composable () -> Unit) = {},
    online: Boolean = true,
    offline: Boolean = true
) {
    var isShowingDialog by remember {
        mutableStateOf(false)
    }

    if (isShowingDialog) {
        ValueSelectorDialog(
            onDismiss = { isShowingDialog = false },
            title = title,
            selectedValue = selectedValue,
            values = values,
            onValueSelected = onValueSelected,
            valueText = valueText
        )
    }

    // Current value on the right, description (if any) under the title.
    SettingsEntry(
        title = title,
        titleSecondary = titleSecondary,
        text = text.orEmpty(),
        modifier = modifier,
        isEnabled = isEnabled,
        onClick = { isShowingDialog = true },
        trailingContent = {
            BasicText(
                text = valueText(selectedValue),
                style = typography().xs.copy(color = colorPalette().textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            trailingContent()
        },
        online = online,
        offline = offline
    )
}

@Composable
fun SwitchSettingEntry(
    title: String,
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    online: Boolean = true,
    offline: Boolean = true
) {
    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = { onCheckedChange(!isChecked) },
        trailingContent = { Switch(isChecked = isChecked) },
        modifier = modifier,
        online = online,
        offline = offline
    )
}

@Composable
fun SettingsEntry(
    modifier: Modifier = Modifier,
    title: String,
    titleSecondary: String? = null,
    text: String,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    online: Boolean = true,
    offline: Boolean = true
) {
    // Inside a settings card (settingsItem) rows get the card inset and a hairline
    // above them; the first row's hairline is clipped by the card's top edge.
    val inCard = LocalSettingsCard.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (inCard) SettingsRowDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .alpha(if (isEnabled) 1f else 0.5f)
                .clickable(enabled = isEnabled, onClick = onClick)
                .padding(horizontal = if (inCard) 12.dp else 4.dp, vertical = 14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = title,
                    style = typography().xs.semiBold.copy(color = colorPalette().text),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (text.isNotEmpty()) {
                    BasicText(
                        text = text,
                        style = typography().xxs.copy(color = colorPalette().textSecondary),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (titleSecondary != null) {
                    BasicText(
                        text = titleSecondary,
                        style = typography().xxs.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            trailingContent?.invoke()
        }
    }
}

/**
 * Pulls the content up by one hairline so the divider of the first row in a card is
 * clipped away by the card's rounded top edge (same trick as settingsItem).
 */
internal fun Modifier.hideLeadingHairline(): Modifier = layout { measurable, constraints ->
    val cut = 1.dp.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - cut).coerceAtLeast(0)) {
        placeable.place(0, -cut)
    }
}

/**
 * Hairline between rows of a settings card (same step as the MyAccountTab cards).
 */
@Composable
private fun SettingsRowDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().background2)
    )
}

@Composable
fun SettingsEntryGroup(
    online: Boolean = true,
    offline: Boolean = true,
    content: @Composable () -> Unit,
) {
    val inCard = LocalSettingsCard.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (inCard) SettingsRowDivider()
        // Laid out like a SettingsEntry row; the old 4x30dp side bar is gone.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (inCard) 12.dp else 4.dp, vertical = 14.dp)
        ) {
            content()
//            SettingsContextIcons(
//                modifier = Modifier
//                    .align(Alignment.BottomEnd),
//                online = online,
//                offline = offline
//            )
        }
    }
}

@Composable
fun SettingsContextIcons(
    modifier: Modifier = Modifier,
    online: Boolean = true,
    offline: Boolean = true
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (online)
            Image(
                painter = painterResource(R.drawable.internet),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(12.dp)
            )
        if (offline)
            Image(
                painter = painterResource(R.drawable.no_internet),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(12.dp)
            )
    }
}

@Composable
fun SettingsTopDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = typography().xs.secondary,
        modifier = modifier
            .padding(start = 12.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
    important: Boolean = false,
) {
    BasicText(
        text = text,
        style = if (important) typography().xxs.semiBold.color(colorPalette().text)
        else typography().xxs.secondary,
        modifier = modifier
            .padding(start = 12.dp)
            //.padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
    )
}

@Composable
fun ImportantSettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = typography().xxs.semiBold.color(colorPalette().text),
        modifier = modifier
            .padding(start = 12.dp)
            .padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsEntryGroupText(
    title: String,
    color: Color = colorPalette().textSecondary,
    uppercase: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = if (uppercase) title.uppercase() else title,
        style = typography().xxs.copy(
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            letterSpacing = 1.sp
        ),
        modifier = modifier
            .padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsGroupSpacer(
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .height(24.dp)
    )
}

@Composable
fun TextDialogSettingEntry(
    title: String,
    text: String,
    currentText: String,
    onTextSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    validationType: ValidationType = ValidationType.None,
    offline: Boolean = true,
    online: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    //val context = LocalContext.current

    if (showDialog) {
        InputTextDialog(
            onDismiss = { showDialog = false },
            title = title,
            value = currentText,
            placeholder = title,
            setValue = {
                onTextSave(it)
                //context.toast("Preference Saved")
            },
            validationType = validationType,
            setValueRequireNotNull = validationType != ValidationType.None

        )
        /*
        TextFieldDialog(hintText = title ,
            onDismiss = { showDialog = false },
            onDone ={ value ->
                onTextSave(value)
                //context.toast("Preference Saved")
            },
            //doneText = "Save",
            initialTextInput = currentText
        )
         */
    }
    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = { showDialog = true },
        trailingContent = { },
        modifier = modifier,
        online = online,
        offline = offline
    )
}

@Composable
fun ColorSettingEntry(
    title: String,
    text: String,
    color: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = { showColorPicker = true },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(BorderStroke(1.dp, colorPalette().textDisabled), CircleShape)
            )
        },
        modifier = modifier
    )

    if (showColorPicker)
        DialogColorPicker(onDismiss = { showColorPicker = false }, color = color) {
            onColorSelected(it)
            showColorPicker = false
            SmartMessage(context.resources.getString(R.string.info_color_s_applied).format(title), context = context)
        }

}

@Composable
fun ButtonBarSettingEntry(
    title: String,
    text: String,
    icon: Int,
    iconSize: Dp = 24.dp,
    iconColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    online: Boolean = true,
    offline: Boolean = true
) {
    SettingsEntry(
        title = title,
        text = text,
        isEnabled = isEnabled,
        onClick = onClick,
        trailingContent = {
            Image(
                painter = painterResource(icon),
                colorFilter = ColorFilter.tint(iconColor ?: colorPalette().text),
                modifier = Modifier.size(iconSize),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        },
        modifier = modifier,
        online = online,
        offline = offline
    )

}

@Composable
fun SliderSettingsEntry(
    title: String,
    text: String,
    state: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onSlide: (Float) -> Unit = { },
    onSlideComplete: () -> Unit = { },
    toDisplay: @Composable (Float) -> String = { it.toString() },
    steps: Int = 0,
    isEnabled: Boolean = true,
    usePadding: Boolean = true
) = Column(modifier = modifier) {

    val manualEnterDialog = object: IDialog {

        var valueFloat: Float by remember( state ) { mutableFloatStateOf( state ) }

        override val dialogTitle: String
            @Composable
            get() = stringResource( R.string.enter_the_value )

        override var isActive: Boolean by rememberSaveable { mutableStateOf(false) }
        override var value: String by remember( valueFloat ) {
            mutableStateOf( "%.1f".format( valueFloat ).replace(",", ".") )
        }

        override fun onSet( newValue: String ) {
            this.valueFloat = newValue.toFloatOrNull() ?: return
            onSlide( this.valueFloat )
            onSlideComplete()

            onDismiss()
        }
    }
    manualEnterDialog.Render()

    SettingsEntry(
        title = title,
        text = "$text (${toDisplay(state)})",
        onClick = manualEnterDialog::onShortClick,
        isEnabled = isEnabled,
        //usePadding = usePadding
    )

    Slider(
        state = state,
        setState = { value: Float ->
            manualEnterDialog.valueFloat = value
            onSlide(value)
        },
        onSlideComplete = onSlideComplete,
        range = range,
        steps = steps,
        modifier = Modifier
            .height(36.dp)
            .alpha(if (isEnabled) 1f else 0.5f)
            .let { if (usePadding) it.padding(horizontal = 12.dp) else it }
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    )
}

@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    description: String? = null,
    important: Boolean = false,
    color: Color = colorPalette().textSecondary,
    uppercase: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        if (title != null) {
            SettingsEntryGroupText(title = title, color = color, uppercase = uppercase)
        }
        Column(modifier = modifier) {


            description?.let { description ->
                SettingsDescription(
                    text = description,
                    important = important
                )
            }

            content()

            SettingsGroupSpacer()
        }
    }
}