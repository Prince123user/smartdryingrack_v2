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

    private fun fixture(scope: CoroutineScope, beforeCommand: (() -> Unit)? = null): Fixture {
        val server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("rack_device", 0).edit().clear().commit()
        val preferences = DevicePreferences(context)
        preferences.saveAddress("http://192.168.1.50:${server.port}")
        // Exercise real HTTP without needing a physical rack or changing address validation.
        val api = RackApi { url ->
            if (url.path == "/api/commands") beforeCommand?.invoke()
            URL("http://127.0.0.1:${server.port}${url.path}").openConnection() as HttpURLConnection
        }
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

    @Test fun droppedTcpConnectionRecoversWithoutConnectButton() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
            // Close an established connection while sending the response, as a Wi-Fi loss can do.
            f.server.enqueue(MockResponse().setBody(status).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            advanceTimeBy(2000); runCurrent()
            assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
            assertEquals(RackState.UNKNOWN, f.controller.observeRackState().value)
            f.server.enqueue(MockResponse().setBody(status))
            advanceTimeBy(5000); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
        }
    }

    @Test fun prolongedUnavailableDeviceKeepsRetryingUntilItReturns() = runTest {
        fixture(backgroundScope).use { f ->
            repeat(12) { f.server.enqueue(MockResponse().setResponseCode(503)) }
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            repeat(11) {
                advanceTimeBy(5000); runCurrent()
                assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
            }
            advanceTimeBy(5000); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
            assertEquals(13, f.server.requestCount)
        }
    }

    @Test fun networkReturnRestartsRecoveryImmediatelyWithoutWaitingForRetryDelay() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setResponseCode(503))
            f.controller.connect(); runCurrent()
            assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.resumeRequestedConnection(force = true); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test fun returningToAppRevivesFailedConnectionButPreservesExplicitDisconnect() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setResponseCode(503))
            f.controller.connect(); runCurrent()
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.resumeRequestedConnection(); runCurrent()
            assertEquals(ConnectionState.ONLINE, f.controller.observeConnection().value)
            f.controller.disconnect()
            f.controller.resumeRequestedConnection(force = true)
            f.controller.resumeRequestedConnection()
            advanceTimeBy(30000); runCurrent()
            assertEquals(ConnectionState.OFFLINE, f.controller.observeConnection().value)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test fun acknowledgementFromObsoleteSessionIsNotConfirmedOrReplayed() = runTest {
        lateinit var controller: WifiRackController
        fixture(backgroundScope, beforeCommand = {
            runBlocking { controller.resumeRequestedConnection(force = true) }
        }).use { f ->
            controller = f.controller
            f.server.enqueue(MockResponse().setBody(status))
            controller.connect(); runCurrent()
            f.server.enqueue(MockResponse().setBody("{\"accepted\":true}"))
            try {
                runBlocking { controller.retract() }
                fail("An acknowledgement across a connection change must not confirm the old session")
            } catch (e: java.io.IOException) {
                assertTrue(e.message!!.contains("Connection changed"))
            }
            assertEquals(2, f.server.requestCount)
            f.server.enqueue(MockResponse().setBody(status))
            runCurrent()
            assertEquals(ConnectionState.ONLINE, controller.observeConnection().value)
            assertEquals(listOf("/api/status", "/api/commands", "/api/status"), List(3) { f.server.takeRequest().path })
        }
    }

    @Test fun buzzerUiStateChangesOnlyAfterConfirmedDeviceResponse() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody(status.dropLast(1) + ",\"buzzerEnabled\":true}"))
            f.controller.connect(); runCurrent()
            assertEquals(true, f.controller.buzzerEnabled.value)
            f.server.enqueue(MockResponse().setBody("{\"accepted\":true,\"buzzerEnabled\":false}"))
            runBlocking { f.controller.setBuzzer(false) }
            assertEquals(false, f.controller.buzzerEnabled.value)
            f.server.enqueue(MockResponse().setBody("{\"accepted\":true,\"buzzerEnabled\":false}"))
            try { runBlocking { f.controller.setBuzzer(true) }; fail("Unconfirmed settings must fail") }
            catch (_: java.io.IOException) { }
            assertEquals(false, f.controller.buzzerEnabled.value)
            f.controller.disconnect()
            assertNull(f.controller.buzzerEnabled.value)
        }
    }

    @Test fun oldFirmwareDoesNotExposeAWorkingBuzzerSwitch() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.enqueue(MockResponse().setBody(status))
            f.controller.connect(); runCurrent()
            assertNull(f.controller.buzzerEnabled.value)
            try { runBlocking { f.controller.setBuzzer(true) }; fail("Unsupported firmware must not accept a setting") }
            catch (_: IllegalStateException) { }
            assertEquals(1, f.server.requestCount)
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
    @Test fun sensorFailureDoesNotPreventAnExplicitStopRequest() = runTest {
        fixture(backgroundScope).use { f ->
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = when (request.path) {
                    "/api/status" -> MockResponse().setResponseCode(503).setBody("{\"error\":\"SENSOR_FAILURE\"}")
                    "/api/commands" -> MockResponse().setBody("{\"accepted\":true}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            f.controller.connect(); runCurrent()
            assertEquals(ConnectionState.ERROR, f.controller.observeConnection().value)
            f.server.takeRequest()
            f.controller.stop()
            var request = f.server.takeRequest(1, TimeUnit.SECONDS)!!
            while (request.path != "/api/commands") request = f.server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/api/commands", request.path)
            assertEquals("STOP", org.json.JSONObject(request.body.readUtf8()).getString("action"))
            f.controller.disconnect()
            assertFalse(f.controller.canAttemptStop)
        }
    }

}
