package com.aboni.n2kRouter

import com.aboni.marine.link.ble.DeviceStore
import java.io.File

/**
 * The device the user chose, in the file `n2k.data` in [dir] (the app's files directory). Same file and format the app
 * has always used: the bare address, UTF-8, no newline.
 */
class FileDeviceStore(private val dir: File, name: String = "n2k.data") : DeviceStore {
    private val file = File(dir, name)

    override fun load(): String? {
        if (!file.exists()) return null
        return file.readBytes().toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
    }

    override fun save(id: String) {
        file.writeBytes(id.toByteArray(Charsets.UTF_8))
    }
}
