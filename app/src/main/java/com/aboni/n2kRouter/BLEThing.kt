package com.aboni.n2kRouter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.io.FileNotFoundException
import java.util.HashMap
import java.util.UUID

private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
private val CHARACTERISTIC_CONF_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
private val CHARACTERISTIC_DATA_UUID = UUID.fromString("55da66c7-801f-498d-b652-c57cb3f1b590")
private val CHARACTERISTIC_CMD_UUID = UUID.fromString("68ad1094-0989-4e22-9f21-4df7ef390803")
private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

interface BLEThing {
    val data: Data
    val conf: Conf

    fun saveDeviceName(n: String)
    fun saveRPMCalibration(rpm: Int)
    fun saveEngineHours(h: Int, m: Int)
    fun saveRPMAdjustment(value: Double)
    fun saveConfiguration(conf: Conf)

    fun startScan()
    fun stopScan()

    fun setDeviceToConnect(address: String)
    fun connect()
    fun disconnect()

    fun getStatus(): BLELifecycleState
    fun getConnectedDevice(): DeviceItem?
    fun addListener(listener: BLEN2KListener)
    fun refreshConnection()
}

class BLEThingImpl(private val context: Context): BLEThing {

    override val conf = Conf()
    override val data = Data()

    private val listeners: MutableList<BLEN2KListener> = ArrayList()
    private val deviceList: MutableMap<String, BluetoothDevice> = HashMap<String, BluetoothDevice>()
    private var readIndex = 0
    private var subscribeIndex = 0
    private var isScanning = false
    private var connectedGatt: BluetoothGatt? = null
    private var characteristicConf: BluetoothGattCharacteristic? = null
    private var characteristicData: BluetoothGattCharacteristic? = null
    private var characteristicCommand: BluetoothGattCharacteristic? = null


    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private var lifecycleStatus: BLELifecycleState =
        BLELifecycleState.Off
        set(value) {
            field = value
            for (l in listeners) l.onStatus(value, isScanning)
        }

    private var deviceToConnectTo: String? = null
        set(value) {
            field = value
            connect()
        }

    init {
        deviceToConnectTo = readFromFile()
    }

    override fun addListener(listener: BLEN2KListener) {
        listeners.remove(listener)
        listeners.add(listener)
    }


