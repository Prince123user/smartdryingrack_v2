package com.example.smartdryingrack.data.repository

import com.example.smartdryingrack.data.hardware.rest.LightingGateway
import com.example.smartdryingrack.data.hardware.rest.RackHttpException
import com.example.smartdryingrack.data.hardware.rest.RackWifiUnavailableException
import java.io.IOException
import com.example.smartdryingrack.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class LightingConnection { DISCONNECTED, LOADING, READY, ERROR, UNSUPPORTED }
enum class LightingProblem { WIFI_UNAVAILABLE, UNREACHABLE, HTTP_ERROR, INVALID_STATE }
enum class LightCommandPhase { DEBOUNCING, SENDING, CONFIRMING, FAILED }
data class PendingLightChange(val settings: LightSettings, val requestId: String, val baseRevision: Long,
                              val phase: LightCommandPhase, val requiredRevision: Long? = null)
data class ZoneLightingState(val confirmed: ConfirmedZone? = null, val pending: PendingLightChange? = null,
                             val error: String? = null) {
    val selected: LightSettings? get() = pending?.settings ?: confirmed?.settings
    val busy: Boolean get() = pending?.phase in setOf(LightCommandPhase.SENDING, LightCommandPhase.CONFIRMING)
}
data class HouseLightingState(
    val connection: LightingConnection = LightingConnection.DISCONNECTED,
    val zones: Map<LightingZone, ZoneLightingState> = LightingZone.entries.associateWith { ZoneLightingState() },
    val indicator: SystemStatusLight? = null,
    val deviceSession: String? = null,
    val version: Long? = null,
    val error: String? = null,
    val lastSyncedAt: Long? = null,
    val problem: LightingProblem? = null
)

/** Owned by the screen ViewModel. Calls/state transitions are confined to its UI coroutine scope.
 * Commands are absolute per-zone states, never automatically replayed; only a GET confirms output.
 */
