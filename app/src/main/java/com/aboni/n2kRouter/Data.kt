package com.aboni.n2kRouter

import java.time.Instant

fun byteToInt(v: ByteArray, offset: Int, size: Int): Long? {
    if ((size+offset)>v.size) return null
    val bytes = v.copyOfRange(offset, offset + size)
    var result = 0L
    var shift = 0
    val negative = bytes[bytes.size-1] < 0
    for (byte in bytes) {
        var ub: UByte = byte.toUByte()
        if (negative) ub = ub.inv()
        result = result or ((ub.toInt() shl shift).toLong())
        shift += 8
    }
    return if (negative)
        -(result+1)
    else
        result
}

class Data {

    class DoubleValue(offset: Int, sz: Int, sg: Boolean, sc: Double) {
        private var iValue = IntValue(offset, sz,sg)
        private val scale = sc

        var valid = false
        var value = Double.NaN

        fun parse(data: ByteArray) {
            iValue.parse(data)
            valid = iValue.valid
            if (valid) value = iValue.value * scale
        }
    }

    open class IntValue(val offset: Int, val size: Int, val signed: Boolean) {
        var value: Long = 0
        var valid: Boolean = false

        fun parse(data: ByteArray) {
            if (data.size<=(offset+size)) {
                valid = false
                return
            }
            val vv = byteToInt(data, offset, size)
            if (vv==null) {
                valid = false
            } else {
                value = vv
                val invalid = if (signed) (1L shl (size * 8 - 1)) - 1   // 0x7F.. for signed
                else (1L shl (size * 8)) - 1                            // 0xFF.. for unsigned
                valid = vv != invalid
            }
        }
    }

    class TimeValue(offset: Int) : IntValue(offset,4, false) {
        fun asTime(): Instant? {
            return if (valid) Instant.ofEpochSecond(value) else null
        }
    }
    /*
    Field	        Start	Size	Type	    End
    version	        0	    1	    uint8_t	    1
    _gpsFix	        1	    1	    int8_t	    2
    _atmo	        2	    4	    uint32_t	6
    _temp	        6	    2	    int16_t	    8
    _hum	        8	    2	    int16_t	    10
    _lat	        10	    4	    int32_t	    14
    _lon	        14	    4	    int32_t	    18
    _mem	        18	    4	    int32_t	    22
    _canbus	        22	    1	    int8_t	    23
    _canbus_s	    23	    4	    int32_t	    27
    _canbus_e	    27	    4	    int32_t	    31
    _sog	        31	    2	    int16_t	    33
    _cog	        33	    2	    int16_t	    35
    _rpm	        35	    2	    uint16_t	37
    _engine_time	37	    4	    uint32_t	41
    _timestamp	    41	    4	    int32_t	    45
    _services	    45	    2	    uint16_t	47
    _rpmAdj	        47	    4	    uint32_t	51
    _current	    51	    2	    int16_t	    53
    _voltage	    53	    2	    int16_t	    55
    _soc	        55	    2	    int16_t	    57
    _n2k_source	    57	    1	    uint8_t	    58
    _stw	        58	    2	    int16_t	    60
    _water_temp	    60	    2	    int16_t	    62
    _stw_adjustment	62	    4	    uint32_t	66
    _stw_alpha	    66	    4	    uint32_t	70
    _sea_temp_adju	70	    4	    uint32_t	74
    _sea_temp_alpha	74	    4	    uint32_t	78

    */
    var gpsFix = IntValue(1, 1, false)
    var atmo = DoubleValue(2, 4, false, 0.001)
    var temp = DoubleValue(6, 2, true, 0.1)
    var hum = DoubleValue(8,2, true, 0.01)
    var lat = DoubleValue(10,4, true, 0.000001)
    var lon = DoubleValue(14,4, true, 0.000001)
    var sog = DoubleValue(31,2, true, 0.01)
    var cog = DoubleValue(33,2, true, 0.1)
    var soc = DoubleValue(55,2, true, 1.0)
    var volts = DoubleValue(53,2, true, 0.01)
    var current = DoubleValue(51,2, true, 0.01)
    var rpm = IntValue(35,2, false)
    var canErrors = IntValue(27,4, false)
    var canSent = IntValue(23,4, false)
    var heap = IntValue(18,4, false)
    var canActive = IntValue(22,1, false)
    var engineHours = IntValue(37,4, false)
    var utcTime = TimeValue(41)
    var servicesValue = IntValue(45,2, false)
    var rpmAdj = DoubleValue(47,4, false, 0.0001)
    var n2kSrc = IntValue(57,1, false)

    var stwPaddle = DoubleValue(58,2, true, 0.01)
    var seaTemp = DoubleValue(60,2, true, 0.1)

    var stwPaddleAdjustment = DoubleValue(62,4, false, 0.01)
    var stwPaddleAlpha = DoubleValue(66,4, false, 0.01)

    var seaTempAdjustment = DoubleValue(70 , 4, false, 0.01)

    var seaTempAlpha = DoubleValue(74, 4, false, 0.01)

    var canSentPeriod = -1
    var canErrorsPeriod = -1
    var lastCanErrors = -1
    var lastCanSent = -1

    var version = -1

    fun parse(data: ByteArray) {
        if (data.isEmpty()) {
            appendLog("WARNING: empty telemetry payload")
            return
        }

        version = data[0].toInt()
        if (version < 10) {
            appendLog("WARNING: unsupported telemetry payload version=$version")
            return
        }

        gpsFix.parse(data)
        atmo.parse(data)
        temp.parse(data)
        hum.parse(data)
        lat.parse(data)
        lon.parse(data)
        heap.parse(data)
        canActive.parse(data)
        canSent.parse(data)
        canErrors.parse(data)
        sog.parse(data)
        cog.parse(data)
        rpm.parse(data)
        engineHours.parse(data)
        utcTime.parse(data)
        servicesValue.parse(data)
        rpmAdj.parse(data)
        current.parse(data)
        volts.parse(data)
        soc.parse(data)
        n2kSrc.parse(data)
        stwPaddle.parse(data)
        seaTemp.parse(data)
        stwPaddleAdjustment.parse(data)
        stwPaddleAlpha.parse(data)
        seaTempAlpha.parse(data)
        seaTempAdjustment.parse(data)

        if (canSent.valid) {
            if (lastCanSent != -1) {
                canSentPeriod = canSent.value.toInt() - lastCanSent
            }
            lastCanSent = canSent.value.toInt()
        }

        if (canErrors.valid) {
            if (lastCanErrors != -1) {
                canErrorsPeriod = canErrors.value.toInt() - lastCanErrors
            }
            lastCanErrors = canErrors.value.toInt()
        }
    }

    fun getServices(): IntValue {
        return servicesValue
    }
}