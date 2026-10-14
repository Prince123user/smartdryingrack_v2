package com.example.smartdryingrack.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import com.example.smartdryingrack.core.designsystem.SmartDryingRackTheme
import com.example.smartdryingrack.data.repository.TemperaturePoint
import com.example.smartdryingrack.feature.history.HistoryScreen
import com.example.smartdryingrack.feature.history.HistoryUiState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.smartdryingrack.MainActivity
import com.example.smartdryingrack.SmartDryingRackApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SmartDryingRackApp::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String, heading: String = "Smart Drying Rack") {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNode(hasText(heading) and !hasClickAction()).assertIsDisplayed()
        println("$name header bounds: " + compose.onNode(hasText(heading) and !hasClickAction()).fetchSemanticsNode().boundsInRoot)
        // Draw the actual activity into a native Android canvas. PixelCopy callbacks are not
        // driven by Robolectric's paused main looper, so Compose captureToImage would time out.
        val image = compose.runOnIdle {
            val root = compose.activity.window.decorView
            check(root.width > 0 && root.height > 0)
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
        val output = File("build/outputs/ui-screenshots/$name.png")
        checkNotNull(output.parentFile).mkdirs()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        check(output.length() > 1000) { "Screenshot was not written." }
    }

    @Test fun appLaunchesAndEveryPrimaryNavigationRenders() {
        compose.onNodeWithText("Connect your ESP32 rack").assertExists()
        compose.onNodeWithContentDescription("Illustration of a motorized drying rack with hanging laundry").assertExists()
        capture("01-home")
        compose.onNodeWithText("Control").performClick()
        compose.onNodeWithText("Extend").assertExists()
        capture("02-control")
        compose.onNodeWithText("Alerts").performClick()
        compose.onNodeWithText("No new alerts").assertExists()
        capture("03-alerts")
        compose.onNode(hasContentDescription("History") and hasClickAction()).performClick()
        compose.onNodeWithText("Waiting for temperature history").assertExists()
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("Today").performClick()
        capture("04-history", "History")
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.onNodeWithText("No notifications yet").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Home").assertHasClickAction().performClick()
        compose.onNodeWithText("Connect your ESP32 rack").assertExists()
        compose.onNodeWithContentDescription("Notifications").performClick()
        capture("05-notifications", "Notifications")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("ESP32 rack").assertExists()
        capture("06-settings", "Settings")
        compose.onNode(hasText("Appearance") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Midnight cyan").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Device Settings") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Your rack").assertExists()
        capture("14-device-settings", "Device Settings")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Notification Settings") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Notification sound").assertExists()
        capture("15-notification-settings", "Notification Settings")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Automation") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Rain Sensor Sensitivity").assertExists()
        capture("16-automation-settings", "Automation")
        compose.onNodeWithContentDescription("Back").performClick()
    }
    @Test fun optionalOnboardingStillReachesHome() {
        compose.runOnUiThread {
            compose.activity.setContent {
                com.example.smartdryingrack.core.navigation.SmartDryingRackRoot(startDestination = "welcome")
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Get Started").fetchSemanticsNodes().isNotEmpty() }
        capture("10-introduction", "SMART\nDRYING RACK")
        compose.onNodeWithText("Get Started").performClick()
        compose.onNodeWithText("Smart Monitoring").assertIsDisplayed()
        compose.onNodeWithText("Illustrative readings").assertExists()
        compose.onNodeWithText("Home").assertDoesNotExist()
        capture("11-monitoring", "Smart Monitoring")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Rain Protection").assertIsDisplayed()
        capture("12-rain-protection", "Rain Protection")
        compose.onNodeWithText("Continue").performClick()
        capture("13-connect-device", "Connect Device")
        compose.onNodeWithText("Explore offline").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        compose.onNodeWithText("Connect your ESP32 rack").assertExists()
    }

    @Test fun tabsAndSettingsLoopRepeatedlyFromHome() {
        fun tab(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())
        fun assertTab(label: String) {
            tab(label).assertIsSelected()
            when (label) {
                "Home" -> compose.onNodeWithText("Connect your ESP32 rack").assertExists()
                "Control" -> compose.onNodeWithText("Extend").assertExists()
                "Alerts" -> compose.onNodeWithText("No new alerts").assertExists()
                "History" -> compose.onNodeWithText("Waiting for temperature history").assertExists()
            }
        }
        assertTab("Home")
        compose.onNodeWithText("Get Started").assertDoesNotExist()
        repeat(5) {
            listOf("Control", "Home", "Alerts", "Control", "Home", "History").forEach { label ->
                tab(label).performClick()
                assertTab(label)
            }
            compose.onNodeWithText("Week").performClick()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("ESP32 rack").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            assertTab("History")
            // Settings also exposes the tabs, including a direct return to History.
            compose.onNodeWithContentDescription("Settings").performClick()
            tab("History").performClick()
            assertTab("History")
            compose.onNodeWithContentDescription("Notifications").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            assertTab("History")
            tab("Home").performClick()
            assertTab("Home")
            tab("Control").performClick()
            assertTab("Control")
            compose.waitForIdle()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            assertTab("Home")
        }
        // Every ordered pair of primary destinations remains reachable.
        repeat(2) {
            listOf("Home", "Control", "Alerts", "History").forEach { source ->
                listOf("Home", "Control", "Alerts", "History").forEach { target ->
                    tab(source).performClick()
                    assertTab(source)
                    tab(target).performClick()
                    assertTab(target)
                }
            }
        }
    }

    @Test fun largeTextLightDashboardKeepsConnectionActionAccessible() {
        var connectTapped = false
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply { fontScale = 1.6f }
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.6f),
                    androidx.compose.ui.platform.LocalConfiguration provides configuration
                ) {
                    SmartDryingRackTheme(darkTheme = false) {
                        Surface {
                            Column {
                                Text("Smart Drying Rack")
                                com.example.smartdryingrack.feature.home.HomeScreen(
                                    com.example.smartdryingrack.feature.home.HomeUiState(),
                                    onConnect = { connectTapped = true })
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Connect rack").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { check(connectTapped) }
        compose.onNodeWithText("Retract rack").assertIsNotEnabled()
        capture("09-large-text-light-dashboard")
    }

    @Test fun adaptiveLauncherIconRendersArtwork() {
        val drawable = compose.activity.getDrawable(com.example.smartdryingrack.R.mipmap.ic_launcher)
        check(drawable is android.graphics.drawable.AdaptiveIconDrawable)
        checkNotNull(drawable.monochrome)
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 192, 192)
        drawable.draw(Canvas(bitmap))
        val output = File("build/outputs/ui-screenshots/08-launcher-icon.png")
        checkNotNull(output.parentFile).mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = mutableSetOf<Int>()
        for (x in 48 until 144) for (y in 48 until 144) colors.add(bitmap.getPixel(x, y))
        check(colors.size > 100) { "Icon foreground artwork did not render." }
    }

    @Test fun temperatureChartRendersWithExplicitTestFixture() {
        val now = System.currentTimeMillis()
        val fixture = listOf(TemperaturePoint(now - 600000, 30f), TemperaturePoint(now - 300000, 31f), TemperaturePoint(now, 32f))
        compose.runOnUiThread {
            compose.activity.setContent {
                SmartDryingRackTheme {
                    Surface {
                        Column {
                            Text("Smart Drying Rack")
                            Text("Render test · synthetic chart fixture")
                            HistoryScreen(HistoryUiState(temperatures = fixture))
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("32.0°C · latest reading").assertExists()
        capture("07-history-test-fixture")
    }
}
