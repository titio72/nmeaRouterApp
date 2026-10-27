package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileDeviceStoreTest {
    @get:Rule val dir = TemporaryFolder()

    @Test
    fun loadReturnsNullWhenMissing() {
        assertNull(FileDeviceStore(dir.root).load())
    }

    @Test
    fun saveThenLoadRoundTrips() {
        FileDeviceStore(dir.root).save("E8:06:90:95:85:29")
        assertEquals("E8:06:90:95:85:29", FileDeviceStore(dir.root).load())
    }

    @Test
    fun readsTheFileTheOldImplementationWrote() {
        // BLEThingImpl wrote the bare address, UTF-8, no newline, in n2k.data.
        File(dir.root, "n2k.data").writeBytes("E8:06:90:95:85:29".toByteArray(Charsets.UTF_8))
        assertEquals("E8:06:90:95:85:29", FileDeviceStore(dir.root).load())
    }

    @Test
    fun writesTheSameBytesTheOldImplementationDid() {
        FileDeviceStore(dir.root).save("E8:06:90:95:85:29")
        assertEquals("E8:06:90:95:85:29", File(dir.root, "n2k.data").readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun anEmptyFileMeansNoDevice() {
        // the old code wrote an empty file when no device was chosen
        File(dir.root, "n2k.data").writeBytes(ByteArray(0))
        assertNull(FileDeviceStore(dir.root).load())
    }
}
