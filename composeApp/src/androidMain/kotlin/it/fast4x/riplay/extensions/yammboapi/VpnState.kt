package it.fast4x.riplay.extensions.yammboapi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import it.fast4x.riplay.utils.appContext

/**
 * Whether this phone is behind a VPN, and which country its SIM belongs to.
 *
 * A country offer can only be given to the country the person is actually in, and through a VPN
 * the server sees the exit node instead. Android tells an app when a VPN transport is up, which
 * is the one reliable signal either side has, so the phone reports it and the server declines to
 * hand the offer out until it is off. Nothing here ever stops playback: a VPN is a perfectly
 * ordinary thing to use, subscribers included.
 */
object VpnState {

    fun isActive(): Boolean = runCatching {
        val manager = appContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrDefault(false)

    /**
     * SIM countries where the free-access offer exists, so the notice is only ever shown to
     * someone it can actually help. Everyone else keeps their VPN in peace.
     */
    val OFFER_SIM_COUNTRIES = setOf("ni")

    /** The header the backend reads to decide whether to hand out a country offer. */
    fun header(): String = if (isActive()) "1" else "0"

    /**
     * The SIM's country, lowercase, or empty. Used only to decide whether the "turn the VPN off"
     * notice is worth showing: nobody outside the offer's countries needs to read it.
     */
    fun simCountry(): String = runCatching {
        val telephony = appContext().getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        (telephony?.simCountryIso ?: telephony?.networkCountryIso).orEmpty().lowercase()
    }.getOrDefault("")
}
