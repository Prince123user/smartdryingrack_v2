package com.example.smartdryingrack.automation

import com.example.smartdryingrack.data.repository.EventLog
import com.example.smartdryingrack.data.repository.TemperatureHistory
import com.example.smartdryingrack.data.repository.RackRepository
import com.example.smartdryingrack.data.repository.ScheduleRepository
import com.example.smartdryingrack.data.repository.SettingsRepository
import com.example.smartdryingrack.domain.model.ConnectionState
import com.example.smartdryingrack.domain.model.EventType
import com.example.smartdryingrack.domain.model.RackState
import com.example.smartdryingrack.domain.model.RackAction
import com.example.smartdryingrack.domain.model.SensorData
import com.example.smartdryingrack.domain.repository.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Runs the automation loop for the whole app process: sensors in, rack commands out. */
@Singleton
class AutomationManager @Inject constructor(
    private val rack: RackRepository,
    private val settings: SettingsRepository,
    private val schedules: ScheduleRepository,
    private val weather: WeatherRepository,
    private val temperatureHistory: TemperatureHistory,
    private val events: EventLog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = DryingDecisionEngine()
    private val debouncer = RainDebouncer(required = 3)

    private var started = false
    private var rainActive = false
    private var dryNotified = false
    private var sensorInvalidReported = false
    private var lastWaitReason: String? = null

    fun start() {
        if (started) return
        started = true
        scope.launch { rack.connect() }
        scope.launch {
            var previous = ConnectionState.OFFLINE
            rack.connection.collect { now ->
                if (now == ConnectionState.ONLINE && previous != ConnectionState.ONLINE) {
                    events.record(EventType.DEVICE_CONNECTED)
                }
                if (previous == ConnectionState.ONLINE && now != ConnectionState.ONLINE) {
                    events.record(EventType.DEVICE_DISCONNECTED)
                }
                previous = now
            }
        }
        scope.launch {
            var lastTimestamp: Long? = null
            while (true) {
                val data = rack.sensorData.value
                if (rack.connection.value == ConnectionState.ONLINE && data != null &&
                    System.currentTimeMillis() - data.timestamp in 0..6000) {
                    onSensor(data, data.timestamp != lastTimestamp)
                    lastTimestamp = data.timestamp
                } else {
                    debouncer.reset()
                    rainActive = false
                    rack.setRainConfirmed(false)
                    lastTimestamp = null
                }
                delay(1000)
            }
        }
    }

    private suspend fun onSensor(data: SensorData, newReading: Boolean) {
        if (!data.isValid) {
            if (!sensorInvalidReported) {
                sensorInvalidReported = true
                events.record(EventType.SENSOR_INVALID)
            }
            return
        }
        sensorInvalidReported = false
        if (newReading) temperatureHistory.record(data.timestamp, data.temperature)

        val s = settings.settings.value
        if (newReading) debouncer.onReading(data.rainDetected)
        val rainConfirmed = debouncer.permitsRainRetraction(data.rainDetected)
        if (rainConfirmed && !rainActive) events.record(EventType.RAIN_DETECTED)
        rainActive = rainConfirmed
        rack.setRainConfirmed(rainConfirmed)

        val dry = data.clothesDryPercentage?.let { engine.isDry(it, s.dryThresholdPercent) } ?: false
        if (dry && !dryNotified && s.clothesDryAlert) {
            dryNotified = true
            events.record(EventType.CLOTHES_DRY)
        } else if (!dry) {
            dryNotified = false
        }

        val nowMs = System.currentTimeMillis()
        val probability = weather.forecast.value
            ?.takeIf { s.useWeather && !it.isSimulated && it.forecastTime - nowMs in 0..FORECAST_WINDOW_MS }
            ?.rainProbability

        val decision = engine.decide(
            DecisionInput(
                // Accepted intent matters for rain protection even before movement telemetry arrives.
                rackState = when (rack.pendingMovement.value) {
                    RackState.EXTENDED -> RackState.EXTENDING
                    RackState.RETRACTED -> RackState.RETRACTING
                    else -> rack.rackState.value
                },
                mode = rack.mode.value,
                rainConfirmed = rainConfirmed,
                forecastRainProbability = probability,
                rainThresholdPercent = DryingDecisionEngine.sensitivityToThreshold(s.rainSensitivity),
                scheduleWantsRackOut = schedules.schedule.value.wantsRackOut(LocalDateTime.now())
            )
        )

        when (decision.action) {
            RackAction.EXTEND, RackAction.RETRACT -> rack.send(decision.action, decision.source)
            RackAction.WAIT ->
                if (decision.reason != lastWaitReason) events.record(EventType.FORECAST_SKIP)
            else -> Unit
        }
        lastWaitReason = if (decision.action == RackAction.WAIT) decision.reason else null
    }

    private companion object {
        const val FORECAST_WINDOW_MS = 3 * 60 * 60 * 1000L
    }
}
