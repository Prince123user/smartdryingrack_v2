package com.example.smartdryingrack.data

import android.content.Context
import android.net.*
import com.example.smartdryingrack.data.hardware.rest.WifiTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WifiTransportTest {
    private fun transport(manager: ConnectivityManager): WifiTransport {
        val context = mock(Context::class.java)
        `when`(context.getSystemService(ConnectivityManager::class.java)).thenReturn(manager)
        return WifiTransport(context)
    }

    @Test fun localRackUsesWifiWithoutInternetEvenWhenCellularIsDefault() {
        val manager = mock(ConnectivityManager::class.java)
        val cellular = mock(Network::class.java)
        val wifi = mock(Network::class.java)
        `when`(manager.allNetworks).thenReturn(arrayOf(cellular, wifi))
        `when`(manager.activeNetwork).thenReturn(cellular)
        `when`(manager.getNetworkCapabilities(cellular)).thenReturn(mock(NetworkCapabilities::class.java))
        val capabilities = mock(NetworkCapabilities::class.java)
        `when`(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)
        assertFalse(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        `when`(manager.getNetworkCapabilities(wifi)).thenReturn(capabilities)
        val url = URL("http://192.168.1.50/api/status")
        val connection = mock(HttpURLConnection::class.java)
        `when`(wifi.openConnection(url)).thenReturn(connection)
        assertSame(connection, transport(manager).openConnection(url))
        verify(wifi).openConnection(url)
        verify(cellular, never()).openConnection(url)
        verify(manager, never()).bindProcessToNetwork(any())
    }

    @Test fun absentWifiFailsInsteadOfSendingLocalRackTrafficThroughCellular() {
        val manager = mock(ConnectivityManager::class.java)
        `when`(manager.allNetworks).thenReturn(emptyArray())
        val error = assertThrows(java.io.IOException::class.java) {
            transport(manager).openConnection(URL("http://192.168.1.50/api/status"))
        }
        assertTrue(error.message!!.contains("Wi-Fi"))
    }

    @Test fun observesWifiLossAndReturnAndUnregistersWhenCollectionStops() = runTest {
        val manager = mock(ConnectivityManager::class.java)
        val observed = mutableListOf<Unit>()
        val job = backgroundScope.launch { transport(manager).changes.collect { observed.add(it) } }
        runCurrent()
        val request = ArgumentCaptor.forClass(NetworkRequest::class.java)
        val callback = ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback::class.java)
        verify(manager).registerNetworkCallback(request.capture(), callback.capture())
        assertTrue(request.value.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        val network = mock(Network::class.java)
        callback.value.onAvailable(network); runCurrent()
        callback.value.onLost(network); runCurrent()
        callback.value.onAvailable(network); runCurrent()
        assertEquals(3, observed.size)
        job.cancel(); runCurrent()
        verify(manager).unregisterNetworkCallback(callback.value)
    }
}
