package it.fast4x.riplay.extensions.ads

import android.content.Context
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.customtabs.YammboCustomTabs
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.themed.SmartMessage
import timber.log.Timber

enum class PremiumFeature {
    Like,
    CreatePlaylist,
    Lyrics,
    Download,
    Settings,
    SkipSong,
    AudioQuality;

    val displayName: String
        get() = when (this) {
            Like -> "dar me gusta"
            CreatePlaylist -> "crear playlists"
            Lyrics -> "ver las letras"
            Download -> "descargar canciones"
            Settings -> "configuración avanzada"
            SkipSong -> "saltar canciones ilimitadas"
            AudioQuality -> "calidad de audio alta"
        }
}

object PremiumGuard {

    fun isPremium(context: Context): Boolean {
        val result = YammboAuthManager(context).isSubscriptionActive()
        Timber.d("PremiumGuard.isPremium: $result (context=${context.javaClass.simpleName})")
        return result
    }

    fun checkFeature(context: Context, feature: PremiumFeature): Boolean {
        Timber.d("PremiumGuard.checkFeature: ${feature.name}")
        if (isPremium(context)) {
            Timber.d("PremiumGuard: user is premium, allowing ${feature.name}")
            return true
        }

        Timber.d("PremiumGuard: user is NOT premium, blocking ${feature.name}")
        SmartMessage(
            "Suscríbete para ${feature.displayName}",
            PopupType.Warning,
            context = context
        )
        return false
    }

    fun openPricing(context: Context) {
        val userId = YammboAuthManager(context).getUserId()
        val url = if (userId > 0)
            "https://music.yammbo.com/app-music/pricing?user_id=$userId"
        else
            "https://music.yammbo.com/app-music/pricing"
        YammboCustomTabs.open(context, url)
    }
}
