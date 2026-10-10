package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataParseTest {

    private fun payload(): ByteArray {
        val b = ByteArray(83) { 0 }
        b[0] = 11
        return b
    }

    private fun putInt16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    @Test
    fun invalidSigned16FieldsAreNotValid() {
        val b = payload()
        for (off in intArrayOf(51, 53, 55, 60, 81)) putInt16(b, off, 0x7FFF)
        val d = Data()
        d.parse(b)
        assertFalse(d.current.valid)
        assertFalse(d.volts.valid)
        assertFalse(d.soc.valid)
        assertFalse(d.seaTemp.valid)
        assertFalse(d.magneticDeviation.valid)
    }

    @Test
    fun validSigned16FieldsParse() {
        val b = payload()
        putInt16(b, 53, 1234)
        putInt16(b, 60, -50)
        val d = Data()
        d.parse(b)
        assertTrue(d.volts.valid)
        assertEquals(12.34, d.volts.value, 1e-9)
        assertTrue(d.seaTemp.valid)
        assertEquals(-5.0, d.seaTemp.value, 1e-9)
    }
}
