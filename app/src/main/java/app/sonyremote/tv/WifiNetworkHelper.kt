package app.sonyremote.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Finds the phone's Wi-Fi network, so LAN traffic is not sent over mobile data. */
class WifiNetworkHelper(context: Context) {
    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    fun wifiNetwork(): Network? = connectivity.allNetworks.firstOrNull { network ->
        connectivity.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun localIpv4(network: Network): Inet4Address? =
        connectivity.getLinkProperties(network)
            ?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull()
}
