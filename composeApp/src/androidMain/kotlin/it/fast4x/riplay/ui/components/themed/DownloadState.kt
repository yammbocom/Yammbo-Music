package it.fast4x.riplay.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.yambo.music.R
import it.fast4x.riplay.data.Database
import kotlinx.coroutines.Dispatchers

/**
 * The icon for a "download these songs" button: the plain arrow until every one of [videoIds]
 * has a downloaded copy, then the checked one. It follows the database, so it turns checked by
 * itself as the queue finishes. Songs already local should be left out of [videoIds].
 */
@DrawableRes
@Composable
fun downloadIconFor(videoIds: List<String>): Int {
    val downloaded by remember { Database.downloadedMediaIds() }
        .collectAsState(initial = emptyList(), context = Dispatchers.IO)
    val allDownloaded = remember(videoIds, downloaded) {
        videoIds.isNotEmpty() && downloaded.toHashSet().containsAll(videoIds)
    }
    return if (allDownloaded) R.drawable.downloaded else R.drawable.download
}
