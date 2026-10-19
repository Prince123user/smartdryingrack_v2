package com.example.smartdryingrack.data

import com.example.smartdryingrack.data.hardware.RackController
import com.example.smartdryingrack.data.repository.EventLog
import com.example.smartdryingrack.data.repository.RackRepository
import com.example.smartdryingrack.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class RackRepositoryTest {
    private open class Controller : RackController {
        val state = MutableStateFlow(RackState.RETRACTED)
        val sensors = MutableStateFlow<SensorData?>(SensorData(29f, 60f, false, null, System.currentTimeMillis()))
        val connection = MutableStateFlow(ConnectionState.ONLINE)
        val commands = mutableListOf<String>()
        var stopFails = false
        override suspend fun extend() { commands += "EXTEND" }
        override suspend fun retract() { commands += "RETRACT" }
        override suspend fun stop() { if (stopFails) throw java.io.IOException("offline"); commands += "STOP" }
        override fun observeRackState() = state
        override fun observeSensorData() = sensors
        override fun observeConnection() = connection
        override suspend fun connect() {}
        override suspend fun disconnect() { connection.value = ConnectionState.OFFLINE }
        override suspend fun isConnected() = connection.value == ConnectionState.ONLINE
    }
    private class SettingsController : Controller(), com.example.smartdryingrack.data.hardware.DeviceRackSettings {
        override val canAttemptStop: Boolean get() = true
        override val deviceMode = MutableStateFlow<RackMode?>(RackMode.AUTO)
        override val modeSupported = MutableStateFlow(true)
        override val stopLatched = MutableStateFlow<Boolean?>(false)
        val modeRequests = java.util.concurrent.CopyOnWriteArrayList<RackMode>()
        var modeGate: CompletableDeferred<Unit>? = null
        var modeFailure = false
        override suspend fun setDeviceMode(mode: RackMode) {
            modeRequests += mode
            modeGate?.await()
            if (modeFailure) throw java.io.IOException("offline")
            deviceMode.value = mode
        }
    }
    private suspend fun ready(controller: Controller): RackRepository {
        val repository = RackRepository(controller, mock(EventLog::class.java))
        withTimeout(2000) {
            repository.connection.first { it == controller.connection.value }
            repository.rackState.first { it == controller.state.value }
            repository.sensorData.first { it != null }
        }
        return repository
    }

    @Test fun wetSensorsPreventExtensionEvenBeforeDebounce() = runBlocking {
        val controller = Controller()
        controller.sensors.value = controller.sensors.value!!.copy(rainDetected = true)
        val repository = ready(controller)
        assertEquals(CommandResult.Rejected(RejectReason.ERROR), repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        assertTrue(controller.commands.isEmpty())
    }

    @Test fun staleTelemetryCannotExtend() = runBlocking {
        val controller = Controller()
        controller.sensors.value = controller.sensors.value!!.copy(timestamp = System.currentTimeMillis() - 20000)
        val repository = ready(controller)
        assertEquals(CommandResult.Rejected(RejectReason.ERROR), repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        assertTrue(controller.commands.isEmpty())
    }

    @Test fun duplicateAcceptedCommandWaitsForTelemetryCompletion() = runBlocking {
        val controller = Controller()
        val repository = ready(controller)
        assertEquals(CommandResult.Accepted, repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        assertEquals(CommandResult.Rejected(RejectReason.BUSY), repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        repository.send(RackAction.STOP, CommandSource.EMERGENCY)
        assertEquals(listOf("EXTEND", "STOP"), controller.commands)
    }

    @Test fun emergencyHoldSurvivesSwitchingBackToAuto() = runBlocking {
        val controller = Controller()
        val repository = ready(controller)
        repository.send(RackAction.STOP, CommandSource.EMERGENCY)
        repository.setMode(RackMode.AUTO)
        assertEquals(CommandResult.Rejected(RejectReason.EMERGENCY_HOLD), repository.send(RackAction.EXTEND, CommandSource.SCHEDULE))
        repository.setMode(RackMode.MANUAL)
        assertEquals(CommandResult.Accepted, repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        repository.send(RackAction.STOP, CommandSource.EMERGENCY)
        Unit
    }

    @Test fun unknownPositionCannotMoveButCanStop() = runBlocking {
        val controller = Controller()
        controller.state.value = RackState.UNKNOWN
        val repository = ready(controller)
        assertEquals(CommandResult.Rejected(RejectReason.ERROR), repository.send(RackAction.RETRACT, CommandSource.MANUAL))
        assertEquals(CommandResult.Accepted, repository.send(RackAction.STOP, CommandSource.EMERGENCY))
        assertEquals(listOf("STOP"), controller.commands)
    }
    @Test fun unconfirmedEmergencyStopStillHoldsAutomation() = runBlocking {
        val controller = Controller()
        controller.stopFails = true
        val repository = ready(controller)
        assertEquals(CommandResult.Rejected(RejectReason.ERROR), repository.send(RackAction.STOP, CommandSource.EMERGENCY))
        repository.setMode(RackMode.AUTO)
        assertEquals(CommandResult.Rejected(RejectReason.EMERGENCY_HOLD), repository.send(RackAction.EXTEND, CommandSource.SCHEDULE))
        assertTrue(controller.commands.isEmpty())
    }
    @Test fun rainPreemptsAcceptedExtensionBeforeMovementTelemetry() = runBlocking {
        val controller = Controller()
        val repository = ready(controller)
        assertEquals(CommandResult.Accepted, repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        controller.sensors.value = controller.sensors.value!!.copy(rainDetected = true, timestamp = System.currentTimeMillis())
        withTimeout(2000) { repository.sensorData.first { it?.rainDetected == true } }
        repository.setRainConfirmed(true)
        // The last telemetry still says RETRACTED. A pending extension must nevertheless be stopped.
        assertEquals(CommandResult.Accepted, repository.send(RackAction.RETRACT, CommandSource.RAIN))
        assertEquals(listOf("EXTEND", "STOP", "RETRACT"), controller.commands)
        repository.send(RackAction.STOP, CommandSource.EMERGENCY)
        Unit
    }
    @Test fun oldRainConfirmationCannotRetractOnFreshDryTelemetry() = runBlocking {
        val controller = Controller()
        controller.state.value = RackState.EXTENDED
        val repository = ready(controller)
        repository.setMode(RackMode.AUTO)
        repository.setRainConfirmed(true)
        assertEquals(CommandResult.Rejected(RejectReason.ERROR), repository.send(RackAction.RETRACT, CommandSource.RAIN))
        assertTrue(controller.commands.isEmpty())
    }

    @Test fun staleWetTelemetryCannotTriggerRainRetraction() = runBlocking {
        val controller = Controller()
        controller.state.value = RackState.EXTENDED
        controller.sensors.value = controller.sensors.value!!.copy(rainDetected = true, timestamp = System.currentTimeMillis() - 20000)
        val repository = ready(controller)
        repository.setRainConfirmed(true)
        assertEquals(CommandResult.Rejected(RejectReason.ERROR), repository.send(RackAction.RETRACT, CommandSource.RAIN))
        assertTrue(controller.commands.isEmpty())
    }

    @Test fun completionRequiresTelemetryReceivedAfterCommandAcceptance() = runBlocking {
        val controller = Controller()
        val repository = ready(controller)
        assertEquals(CommandResult.Accepted, repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        controller.state.value = RackState.EXTENDED
        delay(50)
        assertEquals(RackState.EXTENDED, repository.pendingMovement.value)
        controller.sensors.value = controller.sensors.value!!.copy(timestamp = System.currentTimeMillis())
        withTimeout(2000) { repository.pendingMovement.first { it == null } }
        assertEquals(listOf("EXTEND"), controller.commands)
    }
    @Test fun offlineEmergencyStopHoldsAutomationAfterReconnection() = runBlocking {
        val controller = Controller()
        val repository = ready(controller)
        controller.connection.value = ConnectionState.OFFLINE
        withTimeout(2000) { repository.connection.first { it == ConnectionState.OFFLINE } }
        assertEquals(CommandResult.Rejected(RejectReason.OFFLINE), repository.send(RackAction.STOP, CommandSource.EMERGENCY))
        controller.connection.value = ConnectionState.ONLINE
        withTimeout(2000) { repository.connection.first { it == ConnectionState.ONLINE } }
        repository.setMode(RackMode.AUTO)
        assertEquals(CommandResult.Rejected(RejectReason.EMERGENCY_HOLD), repository.send(RackAction.EXTEND, CommandSource.SCHEDULE))
        assertTrue(controller.commands.isEmpty())
    }
    @Test fun modeChangesWaitForDeviceConfirmationAndBlockMotionUntilConfirmed() = runBlocking {
        val controller = SettingsController()
        val repository = ready(controller)
        withTimeout(2000) { repository.mode.first { it == RackMode.AUTO } }
        controller.modeGate = CompletableDeferred()
        repository.setMode(RackMode.MANUAL)
        withTimeout(2000) { while (controller.modeRequests.isEmpty()) delay(1) }
        assertEquals(RackMode.AUTO, repository.mode.value)
        assertEquals(CommandResult.Rejected(RejectReason.BUSY), repository.send(RackAction.EXTEND, CommandSource.MANUAL))
        repository.setMode(RackMode.AUTO)
        assertEquals(listOf(RackMode.MANUAL), controller.modeRequests.toList())
        controller.modeGate!!.complete(Unit)
        withTimeout(2000) { repository.mode.first { it == RackMode.MANUAL } }
        Unit
    }

    @Test fun rejectedModeDoesNotPretendToChangeTheDeviceOrAutomaticallyRetry() = runBlocking {
        val controller = SettingsController().also { it.modeFailure = true }
        val repository = ready(controller)
        withTimeout(2000) { repository.mode.first { it == RackMode.AUTO } }
        repository.setMode(RackMode.MANUAL)
        withTimeout(2000) { repository.modeMessage.first { it?.contains("not confirmed") == true } }
        assertEquals(RackMode.AUTO, repository.mode.value)
        assertEquals(listOf(RackMode.MANUAL), controller.modeRequests.toList())
    }

    @Test fun firmwareStopLatchPreventsPhoneAutomationFromResumingAfterAppRestart() = runBlocking {
        val controller = SettingsController().also {
            it.stopLatched.value = true; it.state.value = RackState.STOPPED
        }
        val repository = ready(controller)
        assertEquals(CommandResult.Rejected(RejectReason.EMERGENCY_HOLD), repository.send(RackAction.EXTEND, CommandSource.SCHEDULE))
        assertEquals(CommandResult.Rejected(RejectReason.EMERGENCY_HOLD), repository.send(RackAction.RETRACT, CommandSource.RAIN))
        assertTrue(controller.commands.isEmpty())
    }

    @Test fun firmwareRainAutomationIsNotInterruptedByRedundantPhoneCommands() = runBlocking {
        val controller = SettingsController().also { it.state.value = RackState.RETRACTING }
        val repository = ready(controller)
        assertEquals(CommandResult.Rejected(RejectReason.ALREADY_THERE), repository.send(RackAction.RETRACT, CommandSource.RAIN))
        assertTrue(controller.commands.isEmpty()) // In particular, no STOP followed by RETRACT.
    }

    @Test fun explicitStopIsAttemptedWhenRequestedDeviceHasATelemetryError() = runBlocking {
        val controller = SettingsController().also { it.connection.value = ConnectionState.ERROR }
        val repository = ready(controller)
        assertEquals(CommandResult.Accepted, repository.send(RackAction.STOP, CommandSource.EMERGENCY))
        assertEquals(listOf("STOP"), controller.commands)
    }

}
