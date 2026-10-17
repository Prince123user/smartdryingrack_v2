package com.example.smartdryingrack.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.smartdryingrack.MainActivity
import com.example.smartdryingrack.SmartDryingRackApp
import com.example.smartdryingrack.core.designsystem.SmartDryingRackTheme
import com.example.smartdryingrack.data.repository.*
import com.example.smartdryingrack.domain.model.*
import com.example.smartdryingrack.feature.lighting.*
import com.example.smartdryingrack.lighting.lightingFixture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SmartDryingRackApp::class, qualifiers = "w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HouseLightsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Actions : LightingActions {
        val powers = mutableListOf<Pair<LightingZone, Boolean>>()
        val brightnesses = mutableListOf<Pair<LightingZone, Int>>()
        override fun refresh() {}
        override fun power(zone: LightingZone, enabled: Boolean) { powers += zone to enabled }
        override fun brightness(zone: LightingZone, percent: Int) { brightnesses += zone to percent }
        override fun retry(zone: LightingZone) {}
        override fun discard(zone: LightingZone) {}
    }
    private fun fixture(): HouseLightingState {
        val device = lightingFixture()
        return HouseLightingState(LightingConnection.READY, device.zones.mapValues { ZoneLightingState(confirmed = it.value) },
            device.indicator, device.deviceSession, device.version)
    }
    private fun show(state: HouseLightingState, actions: Actions, largeText: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.setContent {
                SmartDryingRackTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f)) {
                        Column {
                            Text("House Lights · simulated test data", color = com.example.smartdryingrack.core.designsystem.AppColors.TextPrimary)
                            HouseLightsScreen(state, actions)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val file = File("build/outputs/ui-screenshots/$name.png")
        file.parentFile!!.mkdirs(); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 1000)
    }
    @Test fun allZonesScrollOnSmallScreenAndStatusHasNoManualControls() {
        show(fixture(), Actions())
        compose.onNodeWithText("Automatic RGB · ESP32 controlled · read-only").assertExists()
        compose.onNodeWithText("Simulated normal operation").assertExists()
        // Exactly six manual switches and six brightness sliders; the seventh indicator adds neither.
        compose.onAllNodes(isToggleable()).assertCountEquals(6)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(6)
        screenshot("17-house-lights-simulated")
        LightingZone.entries.forEach {
            compose.onNodeWithContentDescription("${it.label} power").performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithContentDescription("${it.label} brightness").performScrollTo().assertIsDisplayed()
        }
        screenshot("18-house-lights-balcony-simulated")
    }
    @Test fun ordinaryLightsHaveNoColorControlsAndStatusIsReadOnly() {
        show(fixture(), Actions())
        LightingZone.entries.forEach {
            compose.onNodeWithContentDescription("${it.label} color").assertDoesNotExist()
        }
        compose.onNodeWithText("Apply color").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).assertCountEquals(6)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(6)
    }
    @Test fun powerAndBrightnessTargetTheirLabeledZone() {
        val actions = Actions(); show(fixture(), actions)
        compose.onNodeWithContentDescription("CR power").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Balcony brightness").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertEquals(listOf(LightingZone.CR to false), actions.powers)
        assertEquals(listOf(LightingZone.BALCONY to 0), actions.brightnesses)
    }
    @Test fun unknownStateDisablesAllControlsAndDoesNotFabricateColor() {
        show(HouseLightingState(connection = LightingConnection.UNSUPPORTED), Actions())
        compose.onNodeWithText("Lighting-enabled firmware required").assertExists()
        compose.onNodeWithText("Device state and meaning not reported").assertExists()
        LightingZone.entries.forEach {
            compose.onNodeWithContentDescription("${it.label} power").assertIsNotEnabled()
            compose.onNodeWithContentDescription("${it.label} brightness").assertIsNotEnabled()
        }
        compose.onAllNodesWithText("Power and output not reported").assertCountEquals(6)
    }
    @Test fun zeroBrightnessAndPendingFeedbackRemainReadableAtLargeFont() {
        val state = fixture()
        val actual = state.zones.getValue(LightingZone.FRONT).confirmed!!
        show(state.copy(zones = state.zones + (LightingZone.FRONT to ZoneLightingState(
            confirmed = actual.copy(settings = actual.settings.copy(brightness = 0)),
            pending = PendingLightChange(actual.settings.copy(brightness = 0), "pending", 0, LightCommandPhase.CONFIRMING)
        ))), Actions(), largeText = true)
        compose.onNodeWithText("On · 0% brightness — no visible output").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accepted · waiting for confirmed device state").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Front power").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Balcony power").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("20-house-lights-large-text-simulated")
    }
    @Test fun unreachableRackShowsConnectionActionWithoutClaimingNonexistentReadingsAreStale() {
        var opened = false
        compose.runOnUiThread {
            compose.activity.setContent {
                SmartDryingRackTheme {
                    HouseLightsScreen(HouseLightingState(connection = LightingConnection.ERROR,
                        error = "The ESP32 did not respond at the configured address.", problem = LightingProblem.UNREACHABLE),
                        Actions(), onConnectionSettings = { opened = true })
                }
            }
        }
        compose.onNodeWithText("Rack unreachable · retrying automatically").assertExists()
        compose.onNodeWithText("Last confirmed readings may be stale.").assertDoesNotExist()
        compose.onNodeWithText("Connection settings").performClick()
        assertTrue(opened)
        compose.onNodeWithContentDescription("Front power").assertIsNotEnabled()
    }

    @Test fun failedPwmOutputIsDisabledWhileOtherZonesStayAvailable() {
        val state = fixture()
        val front = state.zones.getValue(LightingZone.FRONT)
        show(state.copy(zones = state.zones + (LightingZone.FRONT to front.copy(
            confirmed = front.confirmed!!.copy(available = false)))), Actions())
        compose.onNodeWithContentDescription("Front power").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Front brightness").assertIsNotEnabled()
        compose.onNodeWithText("PWM output unavailable").assertExists()
        compose.onNodeWithContentDescription("Left side power").assertIsEnabled()
    }

}
