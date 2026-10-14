package com.example.smartdryingrack.automation

import com.example.smartdryingrack.domain.model.CommandSource
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.RackAction
import com.example.smartdryingrack.domain.model.RackMode
import com.example.smartdryingrack.domain.model.RackSchedule
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.SensorData
import com.example.smartdryingrack.domain.model.Unavailable
import com.example.smartdryingrack.domain.model.computeAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AutomationTest {
    private val engine = DryingDecisionEngine()

    private fun input(
        state: RackState = RackState.EXTENDED,
        mode: RackMode = RackMode.AUTO,
        rain: Boolean = false,
        probability: Int? = null,
        threshold: Int = 60,
        schedule: Boolean? = null
    ) = DecisionInput(state, mode, rain, probability, threshold, schedule)

    @Test fun scenario1_rainWhileOutRetracts() {
        val d = engine.decide(input(rain = true))
        assertEquals(RackAction.RETRACT, d.action)
        assertEquals(CommandSource.RAIN, d.source)
    }

    @Test fun rainProtectionAppliesInManualMode() {
        assertEquals(RackAction.RETRACT, engine.decide(input(mode = RackMode.MANUAL, rain = true)).action)
    }

    @Test fun scenario2_highRainProbabilityBlocksScheduledExtension() {
        val d = engine.decide(input(state = RackState.RETRACTED, probability = 80, schedule = true))
        assertEquals(RackAction.WAIT, d.action)
    }

    @Test fun forecastRetractsRackThatIsOut() {
        assertEquals(RackAction.RETRACT, engine.decide(input(probability = 70)).action)
    }

    @Test fun scheduleExtendsWhenSafe() {
        val d = engine.decide(input(state = RackState.RETRACTED, probability = 10, schedule = true))
        assertEquals(RackAction.EXTEND, d.action)
    }

    @Test fun scheduleRetractsAtEndOfDay() {
        assertEquals(RackAction.RETRACT, engine.decide(input(schedule = false)).action)
    }

    @Test fun manualModeDoesNothingWithoutRain() {
        val d = engine.decide(input(state = RackState.RETRACTED, mode = RackMode.MANUAL, schedule = true))
        assertEquals(RackAction.NONE, d.action)
    }

    @Test fun errorStateSendsNoAutomaticCommands() {
        assertEquals(RackAction.NONE, engine.decide(input(state = RackState.ERROR, schedule = false)).action)
    }

    @Test fun sensitivityMapsToThresholds() {
        assertEquals(90, DryingDecisionEngine.sensitivityToThreshold(0f))
        assertEquals(60, DryingDecisionEngine.sensitivityToThreshold(0.5f))
        assertEquals(30, DryingDecisionEngine.sensitivityToThreshold(1f))
    }

    @Test fun scenario3_dryThreshold() {
        assertTrue(engine.isDry(90, 90))
        assertFalse(engine.isDry(89, 90))
    }

    @Test fun debouncerNeedsConsecutiveReadings() {
        val d = RainDebouncer(3)
        assertFalse(d.onReading(true))
        assertFalse(d.onReading(true))
        assertFalse(d.onReading(false))
        assertFalse(d.onReading(true))
        assertFalse(d.onReading(true))
        assertTrue(d.onReading(true))
        assertTrue(d.onReading(false))
        assertTrue(d.onReading(false))
        assertFalse(d.onReading(false))
    }

    @Test fun dryReadingCannotReuseLatchedRainConfirmationForRetraction() {
        val debouncer = RainDebouncer(3)
        repeat(3) { debouncer.onReading(true) }
        assertTrue(debouncer.permitsRainRetraction(true))
        // Hysteresis retains the old confirmation, but current dry telemetry must not move the rack.
        assertTrue(debouncer.onReading(false))
        assertFalse(debouncer.permitsRainRetraction(false))
    }

    @Test fun dryAutoWithScheduleOffNeverRequestsRetraction() {
        RackState.entries.forEach { state ->
            assertEquals(RackAction.NONE, engine.decide(input(state = state)).action)
        }
    }

    @Test fun sensorValidation() {
        val ok = SensorData(30f, 60f, false, 50, 0L)
        assertTrue(ok.isValid)
        assertFalse(ok.copy(temperature = 999f).isValid)
        assertFalse(ok.copy(humidity = -1f).isValid)
        assertFalse(ok.copy(clothesDryPercentage = 101).isValid)
        assertFalse(ok.copy(temperature = Float.NaN).isValid)
    }

    @Test fun schedulesAreOffUntilExplicitlyEnabled() {
        val schedule = RackSchedule()
        assertFalse(schedule.enabled)
        val monday = LocalDate.of(2026, 10, 5)
        assertNull(schedule.wantsRackOut(monday.atTime(7, 59)))
        assertNull(schedule.wantsRackOut(monday.atTime(18, 0)))
    }

    @Test fun scheduleWindow() {
        val s = RackSchedule(enabled = true)
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(false, s.wantsRackOut(monday.atTime(7, 59)))
        assertEquals(true, s.wantsRackOut(monday.atTime(8, 0)))
        assertEquals(false, s.wantsRackOut(monday.atTime(17, 0)))
        assertNull(s.wantsRackOut(LocalDate.of(2026, 10, 10).atTime(9, 0)))
        assertNull(s.copy(enabled = false).wantsRackOut(monday.atTime(9, 0)))
        assertNull(s.copy(extendTime = LocalTime.of(18, 0)).wantsRackOut(monday.atTime(9, 0)))
    }

    @Test fun availabilityRules() {
        val auto = computeAvailability(RackState.EXTENDED, RackMode.AUTO, ConnectionState.ONLINE)
        assertEquals(Unavailable.AUTO_MODE, auto.extend)
        assertNull(auto.stop)
        val manualOut = computeAvailability(RackState.EXTENDED, RackMode.MANUAL, ConnectionState.ONLINE)
        assertEquals(Unavailable.ALREADY_EXTENDED, manualOut.extend)
        assertNull(manualOut.retract)
        val moving = computeAvailability(RackState.RETRACTING, RackMode.MANUAL, ConnectionState.ONLINE)
        assertEquals(Unavailable.MOVING, moving.retract)
        val offline = computeAvailability(RackState.EXTENDED, RackMode.MANUAL, ConnectionState.OFFLINE)
        assertEquals(Unavailable.OFFLINE, offline.stop)
    }
    @Test fun unknownPositionBlocksManualMovementButAllowsStop() {
        val availability = computeAvailability(RackState.UNKNOWN, RackMode.MANUAL, ConnectionState.ONLINE)
        assertEquals(Unavailable.UNSAFE, availability.extend)
        assertEquals(Unavailable.UNSAFE, availability.retract)
        assertNull(availability.stop)
    }

    @Test fun reconnectRequiresThreeNewRainReadings() {
        val d = RainDebouncer(3)
        repeat(3) { d.onReading(true) }
        assertTrue(d.confirmed)
        d.reset()
        assertFalse(d.confirmed)
        assertFalse(d.onReading(true))
        assertFalse(d.onReading(true))
        assertTrue(d.onReading(true))
    }

}
