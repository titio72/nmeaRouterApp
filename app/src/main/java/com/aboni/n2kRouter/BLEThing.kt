package com.aboni.n2kRouter

/** What the screens use to talk to the router. [LinkBleThing] implements it on top of marine-kit's link. */
interface BLEThing {
    val data: Data
    val conf: Conf

    fun saveDeviceName(n: String)
    fun saveRPMCalibration(rpm: Int)
    fun saveEngineHours(h: Int, m: Int)
    fun saveRPMAdjustment(value: Double)
    fun saveConfiguration(conf: Conf)

    fun startScan()
    fun stopScan()

    fun setDeviceToConnect(address: String)
    /** The address chosen by the user (persisted), connected or not. */
    fun getDeviceToConnect(): String?
    fun connect()
    fun disconnect()

    fun getStatus(): BLELifecycleState
    fun getConnectedDevice(): DeviceItem?
    fun addListener(listener: BLEN2KListener)
    fun refreshConnection()
    fun sendHeartbeat()
    fun saveSTWAdjustment(value: Double)
    fun saveSTWAlpha(value: Double)
    fun saveSeaTempAdjustment(value: Double)
    fun saveSeaTempAlpha(value: Double)
    fun saveBatteryCapacity(ah: Int)

    fun release()
}
