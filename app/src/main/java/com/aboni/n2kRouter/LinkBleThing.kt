package com.aboni.n2kRouter

import android.content.Context
import com.aboni.marine.link.CommandResult
import com.aboni.marine.link.DEVICE_SCAN_MS
import com.aboni.marine.link.LinkState
import com.aboni.marine.link.LinkStatus
import com.aboni.marine.link.Pipe
import com.aboni.marine.link.PipeLink
import com.aboni.marine.link.ble.AndroidBleRadio
import com.aboni.marine.link.ble.BlePipe
import com.aboni.marine.link.ble.KeystorePasskeyStore
import com.aboni.marine.link.ble.PasskeyPrompter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * [BLEThing] on marine-kit: scanning, connecting, pairing, the passkey and reconnecting are the link's. This class
 * turns its state into the listener callbacks the screens already use, and the `save...` calls into the router's ASCII
 * commands. All of it runs on [scope]'s dispatcher, which must be the UI thread.
 */
class LinkBleThing(private val scope: CoroutineScope, pipe: Pipe) : BLEThing {

    override val data = Data()
    override val conf = Conf()

    private val link = PipeLink(scope, pipe, RouterProtocol(data, conf))

    /** The link's own status (state, device, signal, last error, data age): what the status bar and device list show. */
    val linkStatus: StateFlow<LinkStatus> get() = link.status
    private val listeners = ArrayList<BLEN2KListener>()
    private val jobs = ArrayList<Job>()
    private var scanWindow: Job? = null
    private var scanning = false
    private var lastStatus: Pair<BLELifecycleState, Boolean>? = null
    private var lastRssi: Int? = null
    private var lastConf: String? = null
    private val seenRssi = HashMap<String, Int>()

    init {
        jobs += scope.launch { link.status.collect(::onLinkStatus) }
        jobs += scope.launch { link.data.collect { state -> if (state != null) onState() } }
        jobs += scope.launch { link.devices.collect(::onDevices) }
    }

    //region listeners
    override fun addListener(listener: BLEN2KListener) {
        listeners.remove(listener)
        listeners.add(listener)
    }

    private fun onLinkStatus(s: LinkStatus) {
        val state = lifecycleOf(s.state)
        val pair = state to (s.state == LinkState.Scanning || scanning)
        if (pair != lastStatus) {
            lastStatus = pair
            appendLog("status = ${pair.first} scanning = ${pair.second}")
            for (l in listeners) l.onStatus(pair.first, pair.second)
        }
        val dev = s.device
        val rssi = dev?.rssi
        if (dev != null && rssi != null && rssi != lastRssi) {
            lastRssi = rssi
            for (l in listeners) l.onRssi(DeviceItem(dev.name, dev.id, rssi), rssi)
        }
    }

    private fun onState() {
        val c = String(conf.toByteArray())
        if (c != lastConf) {
            lastConf = c
            for (l in listeners) l.onConf(conf)
        }
        for (l in listeners) l.onData(data)
    }

    private fun onDevices(list: List<com.aboni.marine.link.DeviceInfo>) {
        for (d in list) {
            val rssi = d.rssi ?: UNKNOWN_RSSI
            if (seenRssi.put(d.id, rssi) != rssi) {
                for (l in listeners) l.onScan(DeviceItem(d.name, d.id, rssi))
            }
        }
    }

    private fun report(result: CommandResult) {
        appendLog("Command result: ${result.text}")
        for (l in listeners) l.onCommandResult(result)
    }
    //endregion

    //region connection
    override fun startScan() {
        link.start()
        link.scan()
        scanning = true
        scanWindow?.cancel()
        scanWindow = scope.launch {
            delay(DEVICE_SCAN_MS)
            scanning = false
            onLinkStatus(link.status.value)
        }
        onLinkStatus(link.status.value)
    }

    override fun stopScan() {
        // the link ends its own scans
    }

    override fun setDeviceToConnect(address: String) = link.select(address)

    override fun getDeviceToConnect(): String? = link.chosen.value

    override fun connect() = link.start()

    override fun disconnect() = link.stop()

    override fun refreshConnection() = link.start()

    override fun getStatus(): BLELifecycleState = lifecycleOf(link.status.value.state)

    override fun getConnectedDevice(): DeviceItem? =
        link.status.value.device?.let { DeviceItem(it.name, it.id, it.rssi ?: -1) }

    override fun release() {
        scanWindow?.cancel()
        jobs.forEach { it.cancel() }
        link.close()
    }
    //endregion

    //region commands
    private fun send(payload: String) {
        scope.launch { report(link.send(RouterCommand(RouterChannels.CMD, payload))) }
    }

    override fun sendHeartbeat() {
        // no feedback: it goes out every few seconds, and the link status already tells if the router is gone
        scope.launch { link.send(RouterCommand.HEARTBEAT) }
    }

    override fun saveConfiguration(conf: Conf) {
        this.conf.copyFrom(conf)
        send("S" + String(conf.toByteArray()))
    }

    override fun saveDeviceName(n: String) = send("N$n")

    override fun saveRPMCalibration(rpm: Int) = send("T$rpm")

    override fun saveEngineHours(h: Int, m: Int) = send("H${h * 3600 + m * 60}")

    override fun saveRPMAdjustment(value: Double) = send("t${scaled(value)}")

    override fun saveSTWAdjustment(value: Double) = send("s${scaled(value)}")

    override fun saveSTWAlpha(value: Double) = send("a${scaled(value)}")

    override fun saveSeaTempAdjustment(value: Double) = send("w${scaled(value)}")

    override fun saveSeaTempAlpha(value: Double) = send("x${scaled(value)}")

    override fun saveBatteryCapacity(ah: Int) = send("B$ah")

    private fun scaled(value: Double) = (value * 100).toInt()
    //endregion

    companion object {
        private const val UNKNOWN_RSSI = -100

        /** The real thing: BLE radio, the file with the chosen device, and the Keystore passkeys the app already has. */
        fun create(context: Context, scope: CoroutineScope, prompter: PasskeyPrompter): LinkBleThing {
            val pipe = BlePipe(
                scope,
                AndroidBleRadio(context),
                ROUTER_PROFILE,
                FileDeviceStore(context.filesDir),
                KeystorePasskeyStore(context, prefsName = "n2k.passkeys", keyAlias = "n2k.passkey.key"),
                prompter,
            )
            return LinkBleThing(scope, pipe)
        }

        private fun lifecycleOf(s: LinkState): BLELifecycleState = when (s) {
            LinkState.Connected, LinkState.Stale -> BLELifecycleState.Connected
            LinkState.Connecting, LinkState.Pairing -> BLELifecycleState.Connect
            LinkState.Idle, LinkState.Scanning -> BLELifecycleState.Off
        }
    }
}
