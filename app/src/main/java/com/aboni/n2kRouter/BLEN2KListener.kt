package com.aboni.n2kRouter

import com.aboni.marine.link.CommandResult

enum class BLELifecycleState {
    Off,
    Connect,
    Discover,
    Connected
}

interface BLEN2KListener {
    fun onStatus(status: BLELifecycleState, scanning: Boolean)
    fun onConf(conf: Conf)
    fun onData(data: Data)
    fun onScan(device: DeviceItem)
    fun onRssi(connectedDevice: DeviceItem, rssi: Int)
    /** The outcome of a command, in [CommandResult.text] plain language. Called on the UI thread. */
    fun onCommandResult(result: CommandResult)
}
