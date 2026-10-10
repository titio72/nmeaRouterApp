package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCapacityTest {
    @Test
    fun acceptsPositiveIntegersBelowOneThousand() {
        assertEquals(1, parseBatteryCapacity("1"))
        assertEquals(200, parseBatteryCapacity("200"))
        assertEquals(999, parseBatteryCapacity("999"))
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        assertEquals(120, parseBatteryCapacity(" 120 "))
    }

    @Test
    fun rejectsOutOfRangeValues() {
        assertNull(parseBatteryCapacity("0"))
        assertNull(parseBatteryCapacity("-5"))
        assertNull(parseBatteryCapacity("1000"))
    }

    @Test
    fun rejectsNonIntegers() {
        assertNull(parseBatteryCapacity(""))
        assertNull(parseBatteryCapacity("abc"))
        assertNull(parseBatteryCapacity("12.5"))
    }
}
