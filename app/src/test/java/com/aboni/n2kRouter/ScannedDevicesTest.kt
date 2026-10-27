package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedDevicesTest {

    @Test
    fun startsEmpty() {
        assertTrue(ScannedDevices().list.isEmpty())
    }

    @Test
    fun keepsFirstSeenOrderAndUpdatesSignal() {
        val s = ScannedDevices()
        s.update(DeviceItem("A", "AA", -80))
        s.update(DeviceItem("B", "BB", -50))
        s.update(DeviceItem("A", "AA", -40))
        assertEquals(listOf("AA", "BB"), s.list.map { it.id })
        assertEquals(-40, s.list[0].rssi)
    }

    @Test
    fun blankNameDoesNotReplaceKnownName() {
        val s = ScannedDevices()
        s.update(DeviceItem("N2K", "AA", -80))
        s.update(DeviceItem("", "AA", -70))
        assertEquals("N2K", s.list[0].name)
        assertEquals(-70, s.list[0].rssi)
    }
}
