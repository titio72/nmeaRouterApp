package com.aboni.n2kRouter

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
}