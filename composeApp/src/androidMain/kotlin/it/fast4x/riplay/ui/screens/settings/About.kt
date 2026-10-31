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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.tight
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
        Spacer(modifier = Modifier.height(24.dp))

        // === Hero ===
        StaggeredEntry(index = 0) {
            AboutHeroCard()
        }

        Spacer(modifier = Modifier.height(32.dp))

        // === Social ===
        StaggeredEntry(index = 1) {
          Column {
            NoirEyebrow(
                text = stringResource(R.string.settings_about_connect),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                SocialChip(
                    iconId = R.drawable.brand_facebook,
                    label = "Facebook",
                ) { uriHandler.openUri("https://www.facebook.com/yammbo") }
                SocialChip(
                    iconId = R.drawable.brand_instagram,
                    label = "Instagram",
                ) { uriHandler.openUri("https://instagram.com/yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_x,
                    label = "X",
                ) { uriHandler.openUri("https://x.com/yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_tiktok,
                    label = "TikTok",
                ) { uriHandler.openUri("https://www.tiktok.com/@yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_whatsapp,
                    label = "WhatsApp",
                ) { uriHandler.openUri("https://api.whatsapp.com/send?phone=5623464876") }
            }
          }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // === Support ===
        StaggeredEntry(index = 2) {
          Column {
            NoirEyebrow(
                text = stringResource(R.string.settings_about_support),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        // === Legal ===
        StaggeredEntry(index = 3) {
          Column {
            NoirEyebrow(
                text = stringResource(R.string.settings_about_legal),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
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

        Spacer(modifier = Modifier.navigationBarsPadding().height(Dimensions.bottomSpacer + 24.dp))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .border(1.dp, colors.text.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.yambo_icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        BasicText(
            text = "Yammbo Music",
            style = typo.xxl.tight.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = colors.text,
                textAlign = TextAlign.Center,
            ),
        )

        Spacer(modifier = Modifier.height(6.dp))

        BasicText(
            text = stringResource(R.string.settings_about_tagline),
            style = typo.s.copy(
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            ),
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Version pill: outlined, mono eyebrow.
        val pillShape = RoundedCornerShape(percent = 50)
        Box(
            modifier = Modifier
                .clip(pillShape)
                .border(1.dp, colors.text.copy(alpha = 0.16f), pillShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            NoirEyebrow(
                text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            )
        }
    }
}

@Composable
private fun LinkGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colorPalette().background1)
            .border(1.dp, colorPalette().text.copy(alpha = 0.10f), shape),
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
                .size(56.dp)
                .clip(CircleShape)
                .border(1.dp, colorPalette().text.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = label,
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BasicText(
            text = label,
            style = typo.xxs.copy(
                fontSize = 12.sp,
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
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background2),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typo.s.copy(fontSize = 16.sp, color = colors.text),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typo.xxs.copy(fontSize = 13.sp, color = colors.textSecondary),
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
            .padding(start = 64.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().text.copy(alpha = 0.10f)),
    )
}
