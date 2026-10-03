package it.fast4x.riplay.ui.components.themed

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yambo.music.R
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.spotify.SpotifyEmbed
import it.fast4x.riplay.utils.spotify.SpotifyImport
import it.fast4x.riplay.utils.typography

/**
 * Drawn once at the root of the app: the "Import from Spotify" dialog (link field, then the
 * progress), wherever the import was started from (Library menu, a shared link, a CSV).
 * Also starts the daily background refresh of imported playlists.
 */
@Composable
fun SpotifyImportHost() {
    LaunchedEffect(Unit) { SpotifyImport.autoRefreshOnce() }
    if (SpotifyImport.dialogVisible) SpotifyImportDialog()
}

@Composable
private fun SpotifyImportDialog() {
    val context = LocalContext.current
    val running = SpotifyImport.running

    // Prefill with a Spotify link waiting in the clipboard.
    LaunchedEffect(Unit) {
        if (SpotifyImport.linkDraft.isBlank() && !running) {
            clipboardText(context)?.takeIf { SpotifyEmbed.looksLikeSpotifyCollection(it) }
                ?.let { SpotifyImport.linkDraft = it.trim() }
        }
    }

    Dialog(onDismissRequest = SpotifyImport::closeDialog) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            color = colorPalette().background1
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 280.dp)
            ) {
                Text(
                    text = stringResource(R.string.spotify_import_title),
                    style = typography().m.semiBold,
                    color = colorPalette().text
                )
                Spacer(Modifier.height(16.dp))

                if (running) RunningContent() else LinkContent()
            }
        }
    }
}

@Composable
private fun LinkContent() {
    OutlinedTextField(
        value = SpotifyImport.linkDraft,
        onValueChange = { SpotifyImport.linkDraft = it },
        label = { Text(stringResource(R.string.spotify_import_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorPalette().text,
            unfocusedBorderColor = colorPalette().textDisabled,
            cursorColor = colorPalette().text,
            focusedTextColor = colorPalette().text,
            unfocusedTextColor = colorPalette().text,
            focusedLabelColor = colorPalette().text,
            unfocusedLabelColor = colorPalette().textSecondary
        )
    )

    SpotifyImport.errorText?.let { error ->
        Spacer(Modifier.height(8.dp))
        Text(text = error, style = typography().xs.semiBold, color = colorPalette().text)
    }

    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.spotify_import_note),
        style = typography().xs,
        color = colorPalette().textSecondary
    )

    Spacer(Modifier.height(24.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = SpotifyImport::closeDialog) {
            Text(stringResource(R.string.cancel), color = colorPalette().text)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            shape = CircleShape,
            enabled = SpotifyImport.linkDraft.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette().text,
                contentColor = colorPalette().background0,
                disabledContainerColor = colorPalette().textDisabled,
                disabledContentColor = colorPalette().background0
            ),
            onClick = { SpotifyImport.startFromLink(SpotifyImport.linkDraft.trim()) }
        ) {
            Text(stringResource(R.string.spotify_import_button))
        }
    }
}

@Composable
private fun RunningContent() {
    val done = SpotifyImport.progressDone
    val total = SpotifyImport.progressTotal

    SpotifyImport.runningName?.takeIf { it.isNotBlank() }?.let { name ->
        Text(text = name, style = typography().s.semiBold, color = colorPalette().text, maxLines = 2)
        Spacer(Modifier.height(8.dp))
    }
    Text(
        text = if (total > 0) stringResource(R.string.spotify_import_progress, done, total)
        else stringResource(R.string.spotify_import_reading),
        style = typography().xs,
        color = colorPalette().textSecondary
    )
    Spacer(Modifier.height(12.dp))
    if (total > 0)
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            color = colorPalette().text,
            trackColor = colorPalette().textDisabled
        )
    else
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = colorPalette().text,
            trackColor = colorPalette().textDisabled
        )

    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.spotify_import_background_note),
        style = typography().xs,
        color = colorPalette().textSecondary
    )
    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = SpotifyImport::closeDialog) {
            Text(stringResource(R.string.spotify_import_hide), color = colorPalette().text)
        }
    }
}

private fun clipboardText(context: Context): String? = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()
