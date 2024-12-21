package com.aboni.n2kRouter

class Conf {

    companion object {
        const val GPS_INDEX_CONF = 0
        const val DHT_INDEX_CONF = 1
        const val BMP_INDEX_CONF = 2
        const val SYT_INDEX_CONF = 3
        const val RPM_INDEX_CONF = 4
        const val STW_INDEX_CONF = 5

        const val CONF_SIZE = 6
    }

    fun copyFrom(c: Conf) {
        bGPS = c.bGPS
        bSYT = c.bSYT
        bBMP = c.bBMP
        bDHT = c.bDHT
        bRPM = c.bRPM
        bSTW = c.bSYT
    }

    fun copyFrom(v: Int) {
        bGPS = (v and 0x01) != 0
        bBMP = (v and 0x02) != 0
        bDHT = (v and 0x04) != 0
        bSYT = (v and 0x08) != 0
        bSTW = (v and 0x10) != 0
        bRPM = (v and 0x20) != 0
    }

    fun copyFrom(value: ByteArray) {
        if (value.size == CONF_SIZE) {
            bGPS = value[GPS_INDEX_CONF] == '1'.code.toByte()
            bDHT = value[DHT_INDEX_CONF] == '1'.code.toByte()
            bBMP = value[BMP_INDEX_CONF] == '1'.code.toByte()
            bSTW = value[STW_INDEX_CONF] == '1'.code.toByte()
            bSYT = value[SYT_INDEX_CONF] == '1'.code.toByte()
            bRPM = value[RPM_INDEX_CONF] == '1'.code.toByte()
        } else {
            val s = String(value)
            throw RuntimeException("Invalid configuration '$s'")
        }
    }

    fun toByteArray(): ByteArray {
        val v = ByteArray(CONF_SIZE)
        v[GPS_INDEX_CONF] = (if (bGPS) '1' else '0').code.toByte()
        v[BMP_INDEX_CONF] = (if (bBMP) '1' else '0').code.toByte()
        v[DHT_INDEX_CONF] = (if (bDHT) '1' else '0').code.toByte()
        v[SYT_INDEX_CONF] = (if (bSYT) '1' else '0').code.toByte()
        v[STW_INDEX_CONF] = (if (bSTW) '1' else '0').code.toByte()
        v[RPM_INDEX_CONF] = (if (bRPM) '1' else '0').code.toByte()
        return v
    }

    var bGPS: Boolean = false
    var bSYT: Boolean = false
    var bBMP: Boolean = false
    var bDHT: Boolean = false
    var bRPM: Boolean = false
    var bSTW: Boolean = false
}