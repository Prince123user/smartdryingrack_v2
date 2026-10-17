package com.example.smartdryingrack.lighting

import com.example.smartdryingrack.data.hardware.rest.*
import com.example.smartdryingrack.data.repository.*
import com.example.smartdryingrack.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LightingRepositoryTest {
    private class Device : LightingGateway {
        var snapshot = lightingFixture()
        var reads = 0
        val writes = mutableListOf<LightingUpdate>()
        var apply = true
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        override suspend fun fetch(address: String): LightingSnapshot { reads++; readFailure?.let { throw it }; return snapshot }
        override suspend fun update(address: String, update: LightingUpdate): LightingAcknowledgement {
            writes += update
            writeFailure?.let { throw it }
            val revision = snapshot.version + 1
            if (apply) snapshot = snapshot.copy(version = revision, zones = snapshot.zones +
                (update.zone to ConfirmedZone(update.settings, revision, update.requestId)))
            return LightingAcknowledgement(update.requestId, update.zone, update.deviceSession, revision)
        }
    }
    private val front = LightingZone.FRONT
    private val left = LightingZone.LEFT

    @Test fun allSixZonesAreIsolatedAndStatusCannotBeTargeted() = runTest {
        val device = Device(); val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent()
        val original = repo.state.value
        LightingZone.entries.forEach { id ->
            repo.brightness(id, 10 + id.ordinal); advanceTimeBy(401); runCurrent()
            assertEquals(10 + id.ordinal, repo.state.value.zones.getValue(id).confirmed!!.settings.brightness)
        }
        assertEquals(LightingZone.entries.toList(), device.writes.map { it.zone })
        assertEquals(original.indicator, repo.state.value.indicator)
        assertThrows(IllegalArgumentException::class.java) { LightingZone.fromId("system_status") }
        assertTrue(repo.state.value.zones.values.all { it.pending == null })
    }

    @Test fun rapidColorAndSliderChangesCoalesceWithoutChangingOtherZones() = runTest {
        val device = Device(); val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent()
        val untouched = repo.state.value.zones.getValue(left)
        repo.brightness(front, 20); advanceTimeBy(150); repo.brightness(front, 25)
        advanceTimeBy(150); repo.color(front, Rgb(255, 0, 100))
        advanceTimeBy(399); runCurrent(); assertTrue(device.writes.isEmpty())
        assertEquals(70, repo.state.value.zones.getValue(front).confirmed!!.settings.brightness)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, device.writes.size)
        assertEquals(LightSettings(true, Rgb(255, 0, 100), 25), device.writes.single().settings)
        assertEquals(untouched, repo.state.value.zones.getValue(left))
    }

    @Test fun offAndOnRetainColorAndBrightnessAndZeroIsExplicit() = runTest {
        val device = Device(); val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent()
        repo.power(front, false); runCurrent()
        val off = repo.state.value.zones.getValue(front).confirmed!!.settings
        assertFalse(off.power); assertEquals(Rgb(32, 160, 220), off.rgb); assertEquals(70, off.brightness)
        repo.brightness(front, 0); advanceTimeBy(401); runCurrent()
        repo.power(front, true); runCurrent()
        val on = repo.state.value.zones.getValue(front).confirmed!!.settings
        assertTrue(on.power); assertEquals(0, on.brightness); assertTrue(on.outputDescription.contains("no visible output"))
        assertEquals(off.rgb, on.rgb)
    }

    @Test fun acceptedCommandStaysPendingUntilActualGetConfirmsRequestAndValues() = runTest {
        val device = Device().also { it.apply = false }; val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent(); repo.power(front, false); runCurrent()
        var zone = repo.state.value.zones.getValue(front)
        assertTrue(zone.confirmed!!.settings.power); assertFalse(zone.selected!!.power)
        assertEquals(LightCommandPhase.CONFIRMING, zone.pending!!.phase)
        val update = device.writes.single()
        // Same desired values without the request identity do not confirm this command.
        device.snapshot = device.snapshot.copy(version = 1, zones = device.snapshot.zones +
            (front to ConfirmedZone(update.settings, 1, "another-client")))
        advanceTimeBy(2001); runCurrent(); assertNotNull(repo.state.value.zones.getValue(front).pending)
        device.snapshot = device.snapshot.copy(zones = device.snapshot.zones +
            (front to ConfirmedZone(update.settings, 1, update.requestId)))
        advanceTimeBy(2000); runCurrent()
        zone = repo.state.value.zones.getValue(front)
        assertNull(zone.pending); assertFalse(zone.confirmed!!.settings.power)
    }

    @Test fun timeoutHasNoAutomaticResendAndAllowsExplicitRetry() = runTest {
        val device = Device().also { it.apply = false }; val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent(); repo.power(front, false); runCurrent()
        advanceTimeBy(10000); runCurrent()
        assertEquals(LightCommandPhase.FAILED, repo.state.value.zones.getValue(front).pending!!.phase)
        assertEquals(1, device.writes.size)
        device.apply = true; repo.retry(front); runCurrent()
        assertEquals(2, device.writes.size)
        assertNotEquals(device.writes[0].requestId, device.writes[1].requestId)
        assertNull(repo.state.value.zones.getValue(front).pending)
    }

    @Test fun rejectionOnlyAffectsItsOwnZoneAndDoesNotRetry() = runTest {
        val device = Device().also { it.writeFailure = IOException("rejected") }; val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent(); repo.power(front, false); runCurrent()
        assertEquals(LightCommandPhase.FAILED, repo.state.value.zones.getValue(front).pending!!.phase)
        assertNull(repo.state.value.zones.getValue(left).error)
        device.writeFailure = null; repo.power(left, false); runCurrent()
        advanceTimeBy(10000); runCurrent()
        assertEquals(2, device.writes.size)
        assertFalse(repo.state.value.zones.getValue(left).confirmed!!.settings.power)
        repo.discard(front); assertNull(repo.state.value.zones.getValue(front).pending)
    }

    @Test fun disconnectCancelsUnsentEditsAndReconnectFetchesWithoutReplay() = runTest {
        val device = Device(); val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent(); repo.brightness(front, 15)
        repo.stop(); advanceTimeBy(500); runCurrent()
        assertTrue(device.writes.isEmpty()); assertEquals(LightingConnection.DISCONNECTED, repo.state.value.connection)
        val reads = device.reads
        repo.open("rack"); runCurrent()
        assertTrue(device.reads > reads); assertTrue(device.writes.isEmpty())
        assertEquals(LightCommandPhase.FAILED, repo.state.value.zones.getValue(front).pending!!.phase)
        repo.open("different-rack"); runCurrent()
        assertNull(repo.state.value.zones.getValue(front).pending)
    }

    @Test fun reconnectDoesNotAcceptStaleSnapshotOrReviveOldBoot() = runTest {
        val device = Device(); val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent(); repo.power(front, false); runCurrent()
        val latest = repo.state.value
        device.snapshot = lightingFixture(); advanceTimeBy(2001); runCurrent()
        assertEquals(latest.version, repo.state.value.version)
        assertFalse(repo.state.value.zones.getValue(front).confirmed!!.settings.power)
        device.snapshot = lightingFixture().copy(deviceSession = "boot-2")
        advanceTimeBy(2000); runCurrent(); assertEquals("boot-2", repo.state.value.deviceSession)
        device.snapshot = lightingFixture().copy(version = 999)
        advanceTimeBy(2000); runCurrent(); assertEquals("boot-2", repo.state.value.deviceSession)
    }

    @Test fun rebootLeavesOutstandingCommandUnconfirmedAndNeverReplays() = runTest {
        val device = Device().also { it.apply = false }; val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent(); repo.power(front, false); runCurrent()
        device.snapshot = lightingFixture().copy(deviceSession = "reboot")
        advanceTimeBy(2001); runCurrent()
        assertEquals(LightCommandPhase.FAILED, repo.state.value.zones.getValue(front).pending!!.phase)
        assertTrue(repo.state.value.zones.getValue(front).error!!.contains("restarted"))
        assertEquals(1, device.writes.size)
    }

    @Test fun oldFirmwareAndReadErrorsDisableWritesButStatusRetriesRecover() = runTest {
        val device = Device().also { it.readFailure = RackHttpException(404) }; val repo = LightingRepository(device, backgroundScope)
        repo.open("rack"); runCurrent(); assertEquals(LightingConnection.UNSUPPORTED, repo.state.value.connection)
        repo.power(front, true); runCurrent(); assertTrue(device.writes.isEmpty())
        device.readFailure = IOException("offline"); repo.refresh(); runCurrent()
        assertEquals(LightingConnection.ERROR, repo.state.value.connection)
        device.readFailure = null; advanceTimeBy(5001); runCurrent()
        assertEquals(LightingConnection.READY, repo.state.value.connection)
        assertNotNull(repo.state.value.indicator)
    }
}
