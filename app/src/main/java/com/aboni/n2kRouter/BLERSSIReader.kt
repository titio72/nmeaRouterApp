package com.aboni.n2kRouter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.TimeInput

@SuppressLint("MissingPermission")
class BLERSSIReader(private val gatt: BluetoothGatt) {

    init {
        Handler(Looper.getMainLooper()).postDelayed({ gatt.readRemoteRssi() }, 5000)
    }
}