    // region save configuration commands
    @SuppressLint("MissingPermission")
    override fun saveConfiguration(conf: Conf) {
        this.conf.copyFrom(conf)
        val v = conf.toByteArray()
        characteristicConf?.let {
            connectedGatt?.writeCharacteristic(characteristicConf!!, v, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
    }

    @SuppressLint("MissingPermission")
    override fun saveDeviceName(n: String) {
        characteristicCommand?.let {
            connectedGatt?.writeCharacteristic(
                characteristicCommand!!,
                ("N$n").toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun saveRPMCalibration(rpm: Int) {
        characteristicCommand?.let {
            connectedGatt?.writeCharacteristic(
                characteristicCommand!!,
                ("T$rpm").toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun saveEngineHours(h: Int, m: Int) {
        val s = h * 3600 + m * 60
        characteristicCommand?.let {
            connectedGatt?.writeCharacteristic(
                characteristicCommand!!,
                ("H$s").toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun saveRPMAdjustment(value: Double) {
        characteristicCommand?.let {
            val iValue = (value * 10000).toInt()
            connectedGatt?.writeCharacteristic(
                characteristicCommand!!,
                ("t$iValue").toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }
    // endregion

    //region lifecycle

    @SuppressLint("MissingPermission")
    override fun getConnectedDevice(): DeviceItem? {
        return if (connectedGatt==null) null else DeviceItem(connectedGatt!!.device.name, connectedGatt!!.device.address)
    }

    override fun getStatus(): BLELifecycleState {
        return lifecycleStatus
    }

    override fun refreshConnection() {
        if (lifecycleStatus==BLELifecycleState.Off && deviceToConnectTo!=null) {
            connect()
        }
    }

    override fun setDeviceToConnect(address: String) {
        deviceToConnectTo = address
    }

    @SuppressLint("MissingPermission")
    override fun connect() {
        disconnect()
        val d: BluetoothDevice? = if (deviceToConnectTo==null) null else deviceList.getOrDefault(deviceToConnectTo, null)
        if (d!=null) {
            lifecycleStatus = BLELifecycleState.Connect
            d.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        connectedGatt?.disconnect()
        connectedGatt?.close()
        setConnectedGattToNull()
        lifecycleStatus = BLELifecycleState.Off
    }

    @SuppressLint("MissingPermission")
    private fun read(index: Int, gatt: BluetoothGatt) {
        readIndex = index
        if (index == 0) {
            val c = characteristicConf
            if (c != null) {
                appendLog("Request read ${c.uuid}")
                if (!c.isReadable()) {
                    appendLog("ERROR: read failed, characteristic not readable " + c.uuid.toString())
                    return
                }
                gatt.readCharacteristic(c)
            }
        } else {
            readIndex = 0
            characteristicData?.let { subscribeToIndications(it, gatt) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToIndications(characteristic: BluetoothGattCharacteristic, gatt: BluetoothGatt) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor: BluetoothGattDescriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
        val res = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
        appendLog("Subscribed to ${characteristic.uuid} $res")
    }

    private fun setConnectedGattToNull() {
        connectedGatt = null
        characteristicConf = null
        characteristicData = null
        characteristicCommand = null
    }
    //endregion

    //region BLE Scan
    private val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .build()

    @SuppressLint("MissingPermission")
    override fun startScan() {
        if (!isScanning) {
            isScanning = true
            appendLog("Start BLE scanning")
            for (l in listeners) {
                l.onStatus(lifecycleStatus, isScanning)
            }
            bluetoothManager.adapter.bluetoothLeScanner.startScan(
                mutableListOf(scanFilter),
                scanSettings,
                scanCallback
            )
            Handler(Looper.getMainLooper()).postDelayed({ stopScan() }, 5000)
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        if (isScanning) {
            isScanning = false
            appendLog("Stop BLE scanning")
            for (l in listeners) {
                l.onStatus(lifecycleStatus, isScanning)
            }
            bluetoothManager.adapter.bluetoothLeScanner.stopScan(scanCallback)
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name: String? = result.device.name
            val address: String = result.device.address
            val rssi: Int = result.rssi
            deviceList[address] = result.device
            // notify listeners
            for (l in listeners) {
                l.onScan(DeviceItem(name ?: "", address, rssi))
            }
            // if the device is the one previously connected, and the status is disconnected, try to reconnect
            if (deviceToConnectTo==address && getStatus()==BLELifecycleState.Off) {
                appendLog("reconnect to $address $name")
                connect()
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            appendLog("onBatchScanResults, ignoring")
        }

        override fun onScanFailed(errorCode: Int) {
            appendLog("onScanFailed errorCode=$errorCode")
            stopScan()
        }
    }
    //endregion

    //region BLE events, when connected
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // TODO: timeout timer: if this callback not called - disconnect(), wait 120ms, close()
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    appendLog("Connected to $deviceAddress")
                    saveToFile()
                    lifecycleStatus = BLELifecycleState.Discover
                    val res = gatt.requestMtu(128)
                    appendLog("Request mtu $res")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    appendLog("Disconnected from $deviceAddress")
                    setConnectedGattToNull()
                    gatt.close()
                    lifecycleStatus = BLELifecycleState.Off
                }
            } else {
                // TODO: random error 133 - close() and try reconnect
                appendLog("ERROR: onConnectionStateChange status=$status deviceAddress=$deviceAddress, disconnecting")
                setConnectedGattToNull()
                gatt.close()
                lifecycleStatus = BLELifecycleState.Off
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            appendLog("onMtuChanged New MTU $mtu status $status")
            gatt?.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            appendLog("onServicesDiscovered services.count=${gatt.services.size} status=$status")
            if (status == 129 /*GATT_INTERNAL_ERROR*/) {
                // it should be a rare case, this article recommends to disconnect:
                // https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
                appendLog("ERROR: status=129 (GATT_INTERNAL_ERROR), disconnecting")
                gatt.disconnect()
                return
            }
            val service = gatt.getService(SERVICE_UUID) ?: run {
                appendLog("ERROR: Service not found $SERVICE_UUID, disconnecting")
                gatt.disconnect()
                return
            }
            connectedGatt = gatt
            characteristicConf = service.getCharacteristic(CHARACTERISTIC_CONF_UUID)
            characteristicData = service.getCharacteristic(CHARACTERISTIC_DATA_UUID)
            characteristicCommand = service.getCharacteristic(CHARACTERISTIC_CMD_UUID)
            lifecycleStatus = BLELifecycleState.Connected
            read(0, gatt)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (c.uuid.equals(CHARACTERISTIC_CONF_UUID)) {
                conf.copyFrom(value)
                for (l in listeners) l.onConf(conf)
                if (readIndex==0) {
                    read(1, gatt)
                }
            } else if (c.uuid.equals(CHARACTERISTIC_DATA_UUID)) {
                data.parse(value)
                for (l in listeners) l.onData(data)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            when (subscribeIndex) {
                0 -> {
                    // in case there are more chars to subscribe do it here and increment the subscribeIndex
                    gatt!!.readRemoteRssi()
                }
                else -> {
                    subscribeIndex = 0
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray) {
            if (c.uuid.equals(CHARACTERISTIC_DATA_UUID)) {
                data.parse(v)
                for (l in listeners) l.onData(data)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            val d = getConnectedDevice()!!
            d.updateRssi(rssi)
            for (listener in listeners) listener.onRssi(d, rssi)

            Handler(Looper.getMainLooper()).postDelayed({ gatt?.readRemoteRssi() }, 1000)

        }
    }

    private fun saveToFile() {
        val fos = context.openFileOutput("n2k.data", MODE_PRIVATE)
        if (deviceToConnectTo!=null) fos.write(deviceToConnectTo!!.toByteArray(Charsets.UTF_8))
        fos.close()
    }

    private fun readFromFile(): String? {
        try {
            val fis = context.openFileInput("n2k.data")
            val b = fis.readBytes()
            fis.close()
            return String(b)
        } catch (e: FileNotFoundException) {
            return null
        }
    }
    //endregion

    //region BluetoothGattCharacteristic extension
    private fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return (properties and property) != 0
    }
    //endregion
}