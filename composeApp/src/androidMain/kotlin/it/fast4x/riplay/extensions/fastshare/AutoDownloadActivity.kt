package it.fast4x.riplay.extensions.fastshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Kept for the manifest entry and for any reminder notification still sitting in the tray from
 * before downloads moved in-app. A worker can queue downloads itself now, so there is nothing
 * to show: it queues the pending favorites and closes.
 */
class AutoDownloadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            downloadPendingFavoritesNow(applicationContext)
            finish()
        }
    }
}
