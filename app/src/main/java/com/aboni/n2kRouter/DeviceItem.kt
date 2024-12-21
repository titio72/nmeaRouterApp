package com.aboni.n2kRouter

class DeviceItem(var name: String, val id: String, var rssi: Int) {

    constructor(name: String, id: String) : this(name, id, -1) {
    }

    fun idMatch(itm: DeviceItem): Boolean {
        return itm.id==id
    }

    fun updateName(name: String): Boolean {
        if (this.name != name) {
            this.name = name
            return true
        }
        return false
    }

    fun updateRssi(rssi: Int): Boolean {
        if (this.rssi != rssi) {
            this.rssi = rssi
            return true
        }
        return false
    }
}

val NO_DEVICE: DeviceItem = DeviceItem("-", "-", 0)