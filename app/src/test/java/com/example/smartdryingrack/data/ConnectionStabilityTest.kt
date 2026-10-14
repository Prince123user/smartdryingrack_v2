package com.example.smartdryingrack.data

import com.example.smartdryingrack.data.hardware.rest.*
import com.example.smartdryingrack.data.repository.DevicePreferences
import com.example.smartdryingrack.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ConnectionStabilityTest {
    private val status = """{"state":"RETRACTED","firmwareVersion":"1.2.0","sensors":{"temperature":29.5,"humidity":64,"rainDetected":false}}"""

    private class Fixture(val server: MockWebServer, val controller: WifiRackController) : AutoCloseable {
        override fun close() { runBlocking { controller.disconnect() }; server.shutdown() }
    }

    private fun fixture(scope: CoroutineScope): Fixture {
        val server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("rack_device", 0).edit().clear().commit()
        val preferences = DevicePreferences(context)
        preferences.saveAddress("http://192.168.1.50:${server.port}")
        // Exercise real HTTP without needing a physical rack or changing address validation.
        val api = RackApi { url -> URL("http://127.0.0.1:${server.port}${url.path}").openConnection() as HttpURLConnection }
        return Fixture(server, WifiRackController(preferences, api, scope))
    }

    @Test fun recoversAutomaticallyAcrossTwentyConnectionDrops() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
            repeat(20) {
                f.server.enqueue(MockResponse().setResponseCode(503))
                advanceTimeBy(2000); runCurrent()
                assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
                assertEquals(RackState.UNKNOWN, f.controller.observeRackState().value)
                // Retained readings are historical; connection state must not stay online.
                assertNotNull(f.controller.observeSensorData().value)
                f.server.enqueue(MockResponse().setBody(status))
                advanceTimeBy(5000); runCurrent()
                assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
                assertEquals(RackState.RETRACTED, f.controller.observeRackState().value)
            }
            assertEquals(41, f.server.requestCount)
        }
    }

    @Test fun malformedTelemetryNeverMarksOnlineAndLaterValidTelemetryRecovers() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody("{}"))
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
            assertNull(f.controller.observeSensorData().value)
            advanceTimeBy(5000); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
        }
    }

    @Test fun slowTelemetryTimesOutAndRecoversWithoutManualReconnect() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody(status).setBodyDelay(3, TimeUnit.SECONDS))
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
            advanceTimeBy(5000); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test fun explicitDisconnectCancelsRetriesAndClearsReadings() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            f.controller.disconnect()
            advanceTimeBy(30000); runCurrent()
            assertEquals(ConnectionState.OFFLINE, f.controller.observeConnection().value)
            assertEquals(RackState.UNKNOWN, f.controller.observeRackState().value)
            assertNull(f.controller.observeSensorData().value)
            assertNull(f.controller.firmware.value)
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test fun repeatedConnectDoesNotCreateDuplicatePollingLoops() = runTest {
        fixture(backgroundScope).use { f ->
            repeat(10) { f.controller.connect() }
            f.server.enqueue(MockResponse().setBody(status))
            runCurrent()
            assertEquals(1, f.server.requestCount)
            f.server.enqueue(MockResponse().setBody(status))
            advanceTimeBy(2000); runCurrent()
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test fun failedMotorCommandIsNotRetriedAndTelemetryCanRestoreConnection() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            f.server.enqueue(MockResponse().setResponseCode(503))
            // Keep virtual polling time fixed while the production IO dispatcher sends the command.
            try { runBlocking { f.controller.retract() }; fail("A rejected motor command must fail") } catch (_: java.io.IOException) { }
            assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
            assertEquals(2, f.server.requestCount)
            f.server.enqueue(MockResponse().setBody(status))
            advanceTimeBy(2000); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
            val paths = List(3) { f.server.takeRequest().path }
            assertEquals(listOf("/api/status", "/api/commands", "/api/status"), paths)
        }
    }
}
