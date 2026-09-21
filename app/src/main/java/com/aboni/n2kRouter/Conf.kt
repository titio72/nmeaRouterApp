package com.aboni.n2kRouter

class Conf {

    companion object {
        const val GPS_INDEX_CONF = 0
        const val DHT_INDEX_CONF = 1
        const val BME_INDEX_CONF = 2
        const val SYT_INDEX_CONF = 3
        const val RPM_INDEX_CONF = 4
        const val STW_INDEX_CONF = 5
        const val VED_INDEX_CONF = 6
        const val KEEP_N2K_SRC_INDEX_CONF = 7
        const val SEA_TEMP_INDEX_CONF = 8
        const val STW_PADDLE_INDEX_CONF = 9
        const val LOG_INDEX_CONF = 10
        const val CONF_SIZE = 11
    }

    fun copyFrom(c: Conf) {
        bGPS = c.bGPS
        bSYT = c.bSYT
        bBME = c.bBME
        bDHT = c.bDHT
        bRPM = c.bRPM
        bSTW = c.bSTW
        bVED = c.bVED
        bSRC = c.bSRC
        bSEA_TEMP = c.bSEA_TEMP
        bSTW_PADDLE = c.bSTW_PADDLE
        bLOG = c.bLOG
    }

    fun copyFrom(v: Int) {
        bGPS = (v and 0x0001) != 0
        bDHT = (v and 0x0002) != 0
        bBME = (v and 0x0004) != 0
        bSYT = (v and 0x0008) != 0
        bRPM = (v and 0x0010) != 0
        bSTW = (v and 0x0020) != 0
        bVED = (v and 0x0040) != 0
        bSRC = (v and 0x0080) != 0
        bSEA_TEMP = (v and 0x0100) != 0
        bSTW_PADDLE = (v and 0x0200) != 0
        bLOG = (v and 0x400) != 0
    }

    fun copyFrom(value: ByteArray) {
        //if (value.size == CONF_SIZE) {
        bGPS = if (GPS_INDEX_CONF<value.size) value[GPS_INDEX_CONF] == '1'.code.toByte() else false
        bDHT = if (DHT_INDEX_CONF<value.size) value[DHT_INDEX_CONF] == '1'.code.toByte() else false
        bBME = if (BME_INDEX_CONF<value.size) value[BME_INDEX_CONF] == '1'.code.toByte() else false
        bSTW = if (STW_INDEX_CONF<value.size) value[STW_INDEX_CONF] == '1'.code.toByte() else false
        bSYT = if (SYT_INDEX_CONF<value.size) value[SYT_INDEX_CONF] == '1'.code.toByte() else false
        bRPM = if (RPM_INDEX_CONF<value.size) value[RPM_INDEX_CONF] == '1'.code.toByte() else false
        bVED = if (VED_INDEX_CONF<value.size) value[VED_INDEX_CONF] == '1'.code.toByte() else false
        bSRC = if (KEEP_N2K_SRC_INDEX_CONF<value.size) value[KEEP_N2K_SRC_INDEX_CONF] == '1'.code.toByte() else false
        bSEA_TEMP = if (SEA_TEMP_INDEX_CONF<value.size) value[SEA_TEMP_INDEX_CONF] == '1'.code.toByte() else false
        bSTW_PADDLE = if (STW_PADDLE_INDEX_CONF<value.size) value[STW_PADDLE_INDEX_CONF] == '1'.code.toByte() else false
        bLOG = if (LOG_INDEX_CONF<value.size) value[LOG_INDEX_CONF] == '1'.code.toByte() else false
        //} else {
        //    val s = String(value)
        //    throw RuntimeException("Invalid configuration '$s'")
        //}
    }

    fun toByteArray(): ByteArray {
        val v = ByteArray(CONF_SIZE)
        v[GPS_INDEX_CONF] = (if (bGPS) '1' else '0').code.toByte()
        v[BME_INDEX_CONF] = (if (bBME) '1' else '0').code.toByte()
        v[DHT_INDEX_CONF] = (if (bDHT) '1' else '0').code.toByte()
        v[SYT_INDEX_CONF] = (if (bSYT) '1' else '0').code.toByte()
        v[STW_INDEX_CONF] = (if (bSTW) '1' else '0').code.toByte()
        v[RPM_INDEX_CONF] = (if (bRPM) '1' else '0').code.toByte()
        v[VED_INDEX_CONF] = (if (bVED) '1' else '0').code.toByte()
        v[KEEP_N2K_SRC_INDEX_CONF] = (if (bSRC) '1' else '0').code.toByte()
        v[SEA_TEMP_INDEX_CONF] = (if (bSEA_TEMP) '1' else '0').code.toByte()
        v[STW_PADDLE_INDEX_CONF] = (if (bSTW_PADDLE) '1' else '0').code.toByte()
        v[LOG_INDEX_CONF] = (if (bLOG) '1' else '0').code.toByte()
        return v
    }

    var bVED: Boolean = false
    var bGPS: Boolean = false
    var bSYT: Boolean = false
    var bDHT: Boolean = false
    var bRPM: Boolean = false
    var bSTW: Boolean = false
    var bBME: Boolean = false
    var bSRC: Boolean = false
    var bSEA_TEMP: Boolean = false
    var bSTW_PADDLE: Boolean = false
    var bLOG: Boolean = false
}