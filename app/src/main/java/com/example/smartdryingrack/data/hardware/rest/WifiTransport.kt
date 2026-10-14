package com.example.smartdryingrack.data.hardware.rest

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Local racks can use Wi-Fi without Internet, even when mobile data is Android's default route. */
@Singleton
class WifiTransport @Inject constructor(@ApplicationContext context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun openConnection(url: URL): HttpURLConnection {
        if (url.protocol == "https") return url.openConnection() as HttpURLConnection
        val wifi = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: throw IOException("Wi-Fi is unavailable. Reconnect to the rack's Wi-Fi network.")
        return wifi.openConnection(url) as HttpURLConnection
    }

    val changes = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(Unit) }
            override fun onLost(network: Network) { trySend(Unit) }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
}
