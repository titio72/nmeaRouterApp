package com.aboni.n2kRouter

enum class BLELifecycleState {
    Off,
    Connect,
    Discover,
    Connected
}

/** Outcome of a write to the device, reported on the UI thread. */
class CommandResult(val kind: Kind, val code: Int = 0, val bondFailure: Int = NO_BOND_FAILURE) {
    enum class Kind {
        Sent,
        NotConnected,
        PasskeyCancelled,
        /** security error: [code] is the GATT status, [bondFailure] the reason of the last failed pairing */
        PasskeyRejected,
        /** [code] is the GATT status or BluetoothStatusCodes value */
        GattError,
        /** the device never answered; [bondFailure] tells if a pairing failed meanwhile */
        Timeout
    }

    companion object {
        const val NO_BOND_FAILURE = -1
    }
}

interface BLEN2KListener {
    fun onStatus(status: BLELifecycleState, scanning: Boolean)
    fun onConf(conf: Conf)
    fun onData(data: Data)
    fun onScan(device: DeviceItem)
    fun onRssi(connectedDevice: DeviceItem, rssi: Int)
    fun onCommandResult(result: CommandResult)
}