class LightingRepository(private val api: LightingGateway, private val scope: CoroutineScope,
                         private val now: () -> Long = System::currentTimeMillis) {
    private val _state = MutableStateFlow(HouseLightingState())
    val state = _state.asStateFlow()
    private var address: String? = null
    private var generation = 0L
    private var polling: Job? = null
    private val commands = mutableMapOf<LightingZone, Job>()
    private val network = Mutex() // Bound lighting traffic; do not bind or serialize the rack/motor client.
    private val retiredBoots = mutableSetOf<String>()

    fun open(deviceAddress: String?) {
        generation++
        polling?.cancel()
        commands.values.forEach { it.cancel() }
        commands.clear()
        val changedDevice = address != null && deviceAddress != null && address != deviceAddress
        if (changedDevice) {
            retiredBoots.clear()
            _state.value = HouseLightingState()
        }
        if (deviceAddress != null) address = deviceAddress
        failPending("Connection changed. The command outcome is unknown; refresh and review before retrying.")
        if (deviceAddress == null) {
            _state.update { it.copy(connection = LightingConnection.DISCONNECTED, error = null, problem = null) }
            return
        }
        _state.update { it.copy(connection = LightingConnection.LOADING, error = null, problem = null) }
        val epoch = generation
        polling = scope.launch {
            while (isActive && epoch == generation) {
                fetch(epoch, deviceAddress)
                if (_state.value.connection == LightingConnection.UNSUPPORTED) break
                delay(if (_state.value.connection == LightingConnection.READY) 2000 else 5000)
            }
        }
    }

    fun refresh() { if (_state.value.connection != LightingConnection.DISCONNECTED) open(address) }
    fun stop() = open(null)

    private fun zone(zone: LightingZone, change: (ZoneLightingState) -> ZoneLightingState) {
        _state.update { state -> state.copy(zones = state.zones + (zone to change(state.zones.getValue(zone)))) }
    }
    private fun failPending(message: String) {
        LightingZone.entries.forEach { id -> zone(id) { current ->
            if (current.pending == null) current else current.copy(
                pending = current.pending.copy(phase = LightCommandPhase.FAILED),
                error = if (current.pending.phase == LightCommandPhase.DEBOUNCING)
                    "Unsent selection was cancelled. Review device state before retrying." else message)
        } }
    }
    private fun current(epoch: Long, id: LightingZone, requestId: String): Boolean =
        generation == epoch && _state.value.zones.getValue(id).pending?.let {
            it.requestId == requestId && it.phase != LightCommandPhase.FAILED
        } == true

    private suspend fun fetch(epoch: Long, target: String) {
        try {
            val snapshot = network.withLock { api.fetch(target) }
            if (epoch != generation || snapshot.deviceSession in retiredBoots) {
                return
            }
            val old = _state.value
            val rebooted = old.deviceSession != null && snapshot.deviceSession != old.deviceSession
            if (!rebooted && old.version != null && snapshot.version < old.version) {
                return
            }
            if (!rebooted && old.deviceSession != null) {
                // A newer global version (e.g. an indicator change) must not roll a zone back.
                old.zones.forEach { (id, ui) ->
                    ui.confirmed?.let { previous ->
                        val next = snapshot.zones.getValue(id)
                        require(next.revision >= previous.revision &&
                            (next.revision != previous.revision || next == previous)) {
                            "Inconsistent ${id.label} state: its revision regressed or changed without a new revision."
                        }
                    }
                }
            }
            if (rebooted) {
                retiredBoots += old.deviceSession!!
                commands.values.forEach { it.cancel() }
                commands.clear()
                failPending("The device restarted. Review its actual state before retrying.")
            }
            _state.update { state ->
                state.copy(connection = LightingConnection.READY, indicator = snapshot.indicator,
                    deviceSession = snapshot.deviceSession, version = snapshot.version, error = null, problem = null, lastSyncedAt = now(),
                    zones = state.zones.mapValues { (id, ui) ->
                        val actual = snapshot.zones.getValue(id)
                        val pending = ui.pending
                        val confirmedRequest = !rebooted && pending != null && actual.lastRequestId == pending.requestId &&
                            actual.revision >= (pending.requiredRevision ?: (pending.baseRevision + 1)) && actual.settings == pending.settings
                        ui.copy(confirmed = actual, pending = if (confirmedRequest) null else pending,
                            error = if (confirmedRequest) null else ui.error)
                    })
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (epoch != generation) return
            val unsupported = e is RackHttpException && e.statusCode in setOf(404, 501)
            if (unsupported) failPending("Lighting-enabled firmware is required.")
            val problem = when (e) {
                is RackWifiUnavailableException -> LightingProblem.WIFI_UNAVAILABLE
                is RackHttpException -> LightingProblem.HTTP_ERROR
                is IOException -> LightingProblem.UNREACHABLE
                else -> LightingProblem.INVALID_STATE
            }
            val message = when {
                unsupported -> "The ESP32 replied, but its firmware has no lighting API. Install firmware supporting the ordinary-LED lighting API (schema 2). Refreshing alone cannot add firmware support."
                problem == LightingProblem.WIFI_UNAVAILABLE -> "Connect your phone to the rack’s Wi-Fi network. Lighting state will retry automatically when Wi-Fi returns."
                problem == LightingProblem.UNREACHABLE -> "The ESP32 did not respond at the configured address. Check its power and current IP in Connection settings, and keep your phone on the same Wi-Fi. Retrying automatically."
                e is RackHttpException -> "The ESP32 replied with HTTP ${e.statusCode}, but lighting state is unavailable. Retrying automatically; check the firmware lighting service."
                else -> "The ESP32 replied with incompatible or inconsistent lighting data. Check that its firmware supports the ordinary-LED lighting API (schema 2). No settings were confirmed."
            }
            _state.update { it.copy(connection = if (unsupported) LightingConnection.UNSUPPORTED else LightingConnection.ERROR,
                problem = problem, error = message) }
        }
    }

    fun power(id: LightingZone, enabled: Boolean) = edit(id, debounce = false) { it.copy(power = enabled) }
    fun brightness(id: LightingZone, percent: Int) {
        require(percent in 0..100)
        edit(id) { it.copy(brightness = percent) }
    }
    fun discard(id: LightingZone) {
        if (_state.value.zones.getValue(id).busy) return
        commands.remove(id)?.cancel()
        zone(id) { it.copy(pending = null, error = null) }
    }
    fun retry(id: LightingZone) {
        val desired = _state.value.zones.getValue(id).pending?.takeIf { it.phase == LightCommandPhase.FAILED }?.settings ?: return
        edit(id, debounce = false) { desired }
    }

    private fun edit(id: LightingZone, debounce: Boolean = true, change: (LightSettings) -> LightSettings) {
        val state = _state.value
        val ui = state.zones.getValue(id)
        if (state.connection != LightingConnection.READY || ui.busy) return
        val actual = ui.confirmed ?: return
        val desired = change(ui.selected ?: actual.settings)
        commands.remove(id)?.cancel() // Only unsent edits or a previously completed request can be replaced.
        if (desired == actual.settings) {
            zone(id) { it.copy(pending = null, error = null) }
            return
        }
        val baseRevision = ui.pending?.takeIf { it.phase == LightCommandPhase.DEBOUNCING }?.baseRevision ?: actual.revision
        val pending = PendingLightChange(desired, UUID.randomUUID().toString(), baseRevision, LightCommandPhase.DEBOUNCING)
        zone(id) { it.copy(pending = pending, error = null) }
        val epoch = generation
        val target = address ?: return
        val boot = state.deviceSession ?: return
        commands[id] = scope.launch {
            if (debounce) delay(400)
            if (!current(epoch, id, pending.requestId)) return@launch
            if (_state.value.connection != LightingConnection.READY) {
                zone(id) { it.copy(pending = it.pending?.copy(phase = LightCommandPhase.FAILED),
                    error = "Lighting connection unavailable. Change was not sent; refresh and retry.") }
                return@launch
            }
            zone(id) { it.copy(pending = it.pending?.copy(phase = LightCommandPhase.SENDING)) }
            try {
                val ack = network.withLock {
                    check(current(epoch, id, pending.requestId) && _state.value.connection == LightingConnection.READY &&
                        _state.value.deviceSession == boot) { "Lighting connection changed before this command was sent." }
                    api.update(target, LightingUpdate(id, boot, baseRevision, pending.requestId, desired))
                }
                if (!current(epoch, id, pending.requestId)) return@launch
                require(ack.zone == id && ack.requestId == pending.requestId && ack.deviceSession == boot && ack.revision > baseRevision) {
                    "The acknowledgement did not match this zone change."
                }
                zone(id) { it.copy(pending = it.pending?.copy(phase = LightCommandPhase.CONFIRMING, requiredRevision = ack.revision)) }
                fetch(epoch, target)
                delay(8000)
                if (current(epoch, id, pending.requestId)) zone(id) {
                    it.copy(pending = it.pending?.copy(phase = LightCommandPhase.FAILED),
                        error = "Accepted, but the device has not confirmed this setting. Review actual state before retrying.")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!current(epoch, id, pending.requestId)) return@launch
                zone(id) { it.copy(pending = it.pending?.copy(phase = LightCommandPhase.FAILED),
                    error = if (e is RackHttpException) "Change not confirmed (HTTP ${e.statusCode}). Review device state before retrying. No automatic resend."
                        else "Change could not be confirmed. Check the connection and review actual device state before retrying. No automatic resend.") }
                fetch(epoch, target)
            }
        }
    }
}
