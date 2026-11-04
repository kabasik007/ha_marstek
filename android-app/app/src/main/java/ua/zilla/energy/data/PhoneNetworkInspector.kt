package ua.zilla.energy.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Local Wi-Fi availability and Android's *opinion* about internet validation. */
data class PhoneLanState(
    val hasWifiLan: Boolean = false,
    val internetValidated: Boolean? = null,
    val checkedAtMs: Long = System.currentTimeMillis()
)

object PhoneNetworkInspector {
    fun inspect(context: Context): PhoneLanState {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = cm.allNetworks.firstNotNullOfOrNull { network ->
            val capabilities = cm.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) capabilities
            else null
        }
        return PhoneLanState(
            hasWifiLan = wifi != null,
            internetValidated = wifi?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
        )
    }
}
