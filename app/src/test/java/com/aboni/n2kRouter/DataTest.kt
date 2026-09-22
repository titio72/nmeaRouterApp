package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELTA = 1e-9

private fun putLE(data: ByteArray, offset: Int, size: Int, value: Long) {
    for (i in 0 until size) {
        data[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }
}

class ByteToIntTest {
    @Test
    fun decodesPositiveLittleEndian() {
        assertEquals(1L, byteToInt(byteArrayOf(1, 0), 0, 2))
        assertEquals(0x0201L, byteToInt(byteArrayOf(1, 2), 0, 2))
    }

    @Test
    fun decodesNegativeTwosComplement() {
        assertEquals(-1L, byteToInt(byteArrayOf(-1, -1), 0, 2))
        assertEquals(-2L, byteToInt(byteArrayOf(-2, -1), 0, 2))
    }

    @Test
    fun respectsOffset() {
        val data = byteArrayOf(0, 0, 10, 0)
        assertEquals(10L, byteToInt(data, 2, 2))
    }

    @Test
    fun returnsNullWhenOutOfBounds() {
        assertNull(byteToInt(byteArrayOf(1, 2), 1, 2))
    }

    @Test
    fun returnsNullWhenOffsetAtOrBeyondBufferSize() {
        // Simulates a UI/firmware version mismatch: the field's offset falls
        // outside a buffer that is shorter than the UI expects.
        val data = byteArrayOf(1, 2, 3)
        assertNull(byteToInt(data, data.size, 2))
        assertNull(byteToInt(data, data.size + 5, 2))
    }
}

class IntValueTest {
    @Test
    fun parsesValueWhenArrayExactlyFitsField() {
        // Regression test: array length == offset+size must be accepted, not rejected.
        val data = ByteArray(4)
        putLE(data, 2, 2, 300)
        val v = Data.IntValue(2, 2, false)
        v.parse(data)
        assertTrue(v.valid)
        assertEquals(300L, v.value)
    }

    @Test
    fun isInvalidWhenArrayOneByteTooShort() {
        val data = ByteArray(3)
        val v = Data.IntValue(2, 2, false)
        v.parse(data)
        assertFalse(v.valid)
    }

    @Test
    fun isInvalidWhenOffsetAtOrBeyondBufferSize() {
        // Older firmware sending a shorter payload than the UI's field table expects
        // must not crash: the field is simply reported as invalid.
        val data = ByteArray(10)
        val atBoundary = Data.IntValue(data.size, 2, false)
        atBoundary.parse(data)
        assertFalse(atBoundary.valid)
        assertEquals(0L, atBoundary.value)

        val wellBeyond = Data.IntValue(data.size + 40, 2, false)
        wellBeyond.parse(data)
        assertFalse(wellBeyond.valid)
    }

    @Test
    fun invalidWhenRawValueMatchesSignedSentinel() {
        // For a signed 2-byte field, 0x7FFF is the reserved "not available" pattern.
        val data = ByteArray(2)
        putLE(data, 0, 2, 0x7FFF)
        val v = Data.IntValue(0, 2, true)
        v.parse(data)
        assertFalse(v.valid)
    }
}

class DoubleValueTest {
    @Test
    fun appliesScaleToRawValue() {
        val data = ByteArray(4)
        putLE(data, 0, 4, 45000000)
        val v = Data.DoubleValue(0, 4, true, 0.000001)
        v.parse(data)
        assertTrue(v.valid)
        assertEquals(45.0, v.value, DELTA)
    }

    @Test
    fun negativeRawValueScalesNegative() {
        val data = ByteArray(2)
        putLE(data, 0, 2, -50)
        val v = Data.DoubleValue(0, 2, true, 0.1)
        v.parse(data)
        assertTrue(v.valid)
        assertEquals(-5.0, v.value, DELTA)
    }

    @Test
    fun keepsDefaultWhenInvalid() {
        val data = ByteArray(1)
        val v = Data.DoubleValue(0, 4, true, 0.1)
        v.parse(data)
        assertFalse(v.valid)
        assertTrue(v.value.isNaN())
    }
}

class TimeValueTest {
    @Test
    fun asTimeReturnsEpochSecondsWhenValid() {
        val data = ByteArray(4)
        putLE(data, 0, 4, 1700000000)
        val v = Data.TimeValue(0)
        v.parse(data)
        assertEquals(1700000000L, v.asTime()?.epochSecond)
    }

    @Test
    fun asTimeReturnsNullWhenInvalid() {
        val data = ByteArray(3)
        val v = Data.TimeValue(0)
        v.parse(data)
        assertNull(v.asTime())
    }
}

class DataParseTest {

    // Layout matches the comment table in Data.kt: total payload size is 80 bytes
    // once the trailing _battery_cap field (offset 78, size 2) is included.
    private fun buildFullPayload(): ByteArray {
        val data = ByteArray(80)
        data[0] = 10 // version
        putLE(data, 1, 1, 3)          // gpsFix
        putLE(data, 2, 4, 101325000)  // atmo -> 101325.0
        putLE(data, 6, 2, -50)        // temp -> -5.0
        putLE(data, 8, 2, 6500)       // hum -> 65.0
        putLE(data, 10, 4, 45000000)  // lat -> 45.0
        putLE(data, 14, 4, -9000000)  // lon -> -9.0
        putLE(data, 18, 4, 123456)    // heap
        putLE(data, 22, 1, 1)         // canActive
        putLE(data, 23, 4, 100)       // canSent
        putLE(data, 27, 4, 2)         // canErrors
        putLE(data, 31, 2, 550)       // sog -> 5.5
        putLE(data, 33, 2, 900)       // cog -> 90.0
        putLE(data, 35, 2, 3000)      // rpm
        putLE(data, 37, 4, 123)       // engineHours
        putLE(data, 41, 4, 1700000000) // utcTime
        putLE(data, 45, 2, 7)         // servicesValue
        putLE(data, 47, 4, 100)       // rpmAdj -> 0.01
        putLE(data, 51, 2, 250)       // current -> 2.5
        putLE(data, 53, 2, 1250)      // volts -> 12.5
        putLE(data, 55, 2, 85)        // soc -> 85.0
        putLE(data, 57, 1, 2)         // n2kSrc
        putLE(data, 58, 2, 500)       // stwPaddle -> 5.0
        putLE(data, 60, 2, 180)       // seaTemp -> 18.0
        putLE(data, 62, 4, 10)        // stwPaddleAdjustment -> 0.1
        putLE(data, 66, 4, 20)        // stwPaddleAlpha -> 0.2
        putLE(data, 70, 4, 30)        // seaTempAdjustment -> 0.3
        putLE(data, 74, 4, 40)        // seaTempAlpha -> 0.4
        putLE(data, 78, 2, 200)       // batteryCapacity
        return data
    }

    @Test
    fun parsesAllFieldsFromFullPayload() {
        val d = Data()
        d.parse(buildFullPayload())

        assertEquals(10, d.version)
        assertTrue(d.gpsFix.valid)
        assertEquals(3L, d.gpsFix.value)
        assertEquals(101325.0, d.atmo.value, DELTA)
        assertEquals(-5.0, d.temp.value, DELTA)
        assertEquals(65.0, d.hum.value, DELTA)
        assertEquals(45.0, d.lat.value, DELTA)
        assertEquals(-9.0, d.lon.value, DELTA)
        assertEquals(5.5, d.sog.value, DELTA)
        assertEquals(90.0, d.cog.value, DELTA)
        assertEquals(3000L, d.rpm.value)
        assertEquals(1700000000L, d.utcTime.asTime()?.epochSecond)
        assertEquals(0.01, d.rpmAdj.value, DELTA)
        assertEquals(2.5, d.current.value, DELTA)
        assertEquals(12.5, d.volts.value, DELTA)
        assertEquals(85.0, d.soc.value, DELTA)
        assertEquals(5.0, d.stwPaddle.value, DELTA)
        assertEquals(18.0, d.seaTemp.value, DELTA)

        // Regression: the last field in the payload must be read, not dropped.
        assertTrue(d.batteryCapacity.valid)
        assertEquals(200L, d.batteryCapacity.value)
    }

    @Test
    fun lastFieldIsInvalidWhenPayloadIsOneByteShort() {
        val d = Data()
        d.parse(buildFullPayload().copyOf(79))
        assertFalse(d.batteryCapacity.valid)
    }

    @Test
    fun lastFieldIsValidWhenPayloadExactlyFitsIt() {
        val d = Data()
        d.parse(buildFullPayload().copyOf(80))
        assertTrue(d.batteryCapacity.valid)
        assertEquals(200L, d.batteryCapacity.value)
    }

    @Test
    fun fieldsWithOffsetBeyondShortLegacyPayloadAreInvalidButEarlierFieldsStillParse() {
        // Simulates a UI/firmware version mismatch: an older firmware sends a
        // payload that ends well before the offset of newer fields (e.g. the UI
        // knows about batteryCapacity at offset 78, but the device only sends 40
        // bytes). This must not crash, and fields that do fit must still parse.
        val legacyPayload = buildFullPayload().copyOf(40)
        val d = Data()
        d.parse(legacyPayload)

        assertEquals(10, d.version)
        assertTrue(d.gpsFix.valid)
        assertTrue(d.lat.valid)
        assertTrue(d.lon.valid)

        assertFalse(d.rpmAdj.valid)      // offset 47, beyond the 40-byte payload
        assertFalse(d.stwPaddle.valid)   // offset 58, beyond the 40-byte payload
        assertFalse(d.batteryCapacity.valid) // offset 78, far beyond the 40-byte payload
        assertEquals(0L, d.batteryCapacity.value)
    }

    @Test
    fun emptyPayloadIsIgnored() {
        val d = Data()
        d.parse(ByteArray(0))
        assertEquals(-1, d.version)
        assertFalse(d.gpsFix.valid)
    }

    @Test
    fun unsupportedVersionIsIgnored() {
        val d = Data()
        val data = buildFullPayload()
        data[0] = 9
        d.parse(data)
        assertEquals(9, d.version)
        assertFalse(d.gpsFix.valid)
        assertFalse(d.batteryCapacity.valid)
    }

    @Test
    fun computesCanSentAndCanErrorsPeriodAcrossParses() {
        val d = Data()
        val first = buildFullPayload()
        putLE(first, 23, 4, 100) // canSent
        putLE(first, 27, 4, 5)   // canErrors
        d.parse(first)
        assertEquals(-1, d.canSentPeriod)
        assertEquals(-1, d.canErrorsPeriod)

        val second = buildFullPayload()
        putLE(second, 23, 4, 140)
        putLE(second, 27, 4, 8)
        d.parse(second)

        assertEquals(40, d.canSentPeriod)
        assertEquals(3, d.canErrorsPeriod)
    }
}
