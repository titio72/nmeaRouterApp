package com.aboni.n2kRouter

import com.aboni.marine.link.CommandResult
import com.aboni.marine.link.DEVICE_SCAN_MS
import com.aboni.marine.link.DeviceChooser
import com.aboni.marine.link.DeviceInfo
import com.aboni.marine.link.Packet
import com.aboni.marine.link.Pipe
import com.aboni.marine.link.PipeState
import com.aboni.marine.link.PipeStatus
import com.aboni.marine.link.Transport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FakeRouterPipe : Pipe, DeviceChooser {
    override val transport = Transport.Bluetooth
    override val status = MutableStateFlow(PipeStatus())
    override val inbound = MutableSharedFlow<Packet>(extraBufferCapacity = 64)
    override val devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override val chosen = MutableStateFlow<String?>(null)
    val writes = mutableListOf<Packet>()
    var failWith: IOException? = null
    var started = 0
    var stopped = 0
    var scans = 0

    override fun start() { started++ }
    override fun stop() { stopped++; status.value = PipeStatus() }
    override fun select(id: String) { chosen.value = id }
    override fun scan() { scans++ }
    override suspend fun write(p: Packet) {
        failWith?.let { throw it }
        writes += p
    }

    fun up(id: String = "AA:BB", name: String = "N2K", rssi: Int? = null) {
        status.value = PipeStatus(PipeState.Up, DeviceInfo(id, name, rssi))
    }

    fun data(version: Int = 10) = check(inbound.tryEmit(Packet(RouterChannels.DATA, ByteArray(80).also { it[0] = version.toByte() })))
    fun conf(s: String) = check(inbound.tryEmit(Packet(RouterChannels.CONF, s.toByteArray())))
}

