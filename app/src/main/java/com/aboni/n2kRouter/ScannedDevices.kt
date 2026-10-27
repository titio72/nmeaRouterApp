package com.aboni.n2kRouter

import com.aboni.marine.link.DeviceInfo

/**
 * The devices seen by scans, in the order they were first found, so rows don't jump under the finger.
 * Not thread-safe: use it from the UI thread.
 */
class ScannedDevices {
    private val devices = LinkedHashMap<String, DeviceInfo>()

    val list: List<DeviceInfo> get() = devices.values.toList()

    /** Adds a scan result, or refreshes its signal. A blank name never replaces a known one. */
    fun update(d: DeviceItem) {
        val name = d.name.ifBlank { devices[d.id]?.name ?: "" }
        devices[d.id] = DeviceInfo(d.id, name, d.rssi)
    }
}
