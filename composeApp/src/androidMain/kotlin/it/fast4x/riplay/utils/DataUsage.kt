package it.fast4x.riplay.utils

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import androidx.annotation.WorkerThread
import timber.log.Timber
import java.util.Calendar
import java.util.Locale

/**
 * What this app has spent on the network. [sinceBoot] is set when the per-network history was
 * not available and the only figure is TrafficStats: every network together, since the phone
 * was last turned on.
 */
data class AppDataUsage(
    val mobileToday: Long = 0,
    val mobileLast7Days: Long = 0,
    val mobileLast30Days: Long = 0,
    val wifiLast30Days: Long = 0,
    val sinceBoot: Long? = null,
)

private const val DAY_MS = 24L * 60 * 60 * 1000

@WorkerThread
fun readAppDataUsage(context: Context): AppDataUsage {
    // Own-app history needs no special permission from Android 10 on; before that, or if a
    // vendor build refuses anyway, fall back to the one counter every version has.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val now = System.currentTimeMillis()
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return AppDataUsage(
                mobileToday = ownUsage(manager, ConnectivityManager.TYPE_MOBILE, startOfToday, now),
                mobileLast7Days = ownUsage(manager, ConnectivityManager.TYPE_MOBILE, now - 7 * DAY_MS, now),
                mobileLast30Days = ownUsage(manager, ConnectivityManager.TYPE_MOBILE, now - 30 * DAY_MS, now),
                wifiLast30Days = ownUsage(manager, ConnectivityManager.TYPE_WIFI, now - 30 * DAY_MS, now),
            )
        } catch (e: Exception) {
            // SecurityException on some builds, RemoteException if the service is unwell
            Timber.w("readAppDataUsage NetworkStatsManager unavailable: ${e.message}")
        }
    }
    val uid = Process.myUid()
    val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
    val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
    return AppDataUsage(sinceBoot = rx + tx)
}

@Suppress("DEPRECATION")
private fun ownUsage(manager: NetworkStatsManager, networkType: Int, start: Long, end: Long): Long {
    val myUid = Process.myUid()
    var total = 0L
    manager.querySummary(networkType, null, start, end).use { stats ->
        val bucket = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            if (bucket.uid == myUid) total += bucket.rxBytes + bucket.txBytes
        }
    }
    return total
}

/** "12.3 MB" / "1.2 GB", always one decimal, so the figures line up under each other. */
fun formatDataAmount(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.getDefault(), "%.1f GB", mb / 1024)
    else String.format(Locale.getDefault(), "%.1f MB", mb)
}
