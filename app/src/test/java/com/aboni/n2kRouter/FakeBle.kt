package com.aboni.n2kRouter

/** A [BLEThing] that records the commands the screens send. */
class FakeBle(var connectedName: String? = "N2K") : BLEThing {
    override val data = Data()
    override val conf = Conf()
    val sent = mutableListOf<String>()
    val listeners = mutableListOf<BLEN2KListener>()

    override fun addListener(listener: BLEN2KListener) { listeners += listener }
    override fun saveDeviceName(n: String) { sent += "name $n" }
    override fun saveRPMCalibration(rpm: Int) { sent += "rpmCal $rpm" }
    override fun saveEngineHours(h: Int, m: Int) { sent += "engineHours $h:$m" }
    override fun saveRPMAdjustment(value: Double) { sent += "rpmAdj $value" }
    override fun saveConfiguration(conf: Conf) { sent += "conf ${String(conf.toByteArray())}" }
    override fun saveSTWAdjustment(value: Double) { sent += "stwAdj $value" }
    override fun saveSTWAlpha(value: Double) { sent += "stwAlpha $value" }
    override fun saveSeaTempAdjustment(value: Double) { sent += "seaTempAdj $value" }
    override fun saveSeaTempAlpha(value: Double) { sent += "seaTempAlpha $value" }
    override fun saveBatteryCapacity(ah: Int) { sent += "battery $ah" }
    override fun startScan() {}
    override fun stopScan() {}
    override fun setDeviceToConnect(address: String) {}
    override fun getDeviceToConnect(): String? = null
    override fun connect() {}
    override fun disconnect() {}
    override fun getStatus() = if (connectedName != null) BLELifecycleState.Connected else BLELifecycleState.Off
    override fun getConnectedDevice() = connectedName?.let { DeviceItem(it, "AA:BB", -60) }
    override fun refreshConnection() {}
    override fun sendHeartbeat() {}
    override fun release() {}

    fun status(s: BLELifecycleState) = listeners.forEach { it.onStatus(s, false) }
    fun data(d: Data = data) = listeners.forEach { it.onData(d) }
    fun conf(c: Conf) = listeners.forEach { it.onConf(c) }
}

/** Data whose services field reports [conf]. */
fun dataWithServices(conf: Conf): Data = Data().apply {
    servicesValue.valid = true
    servicesValue.value = conf.toBits().toLong()
}

private fun Conf.toBits(): Int = listOf(bGPS, bDHT, bBME, bSYT, bRPM, bSTW, bVED, bSRC, bSEA_TEMP, bSTW_PADDLE, bLOG)
    .foldIndexed(0) { i, acc, on -> if (on) acc or (1 shl i) else acc }
