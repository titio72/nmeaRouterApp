package com.aboni.n2kRouter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.aboni.marine.link.DeviceInfo
import com.aboni.marine.link.LinkStatus
import com.aboni.marine.ui.devices.DeviceList

/**
 * What the Devices card shows: the routers found by scans (in the order they were found, so rows don't jump under the
 * finger), the chosen one and whether a scan runs. Fed by the router's listener on the UI thread.
 */
class DevicesState(private val ble: BLEThing) : BLEN2KListener {
    private val scanned = ScannedDevices()
    var devices: List<DeviceInfo> by mutableStateOf(scanned.list)
        private set
    var chosenId: String? by mutableStateOf(ble.getDeviceToConnect())
        private set
    var scanning: Boolean by mutableStateOf(false)
        private set

    init {
        ble.addListener(this)
    }

    fun scan() = ble.startScan()

    fun select(id: String) {
        chosenId = id
        ble.setDeviceToConnect(id)
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        this.scanning = scanning
        chosenId = ble.getDeviceToConnect()
    }

    override fun onScan(device: DeviceItem) {
        scanned.update(device)
        devices = scanned.list
    }

    override fun onConf(conf: Conf) {}
    override fun onData(data: Data) {}
    override fun onRssi(connectedDevice: DeviceItem, rssi: Int) {}
    override fun onCommandResult(result: com.aboni.marine.link.CommandResult) {}
}

/** The Devices card's content: marine-kit's DeviceList. Tapping a router makes it the device to connect to. */
@Composable
fun DevicesCard(state: DevicesState, status: LinkStatus) {
    DeviceList(
        devices = state.devices,
        status = status,
        chosenId = state.chosenId,
        onSelect = state::select,
        scanning = state.scanning,
        emptyText = stringResource(R.string.scan_no_devices),
    )
}
