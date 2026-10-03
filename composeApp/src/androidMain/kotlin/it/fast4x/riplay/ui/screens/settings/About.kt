package it.fast4x.riplay.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yambo.music.BuildConfig
import com.yambo.music.R
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.customtabs.YammboWebViewActivity
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.launch


@ExperimentalAnimationApi
@Composable
fun About() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val colors = colorPalette()
    val typo = typography()

    var showBugReportSheet by remember { mutableStateOf(false) }
    val authManager = remember { YammboAuthManager(context) }

    // Resolved here because the click handlers below are not composable.
    val helpCenterTitle = stringResource(R.string.settings_about_help_center)
    val liveChatTitle = stringResource(R.string.settings_about_live_chat)

    Column(
        modifier = Modifier
            .background(colors.background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // === Hero ===
        StaggeredEntry(index = 0) {
            AboutHeroCard()
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Social ===
        StaggeredEntry(index = 1) {
            SectionLabel(stringResource(R.string.settings_about_connect))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background1)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                SocialChip(
                    iconId = R.drawable.brand_facebook,
                    label = "Facebook",
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://www.facebook.com/yammbo") }
                SocialChip(
                    iconId = R.drawable.brand_instagram,
                    label = "Instagram",
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://instagram.com/yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_x,
                    label = "X",
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://x.com/yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_tiktok,
                    label = "TikTok",
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://www.tiktok.com/@yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_whatsapp,
                    label = "WhatsApp",
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://api.whatsapp.com/send?phone=5623464876") }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Support ===
        StaggeredEntry(index = 2) {
            SectionLabel(stringResource(R.string.settings_about_support))
            Spacer(modifier = Modifier.height(10.dp))
            LinkGroup {
                AboutLinkRow(
                    title = stringResource(R.string.settings_about_website),
                    subtitle = "music.yammbo.com",
                    iconId = R.drawable.globe,
                ) { uriHandler.openUri("https://music.yammbo.com") }
                AboutRowDivider()
                AboutLinkRow(
                    title = helpCenterTitle,
                    subtitle = stringResource(R.string.settings_about_help_center_sub),
                    iconId = R.drawable.information,
                ) { YammboWebViewActivity.open(context, "https://help.yammbo.com/", helpCenterTitle) }
                AboutRowDivider()
                AboutLinkRow(
                    title = liveChatTitle,
                    subtitle = stringResource(R.string.settings_about_live_chat_sub),
                    iconId = R.drawable.help_circle,
                ) { YammboWebViewActivity.open(context, "https://tawk.to/yammbo", liveChatTitle) }
                AboutRowDivider()
                AboutLinkRow(
                    title = stringResource(R.string.report_an_issue),
                    subtitle = stringResource(R.string.settings_about_report_sub),
                    iconId = R.drawable.alert_circle,
                ) { showBugReportSheet = true }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Legal ===
        StaggeredEntry(index = 3) {
            SectionLabel(stringResource(R.string.settings_about_legal))
            Spacer(modifier = Modifier.height(10.dp))
            LinkGroup {
                AboutLinkRow(
                    title = stringResource(R.string.settings_about_privacy),
                    subtitle = stringResource(R.string.settings_about_privacy_sub),
                    iconId = R.drawable.shield_checkmark,
                ) { uriHandler.openUri("https://music.yammbo.com/pages/privacy-policy") }
                AboutRowDivider()
                AboutLinkRow(
                    title = stringResource(R.string.settings_about_terms),
                    subtitle = stringResource(R.string.settings_about_terms_sub),
                    iconId = R.drawable.singlepage,
                ) { uriHandler.openUri("https://music.yammbo.com/pages/terms-of-service") }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // === Footer ===
        StaggeredEntry(index = 4) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = stringResource(R.string.settings_about_made_by),
                    style = typo.xs.copy(
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.settings_about_copyright),
                    style = typo.xxs.copy(
                        color = colors.textDisabled,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }

    if (showBugReportSheet) {
        BugReportSheet(
            authManager = authManager,
            onDismiss = { showBugReportSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BugReportSheet(
    authManager: YammboAuthManager,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = colorPalette()
    val typo = typography()
    val scope = rememberCoroutineScope()

    // Resolved here because the button handler is not composable.
    val invalidMessage = stringResource(R.string.settings_about_bug_invalid)
    val sentMessage = stringResource(R.string.settings_about_bug_sent)
    val failedMessage = stringResource(R.string.settings_about_bug_failed)

    var name by remember { mutableStateOf(authManager.getUserName().orEmpty()) }
    var email by remember { mutableStateOf(authManager.getUserEmail().orEmpty()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.textDisabled,
        cursorColor = colors.accent,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text,
    )

    CustomModalBottomSheet(
        showSheet = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background0,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            BasicText(
                text = stringResource(R.string.settings_about_bug_title),
                style = typo.l.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = stringResource(R.string.settings_about_bug_subtitle),
                style = typo.xxs.secondary,
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_about_bug_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.settings_about_bug_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text(stringResource(R.string.settings_about_bug_message)) },
                minLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || messageText.isBlank() ||
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
                    ) {
                        SmartMessage(
                            invalidMessage,
                            type = PopupType.Error,
                            context = context,
                        )
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val device =
                            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
                        val result = YammboApiService.reportBug(
                            name = name.trim(),
                            email = email.trim(),
                            message = messageText.trim(),
                            appVersion = BuildConfig.VERSION_NAME,
                            device = device,
                            token = authManager.getAccessToken(),
                        )
                        if (result.isSuccess && result.getOrNull()?.success == true) {
                            SmartMessage(
                                sentMessage,
                                type = PopupType.Success,
                                context = context,
                            )
                            onDismiss()
                        } else {
                            SmartMessage(
                                failedMessage,
                                type = PopupType.Error,
                                context = context,
                            )
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = colors.onAccent,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.settings_about_bug_send))
                }
            }
        }
    }
}

@Composable
private fun AboutHeroCard() {
    val colors = colorPalette()
    val typo = typography()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = 0.28f),
                        colors.background2.copy(alpha = 0.92f),
                    )
                )
            )
            .padding(vertical = 32.dp, horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon — circle backdrop with subtle border for depth.
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colors.background0)
                    .border(
                        width = 2.dp,
                        color = colors.accent.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.yambo_icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            BasicText(
                text = "Yammbo Music",
                style = typo.l.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(modifier = Modifier.height(2.dp))

            BasicText(
                text = stringResource(R.string.settings_about_tagline),
                style = typo.s.copy(
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Version pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.background0.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicText(
                    text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = typo.xs.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = typography().xxs.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().textSecondary,
            letterSpacing = 1.sp,
        ),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun LinkGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorPalette().background1)
            .padding(horizontal = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun SocialChip(
    iconId: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val typo = typography()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.pressable(onClick = onClick),
    ) {
        // Black and white only: the platform colors are gone on purpose.
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(colorPalette().background2),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = label,
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        BasicText(
            text = label,
            style = typo.xxs.copy(
                color = colorPalette().textSecondary,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    subtitle: String,
    iconId: Int,
    onClick: () -> Unit,
) {
    val colors = colorPalette()
    val typo = typography()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.background2),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typo.s.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                ),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typo.xxs.secondary,
                maxLines = 1,
            )
        }
        Image(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AboutRowDivider() {
    Box(
        modifier = Modifier
            .padding(start = 66.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().background2),
    )
}
