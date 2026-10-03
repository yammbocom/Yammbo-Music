package it.fast4x.riplay.service

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import timber.log.Timber

/**
 * "Hey Google, play X on Yammbo Music" on the phone: the Assistant opens the app with
 * MEDIA_PLAY_FROM_SEARCH. The player service may not be running yet (cold start), so this binds
 * to it on its own, hands the request over once the binder is there, and unbinds when done; the
 * activity's own binding and its start of the service are left untouched.
 */
@UnstableApi
object VoiceSearchIntent {

    private const val HANDLED_EXTRA = "it.fast4x.riplay.voiceSearchHandled"

    /** True when [intent] was a voice play request (handled now or earlier). */
    fun handle(context: Context, intent: Intent?): Boolean {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return false
        // The same intent object comes back when the activity is recreated
        if (intent.getBooleanExtra(HANDLED_EXTRA, false)) return true
        // Reopened from Recents: the system hands back the original voice intent, which is
        // not a new request (it would replace whatever the user is listening to now)
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return true
        intent.putExtra(HANDLED_EXTRA, true)

        val query = intent.getStringExtra(SearchManager.QUERY)
        val extras = intent.extras?.let { Bundle(it) }

        // Later recreations of the activity must not see the request again
        if (context is Activity && context.intent === intent)
            context.intent = Intent(context, context.javaClass).setAction(Intent.ACTION_MAIN)
        Timber.d("VoiceSearchIntent query '$query' focus ${extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)}")

        val app = context.applicationContext
        val connection = object : ServiceConnection {
            private var used = false

            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (used) return
                used = true
                val binder = service as? PlayerService.Binder
                if (binder == null) {
                    unbind()
                    return
                }
                SessionPlayback.playFromSearch(binder, query, extras) { unbind() }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit

            fun unbind() {
                runCatching { app.unbindService(this) }
            }
        }

        val bound = runCatching {
            app.bindService(Intent(app, PlayerService::class.java), connection, Context.BIND_AUTO_CREATE)
        }.onFailure { Timber.e("VoiceSearchIntent bindService failed ${it.message}") }
            .getOrDefault(false)
        if (!bound) runCatching { app.unbindService(connection) }
        return true
    }
}
