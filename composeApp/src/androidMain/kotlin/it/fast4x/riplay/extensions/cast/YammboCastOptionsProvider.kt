package it.fast4x.riplay.extensions.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Declared in the manifest; the Cast framework reads it the first time a CastContext is created.
 *
 * The receiver id is read once here, so switching it in Settings takes effect on the next app
 * start. That is on purpose: the framework caches the CastContext for the life of the process.
 */
class YammboCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastReceivers.current(context))
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setNotificationOptions(
                        NotificationOptions.Builder()
                            .setTargetActivityClassName("it.fast4x.riplay.MainActivity")
                            .build()
                    )
                    .build()
            )
            // Leaving the receiver running after the phone disconnects would keep the TV on our
            // page with nothing playing.
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
