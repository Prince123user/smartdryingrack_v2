package com.example.smartdryingrack.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.smartdryingrack.MainActivity
import com.example.smartdryingrack.SmartDryingRackApp
import com.example.smartdryingrack.core.designsystem.*
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.feature.device.DeviceConnectionContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SmartDryingRackApp::class, qualifiers = "w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectionRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val connections = mutableListOf<String>()
    private var disconnected = false
    private var wifiOpened = false
    @Test fun automaticBuzzerUsesReportedPolicyAndCannotBeMutedByTheManualSwitch() {
        val actions = org.mockito.Mockito.mock(com.example.smartdryingrack.feature.control.ControlActions::class.java)
        compose.runOnUiThread {
            compose.activity.setContent {
                SmartDryingRackTheme {
                    com.example.smartdryingrack.feature.control.ControlScreen(
                        com.example.smartdryingrack.feature.control.ControlUiState(
                            connection = ConnectionState.ONLINE,
                            mode = com.example.smartdryingrack.domain.model.RackMode.AUTO,
                            buzzerEnabled = false, buzzerAutoRequired = true), actions)
                }
            }
        }
        compose.onNodeWithContentDescription("Buzzer").performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("AUTO · movement sound always on. Switch to MANUAL to choose sound.").assertExists()
        org.mockito.Mockito.verifyNoInteractions(actions)
    }
    private fun show(state: ConnectionState, largeText: Boolean = false, lastReading: Long? = null) {
        compose.runOnUiThread {
            compose.activity.setContent {
                SmartDryingRackTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                        ScreenColumn {
                            DeviceConnectionContent(state, "http://192.168.1.50", lastReading, "1.1.4",
                                if (state == ConnectionState.ERROR) "The ESP32 responded, but its status service is unavailable." else null,
                                null, if (state == ConnectionState.ERROR) 2 else 0,
                                { connections += it }, { disconnected = true }, { wifiOpened = true })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file = java.io.File("build/reports/connectivity-ux/${state.name.lowercase()}.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun failedConnectionOffersRetryWifiSettingsAndExplicitPauseOnSmallScreen() {
        show(ConnectionState.ERROR, largeText = true, lastReading = System.currentTimeMillis() - 60000)
        compose.onNodeWithText("Connection needs attention").assertExists()
        compose.onNodeWithText("Last reading (stale):", substring = true).assertExists()
        compose.onNodeWithText("Retry now").performScrollTo().performClick()
        assertEquals(listOf("http://192.168.1.50"), connections)
        compose.onNodeWithText("Open Wi-Fi settings").performScrollTo().performClick()
        assertTrue(wifiOpened)
        compose.onNodeWithText("Disconnect & pause retries").performScrollTo().performClick()
        assertTrue(disconnected)
    }
    @Test fun connectingDisablesDuplicateRequestsButCanStillBeCancelled() {
        show(ConnectionState.CONNECTING)
        compose.onNodeWithText("Connecting…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Disconnect & pause retries").performScrollTo().performClick()
        assertTrue(disconnected)
    }
    @Test fun editedAddressUsesExplicitSaveAndConnectAction() {
        show(ConnectionState.OFFLINE)
        compose.onNodeWithText("Rack address").performScrollTo().performTextReplacement("http://192.168.1.92")
        compose.onNodeWithText("Save address & connect").performScrollTo().performClick()
        assertEquals(listOf("http://192.168.1.92"), connections)
        compose.onNodeWithText("Disconnect & pause retries").assertDoesNotExist()
    }
    @Test fun onlineStateSeparatesConfirmedConnectionFromDeviceDetails() {
        show(ConnectionState.ONLINE, lastReading = System.currentTimeMillis())
        compose.onNodeWithText("Rack connected").assertExists()
        compose.onNodeWithText("Check connection").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Device details & troubleshooting").performScrollTo().performClick()
        compose.onNodeWithText("Firmware: 1.1.4", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Last reading (stale):", substring = true).assertDoesNotExist()
    }
    @Test fun lostConnectionCannotPresentAnAcceptedMovementAsCurrentRetraction() {
        var opened = false
        val actions = org.mockito.Mockito.mock(com.example.smartdryingrack.feature.control.ControlActions::class.java)
        compose.runOnUiThread {
            compose.activity.setContent {
                SmartDryingRackTheme {
                    com.example.smartdryingrack.feature.control.ControlScreen(
                        com.example.smartdryingrack.feature.control.ControlUiState(
                            connection = ConnectionState.ERROR,
                            rackState = com.example.smartdryingrack.domain.model.RackState.RETRACTING,
                            pendingMovement = com.example.smartdryingrack.domain.model.RackState.RETRACTED),
                        actions, onConnectionSettings = { opened = true })
                }
            }
        }
        compose.onNodeWithText("Connection lost · movement outcome unknown").assertExists()
        compose.onNodeWithText("Connection settings").performClick()
        assertTrue(opened)
        compose.onNodeWithText("Movement accepted. Waiting for updated device state.").assertDoesNotExist()
        compose.onNodeWithText("RETRACTING…").assertDoesNotExist()
        compose.onNodeWithText("Position unknown").assertExists()
        compose.onNodeWithText("Extend", useUnmergedTree = true).performScrollTo().assertExists()
        org.mockito.Mockito.verifyNoInteractions(actions)
    }
}