class RecordingListener : BLEN2KListener {
    val statuses = mutableListOf<Pair<BLELifecycleState, Boolean>>()
    val scanned = mutableListOf<DeviceItem>()
    val rssis = mutableListOf<Int>()
    val results = mutableListOf<CommandResult>()
    var datas = 0
    var confs = 0
    override fun onStatus(status: BLELifecycleState, scanning: Boolean) { statuses += status to scanning }
    override fun onConf(conf: Conf) { confs++ }
    override fun onData(data: Data) { datas++ }
    override fun onScan(device: DeviceItem) { scanned += device }
    override fun onRssi(connectedDevice: DeviceItem, rssi: Int) { rssis += rssi }
    override fun onCommandResult(result: CommandResult) { results += result }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LinkBleThingTest {
    private val pipe = FakeRouterPipe()
    private val listener = RecordingListener()

    private fun TestScope.thing(connected: Boolean = true): LinkBleThing {
        val t = LinkBleThing(backgroundScope, pipe)
        t.addListener(listener)
        runCurrent()
        if (connected) { pipe.up(); pipe.data(); runCurrent() }
        return t
    }

    private fun commands() = pipe.writes.map { it.channel to String(it.bytes) }

    @Test
    fun commandStringsMatchTheOldImplementation() = runTest {
        val t = thing()
        t.saveDeviceName("N2K"); t.saveRPMCalibration(1200); t.saveEngineHours(2, 30); t.saveRPMAdjustment(1.25)
        t.saveSTWAdjustment(0.5); t.saveSTWAlpha(0.2); t.saveSeaTempAdjustment(-1.0); t.saveSeaTempAlpha(0.3)
        t.saveBatteryCapacity(200)
        runCurrent()
        assertEquals(
            listOf("NN2K", "T1200", "H9000", "t125", "s50", "a20", "w-100", "x30", "B200").map { RouterChannels.CMD to it },
            commands(),
        )
    }

    @Test
    fun saveConfigurationSendsSPlusConfBytesAndKeepsAnLocalCopy() = runTest {
        val t = thing()
        val c = Conf().apply { bGPS = true; bRPM = true }
        t.saveConfiguration(c)
        runCurrent()
        assertEquals(listOf(RouterChannels.CMD to "S10001000000"), commands())
        assertTrue(t.conf.bGPS && t.conf.bRPM)
    }

    @Test
    fun heartbeatIsWrittenOnChannelFourAndNeverReportsAResult() = runTest {
        val t = thing()
        t.sendHeartbeat(); runCurrent()
        assertEquals(listOf(RouterChannels.HEARTBEAT to "h"), commands())
        assertTrue(listener.results.isEmpty())
    }

    @Test
    fun heartbeatFailureIsNotReported() = runTest {
        val t = thing()
        pipe.failWith = IOException("boom")
        t.sendHeartbeat(); runCurrent()
        assertTrue(listener.results.isEmpty())
    }

    @Test
    fun sentResultIsReportedToListeners() = runTest {
        val t = thing()
        t.saveDeviceName("N2K"); runCurrent()
        assertEquals(listOf<CommandResult>(CommandResult.Sent), listener.results)
    }

    @Test
    fun commandWhileNotConnectedReportsNotConnected() = runTest {
        val t = thing(connected = false)
        t.saveDeviceName("N2K"); runCurrent()
        assertEquals(listOf<CommandResult>(CommandResult.NotConnected), listener.results)
        assertTrue(pipe.writes.isEmpty())
    }

    @Test
    fun statusMapping() = runTest {
        val t = thing(connected = false)
        assertEquals(BLELifecycleState.Off, t.getStatus())
        pipe.status.value = PipeStatus(PipeState.Scanning); runCurrent()
        assertEquals(BLELifecycleState.Off, t.getStatus())
        assertEquals(BLELifecycleState.Off to true, listener.statuses.last())
        pipe.status.value = PipeStatus(PipeState.Connecting, DeviceInfo("AA", "N2K")); runCurrent()
        assertEquals(BLELifecycleState.Connect, t.getStatus())
        pipe.status.value = PipeStatus(PipeState.Pairing, DeviceInfo("AA", "N2K")); runCurrent()
        assertEquals(BLELifecycleState.Connect, t.getStatus())
        pipe.up("AA"); pipe.data(); runCurrent()
        assertEquals(BLELifecycleState.Connected, t.getStatus())
        assertEquals(BLELifecycleState.Connected to false, listener.statuses.last())
        pipe.status.value = PipeStatus(); runCurrent()
        assertEquals(BLELifecycleState.Off, t.getStatus())
    }

    @Test
    fun linkStatusIsTheLinksOwnStatusWithItsErrorText() = runTest {
        val t = thing(connected = false)
        pipe.status.value = PipeStatus(PipeState.Connecting, DeviceInfo("AA", "N2K"), "Bluetooth is off"); runCurrent()
        assertEquals(com.aboni.marine.link.LinkState.Connecting, t.linkStatus.value.state)
        assertEquals("Bluetooth is off", t.linkStatus.value.lastError)
        assertEquals("AA", t.linkStatus.value.device?.id)
    }

    @Test
    fun getConnectedDeviceIsNullWhenIdleAndTheDeviceWhenConnected() = runTest {
        val t = thing(connected = false)
        assertNull(t.getConnectedDevice())
        pipe.up("AA:BB", "N2K"); pipe.data(); runCurrent()
        assertEquals("AA:BB", t.getConnectedDevice()?.id)
        assertEquals("N2K", t.getConnectedDevice()?.name)
    }

    @Test
    fun selectingADevicePassesItToTheLinkAndGetDeviceToConnectReturnsIt() = runTest {
        val t = thing(connected = false)
        assertNull(t.getDeviceToConnect())
        t.setDeviceToConnect("AA:BB"); runCurrent()
        assertEquals("AA:BB", pipe.chosen.value)
        assertEquals("AA:BB", t.getDeviceToConnect())
    }

    @Test
    fun dataPacketReachesOnDataWithTheSameInstance() = runTest {
        val t = thing(connected = false)
        val before = t.data
        pipe.up(); pipe.data(); runCurrent()
        assertTrue(listener.datas >= 1)
        assertSame(before, t.data)
        assertEquals(10, t.data.version)
    }

    @Test
    fun confIsReportedOnlyWhenItChanges() = runTest {
        val t = thing(connected = false)
        pipe.up(); pipe.conf("10000000000"); runCurrent()
        pipe.conf("10000000000"); runCurrent()
        assertEquals(1, listener.confs)
        pipe.conf("11000000000"); runCurrent()
        assertEquals(2, listener.confs)
        assertTrue(t.conf.bDHT)
    }

    @Test
    fun startScanStartsTheLinkScansAndReportsScanningForTheScanWindow() = runTest {
        val t = thing(connected = false)
        t.startScan(); runCurrent()
        assertEquals(1, pipe.started)
        assertEquals(1, pipe.scans)
        assertEquals(BLELifecycleState.Off to true, listener.statuses.last())
        advanceTimeBy(DEVICE_SCAN_MS + 1); runCurrent()
        assertEquals(BLELifecycleState.Off to false, listener.statuses.last())
    }

    @Test
    fun scannedDevicesAreReportedOnceUnlessTheirSignalChanges() = runTest {
        thing(connected = false)
        pipe.devices.value = listOf(DeviceInfo("AA", "N2K", -60)); runCurrent()
        pipe.devices.value = listOf(DeviceInfo("AA", "N2K", -60), DeviceInfo("BB", "Other", -80)); runCurrent()
        pipe.devices.value = listOf(DeviceInfo("AA", "N2K", -50), DeviceInfo("BB", "Other", -80)); runCurrent()
        assertEquals(listOf("AA" to -60, "BB" to -80, "AA" to -50), listener.scanned.map { it.id to it.rssi })
    }

    @Test
    fun connectedDeviceSignalIsReportedAsRssi() = runTest {
        thing(connected = false)
        pipe.up("AA", "N2K", rssi = -61); pipe.data(); runCurrent()
        pipe.up("AA", "N2K", rssi = -55); runCurrent()
        assertEquals(listOf(-61, -55), listener.rssis)
    }

    @Test
    fun disconnectStopsTheLinkAndReleaseClosesIt() = runTest {
        val t = thing()
        t.disconnect(); runCurrent()
        assertEquals(1, pipe.stopped)
        t.release()
    }
}
