package com.example.smartdryingrack.data.hardware.rest

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.LinkProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

class RackWifiUnavailableException : IOException("Wi-Fi is unavailable. Reconnect to the rack’s Wi-Fi network.")

/** Local racks can use Wi-Fi without Internet, even when mobile data is Android's default route. */
@Singleton
class WifiTransport @Inject constructor(@ApplicationContext context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun openConnection(url: URL): HttpURLConnection {
        if (url.protocol == "https") return url.openConnection() as HttpURLConnection
        val networks = connectivity.allNetworks.filter { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        // Android can expose an old/secondary Wi-Fi network alongside the active one.
        // Prefer the active Wi-Fi rather than depending on allNetworks enumeration order.
        val active = connectivity.activeNetwork
        val wifi = networks.firstOrNull { it == active } ?: networks.firstOrNull()
            ?: throw RackWifiUnavailableException()
        return wifi.openConnection(url) as HttpURLConnection
    }

    val changes = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(Unit) }
            override fun onLost(network: Network) { trySend(Unit) }
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) { trySend(Unit) }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
}
