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

    class DoubleValue(sz: Int, sg: Boolean, sc: Double) {
        private var iValue = IntValue(sz, sg)
        private val scale = sc

        var valid = false
        var value = Double.NaN

        fun parse(data: ByteArray, offset: Int): Int {
            val newOffset = iValue.parse(data, offset)
            valid = iValue.valid
            if (valid) value = iValue.value * scale
            return newOffset

        }
    }

    open class IntValue(sz: Int, sg: Boolean) {
        private val size = sz
        private val signed = sg
        var value: Long = 0
        var valid: Boolean = false

        fun parse(data: ByteArray, offset: Int): Int {
            val vv = byteToInt(data, offset, size)
            if (vv==null) {
                valid = false
            } else {
                value = vv
                valid = false
                for (i in 1..<size) {
                    valid = valid or (data[i + offset] != 0xFF.toByte())
                }
                valid = if (signed)
                    valid or (data[offset] != 0x7F.toByte())
                else
                    valid or (data[offset] != 0xFF.toByte())
            }
            return offset + size
        }
    }

    class TimeValue : IntValue(4, false) {
        fun asTime(): Instant? {
            return if (valid) Instant.ofEpochSecond(value) else null
        }
    }

    var gpsFix = IntValue(1, false)
    var atmo = DoubleValue(4, false, 0.001)
    var temp = DoubleValue(2, true, 0.1)
    var hum = DoubleValue(2, true, 0.01)
    var lat = DoubleValue(4, true, 0.000001)
    var lon = DoubleValue(4, true, 0.000001)
    var sog = DoubleValue(2, true, 0.01)
    var cog = DoubleValue(2, true, 0.1)
    var soc = DoubleValue(2, true, 1.0)
    var volts = DoubleValue(2, true, 0.01)
    var current = DoubleValue(2, true, 0.01)
    var rpm = IntValue(2, false)
    var canErrors = IntValue(4, false)
    var canSent = IntValue(4, false)
    var heap = IntValue(4, false)
    var canActive = IntValue(1, false)
    var engineHours = IntValue(4, false)
    var utcTime = TimeValue()
    var svc = IntValue(1, false)
    var rpmAdj = DoubleValue(4, false, 0.0001)
    var n2kSrc = IntValue(1, false)

    var canSentPeriod = -1
    var canErrorsPeriod = -1
    var lastCanErrors = -1
    var lastCanSent = -1

    fun parse(data: ByteArray) {
        var offset = 0
        offset = gpsFix.parse(data, offset)
        offset = atmo.parse(data, offset)
        offset = temp.parse(data, offset)
        offset = hum.parse(data, offset)
        offset = lat.parse(data, offset)
        offset = lon.parse(data, offset)
        offset = heap.parse(data, offset)
        offset = canActive.parse(data, offset)
        offset = canSent.parse(data, offset)
        offset = canErrors.parse(data, offset)
        offset = sog.parse(data, offset)
        offset = cog.parse(data, offset)
        offset = rpm.parse(data, offset)
        offset = engineHours.parse(data, offset)
        offset = utcTime.parse(data, offset)
        offset = svc.parse(data, offset)
        offset = rpmAdj.parse(data, offset)
        offset = current.parse(data, offset)
        offset = volts.parse(data, offset)
        offset = soc.parse(data, offset)
        n2kSrc.parse(data, offset)


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
}