package it.fast4x.riplay.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi

import com.yambo.music.R
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.ui.components.themed.InputTextDialog
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.extensions.preferences.logDebugEnabledKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("BatteryLife")
@ExperimentalAnimationApi
@Composable
fun MiscSettings() {
    val context = LocalContext.current

    var logDebugEnabled by rememberPreference(logDebugEnabledKey, false)

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    var fileName by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(null as String?) }

    val noLogAvailable = stringResource(R.string.no_log_available)
    var exportCrashlog by remember { mutableStateOf(false) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val file =
                File(
                    context.filesDir.resolve("logs"),
                    if (exportCrashlog) "YammboMusic_crash_log.txt" else "YammboMusic_log.txt"
                )
            if (file.exists()) {
                text = file.readText()
            } else {
                SmartMessage(noLogAvailable, type = PopupType.Info, context = context)
                return@rememberLauncherForActivityResult
            }

            context.applicationContext.contentResolver.openOutputStream(uri)
                ?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
        }

    var isExporting by rememberSaveable { mutableStateOf(false) }

    if (isExporting) {
        InputTextDialog(
            onDismiss = { isExporting = false },
            title = stringResource(R.string.enter_the_name_of_log_export),
            value = "",
            placeholder = stringResource(R.string.enter_the_name_of_log_export),
            setValue = { txt ->
                fileName = txt
                try {
                    @SuppressLint("SimpleDateFormat")
                    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss")
                    exportLauncher.launch(
                        "RMLog_${txt.take(20)}_${dateFormat.format(Date())}"
                    )
                } catch (e: ActivityNotFoundException) {
                    SmartMessage(
                        "Couldn't find an application to create documents",
                        type = PopupType.Warning, context = context
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        SettingsCard(title = stringResource(R.string.debug)) {
            SwitchSettingEntry(
                online = false,
                offline = false,
                title = stringResource(R.string.enable_log_debug),
                text = stringResource(R.string.if_enabled_create_a_log_file_to_highlight_errors),
                isChecked = logDebugEnabled,
                onCheckedChange = {
                    logDebugEnabled = it
                    if (!it) {
                        val file = File(context.filesDir.resolve("logs"), "YammboMusic_log.txt")
                        if (file.exists()) file.delete()

                        val filec = File(
                            context.filesDir.resolve("logs"),
                            "YammboMusic_crash_log.txt"
                        )
                        if (filec.exists()) filec.delete()
                    } else {
                        SmartMessage(
                            context.resources.getString(R.string.restarting_riplay_is_required),
                            type = PopupType.Info, context = context
                        )
                    }
                }
            )
            BasicText(
                text = stringResource(R.string.restarting_riplay_is_required),
                style = typography().xxs.copy(
                    color = colorPalette().red,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            ButtonBarSettingEntry(
                online = false,
                offline = false,
                isEnabled = logDebugEnabled,
                title = stringResource(R.string.export_log),
                text = "",
                icon = R.drawable.export,
                onClick = {
                    exportCrashlog = false
                    isExporting = true
                }
            )
            ButtonBarSettingEntry(
                online = false,
                offline = false,
                title = stringResource(R.string.export_crash_log),
                text = stringResource(R.string.is_always_enabled),
                icon = R.drawable.export,
                onClick = {
                    exportCrashlog = true
                    isExporting = true
                }
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}
