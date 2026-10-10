package com.aboni.n2kRouter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
import java.util.ArrayDeque
import java.util.HashMap
import java.util.UUID

private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
private val CHARACTERISTIC_CONF_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
private val CHARACTERISTIC_DATA_UUID = UUID.fromString("55da66c7-801f-498d-b652-c57cb3f1b590")
private val CHARACTERISTIC_CMD_UUID = UUID.fromString("68ad1094-0989-4e22-9f21-4df7ef390803")
private val CHARACTERISTIC_HEARTBEAT_UUID = UUID.fromString("31a627d4-90cd-43df-8c0d-460e77fd294b")
private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private const val GATT_INSUFFICIENT_AUTHENTICATION = 5
private const val GATT_INSUFFICIENT_ENCRYPTION = 15
private const val GATT_AUTH_FAIL = 137
private const val COMMAND_TIMEOUT_MS = 15000L
private const val EXTRA_BOND_REASON = "android.bluetooth.device.extra.REASON" // hidden in BluetoothDevice
private const val MAX_BUSY_RETRIES = 20
private const val BUSY_RETRY_DELAY_MS = 100L
private const val PAIRING_VARIANT_PASSKEY = 1 // hidden in BluetoothDevice

/**
 * Asks the user for the passkey of [device]. Called on the UI thread; [result] must be invoked
 * (on any thread) with the passkey, or null if the user gave up. [failure] is why the previous passkey was
 * rejected, null when it is the first request.
 */
typealias PasskeyRequestHandler = (device: DeviceItem, failure: CommandResult?, result: (String?) -> Unit) -> Unit

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
    fun sendHeartbeat()
    fun saveSTWAdjustment(value: Double)
    fun saveSTWAlpha(value: Double)
    fun saveSeaTempAdjustment(value: Double)
    fun saveSeaTempAlpha(value: Double)
    fun saveBatteryCapacity(ah: Int)

    fun setPasskeyRequestHandler(handler: PasskeyRequestHandler?)
    fun release()
}

class BLEThingImpl(private val context: Context) : BLEThing {

    var hostVersion: Int = 0

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
    private var characteristicHeartbeat: BluetoothGattCharacteristic? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val passkeyStore = PasskeyStore(context)
    private var passkeyRequestHandler: PasskeyRequestHandler? = null

    // write commands are serialized; all the queue state is touched on the main thread only
    private val commandQueue = ArrayDeque<String>()
    private var commandInFlight: String? = null
    private var busyRetries = 0
    private var waitingForPasskey = false
    private var commandWatchdog: Runnable? = null
    private var lastBondFailure = CommandResult.NO_BOND_FAILURE

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

    override fun setPasskeyRequestHandler(handler: PasskeyRequestHandler?) {
        passkeyRequestHandler = handler
    }

    override fun release() {
        try {
            context.unregisterReceiver(pairingReceiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered
        }
    }

    override fun addListener(listener: BLEN2KListener) {
        listeners.remove(listener)
        listeners.add(listener)
    }

    // region save configuration commands
    /** Queues a write on the command characteristic; it is sent once a passkey is available. */
    private fun writeCommand(payload: String): Boolean {
        if (connectedGatt == null || characteristicCommand == null) {
            appendLog("WARN: dropping command '$payload' (not connected)")
            report(CommandResult(CommandResult.Kind.NotConnected))
            return false
        }
        mainHandler.post {
            commandQueue.add(payload)
            pumpCommands()
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun pumpCommands() {
        if (commandInFlight != null || waitingForPasskey) return
        val payload = commandQueue.peek() ?: return
        val gatt = connectedGatt
        val cmd = characteristicCommand
        if (gatt == null || cmd == null) {
            appendLog("WARN: dropping ${commandQueue.size} queued command(s) (not connected)")
            commandQueue.clear()
            report(CommandResult(CommandResult.Kind.NotConnected))
            return
        }
        val address = gatt.device.address
        if (passkeyStore.get(address) == null) {
            requestPasskey(gatt.device, null)
            return
        }
        val status = gatt.writeCharacteristic(
            cmd,
            payload.toByteArray(Charsets.UTF_8),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        when (status) {
            BluetoothStatusCodes.SUCCESS -> {
                busyRetries = 0
                commandInFlight = commandQueue.poll()
                startCommandWatchdog(gatt.device)
            }
            BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> {
                if (++busyRetries <= MAX_BUSY_RETRIES) {
                    mainHandler.postDelayed({ pumpCommands() }, BUSY_RETRY_DELAY_MS)
                } else {
                    appendLog("WARN: dropping command '$payload', gatt busy")
                    busyRetries = 0
                    commandQueue.poll()
                    report(CommandResult(CommandResult.Kind.GattError, status))
                    pumpCommands()
                }
            }
            else -> {
                appendLog("WARN: failed to queue command '$payload' status=$status")
                commandQueue.poll()
                report(CommandResult(CommandResult.Kind.GattError, status))
                pumpCommands()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPasskey(device: BluetoothDevice, failure: CommandResult?) {
        val handler = passkeyRequestHandler
        if (handler == null) {
            appendLog("WARN: passkey needed but no handler, dropping ${commandQueue.size} queued command(s)")
            commandQueue.clear()
            report(failure ?: CommandResult(CommandResult.Kind.PasskeyCancelled))
            return
        }
        waitingForPasskey = true
        val item = DeviceItem(device.name ?: "", device.address)
        appendLog("Asking passkey for ${device.address} failure=${failure?.kind}")
        handler(item, failure) { passkey ->
            mainHandler.post {
                waitingForPasskey = false
                if (passkey.isNullOrEmpty()) {
                    appendLog("Passkey not provided, dropping ${commandQueue.size} queued command(s)")
                    commandQueue.clear()
                    report(CommandResult(CommandResult.Kind.PasskeyCancelled))
                } else {
                    lastBondFailure = CommandResult.NO_BOND_FAILURE
                    passkeyStore.put(device.address, passkey)
                    pumpCommands()
                }
            }
        }
    }

    /** A write was refused for security reasons: forget the passkey and the bond, then ask again. */
    @SuppressLint("MissingPermission")
    private fun onCommandSecurityError(device: BluetoothDevice, status: Int) {
        appendLog("ERROR: security error writing command to ${device.address} status=$status bondState=${device.bondState} lastBondFailure=$lastBondFailure")
        cancelCommandWatchdog()
        commandInFlight?.let { commandQueue.addFirst(it) }
        commandInFlight = null
        passkeyStore.remove(device.address)
        removeBond(device)
        val failure = CommandResult(CommandResult.Kind.PasskeyRejected, status, lastBondFailure)
        lastBondFailure = CommandResult.NO_BOND_FAILURE
        requestPasskey(device, failure)
    }

    private fun report(result: CommandResult) {
        mainHandler.post {
            appendLog("Command result ${result.kind} code=${result.code} bondFailure=${result.bondFailure}")
            for (l in listeners) l.onCommandResult(result)
        }
    }

    /** No callback for the write in flight (e.g. pairing stalled): don't leave the queue blocked and the user in the dark. */
    private fun startCommandWatchdog(device: BluetoothDevice) {
        cancelCommandWatchdog()
        val r = Runnable {
            commandWatchdog = null
            appendLog("ERROR: no answer to command '$commandInFlight' after ${COMMAND_TIMEOUT_MS}ms")
            if (lastBondFailure != CommandResult.NO_BOND_FAILURE) {
                onCommandSecurityError(device, BluetoothGatt.GATT_FAILURE)
            } else {
                commandInFlight = null
                report(CommandResult(CommandResult.Kind.Timeout))
                pumpCommands()
            }
        }
        commandWatchdog = r
        mainHandler.postDelayed(r, COMMAND_TIMEOUT_MS)
    }

    private fun cancelCommandWatchdog() {
        commandWatchdog?.let { mainHandler.removeCallbacks(it) }
        commandWatchdog = null
    }

    @SuppressLint("MissingPermission")
    private fun removeBond(device: BluetoothDevice) {
        if (device.bondState != BluetoothDevice.BOND_BONDED) return
        try {
            // BluetoothDevice.removeBond() is not part of the public SDK
            val res = device.javaClass.getMethod("removeBond").invoke(device)
            appendLog("removeBond ${device.address} -> $res")
        } catch (e: Exception) {
            appendLog("WARN: unable to remove bond ${device.address}: ${e.message}")
        }
    }

    private val pairingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                val reason = intent.getIntExtra(EXTRA_BOND_REASON, CommandResult.NO_BOND_FAILURE)
                appendLog("Bond state ${device.address} $previous -> $state reason=$reason")
                if (device.address == deviceToConnectTo) {
                    if (state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING) {
                        lastBondFailure = reason
                    } else if (state == BluetoothDevice.BOND_BONDED) {
                        lastBondFailure = CommandResult.NO_BOND_FAILURE
                    }
                }
                return
            }
            val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
            appendLog("Pairing request from ${device.address} variant=$variant")
            if (variant != BluetoothDevice.PAIRING_VARIANT_PIN && variant != PAIRING_VARIANT_PASSKEY) return
            // only answer for the device we are talking to; otherwise let the system dialog show up
            if (device.address != deviceToConnectTo) return
            val passkey = passkeyStore.get(device.address) ?: return
            if (device.setPin(passkey.toByteArray(Charsets.UTF_8))) {
                appendLog("Pairing request from ${device.address} answered with the stored passkey")
                abortBroadcast()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun saveConfiguration(conf: Conf) {
        this.conf.copyFrom(conf)
        val v = conf.toByteArray()
        val s = String(v)
        writeCommand("S$s")
    }

    @SuppressLint("MissingPermission")
    override fun saveDeviceName(n: String) {
        writeCommand("N$n")
    }

    @SuppressLint("MissingPermission")
    override fun saveRPMCalibration(rpm: Int) {
        writeCommand("T$rpm")
    }

    @SuppressLint("MissingPermission")
    override fun saveEngineHours(h: Int, m: Int) {
        val s = h * 3600 + m * 60
        writeCommand("H$s")
    }

    @SuppressLint("MissingPermission")
    override fun saveRPMAdjustment(value: Double) {
        val scale = 100
        val iValue = (value * scale).toInt()
        writeCommand("t$iValue")
    }

    @SuppressLint("MissingPermission")
    override fun sendHeartbeat() {
        // the heartbeat characteristic does not require pairing, so it bypasses the command queue
        val gatt = connectedGatt
        val hb = characteristicHeartbeat
        if (gatt == null || hb == null) return
        val status = gatt.writeCharacteristic(
            hb,
            "h".toByteArray(Charsets.UTF_8),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        if (status != BluetoothStatusCodes.SUCCESS) {
            appendLog("WARN: failed to queue heartbeat status=$status")
        }
    }

    override fun saveSTWAdjustment(value: Double) {
        val scale = 100
        val iValue = (value * scale).toInt()
        writeCommand("s$iValue")
    }

    override fun saveSTWAlpha(value: Double) {
        val scale = 100
        val iValue = (value * scale).toInt()
        writeCommand("a$iValue")
    }

    override fun saveSeaTempAdjustment(value: Double) {
        val scale = 100
        val iValue = (value * scale).toInt()
        writeCommand("w$iValue")
    }

    override fun saveSeaTempAlpha(value: Double) {
        val scale = 100
        val iValue = (value * scale).toInt()
        writeCommand("x$iValue")
    }

    override fun saveBatteryCapacity(ah: Int) {
        writeCommand("B$ah")
    }
    // endregion

    init {
        // declared after pairingReceiver so that it is initialized
        val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        context.registerReceiver(pairingReceiver, filter, Context.RECEIVER_EXPORTED)
    }

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
            transitionLifecycleStatus(BLELifecycleState.Connect)
            d.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        connectedGatt?.disconnect()
        connectedGatt?.close()
        setConnectedGattToNull()
        transitionLifecycleStatus(BLELifecycleState.Off)
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

    @Synchronized
    private fun setConnectedGattToNull() {
        connectedGatt = null
        characteristicConf = null
        characteristicData = null
        characteristicCommand = null
        characteristicHeartbeat = null
        mainHandler.post {
            cancelCommandWatchdog()
            if (commandQueue.isNotEmpty() || commandInFlight != null) {
                appendLog("WARN: connection lost, dropping ${commandQueue.size} queued command(s) and in flight '$commandInFlight'")
                report(CommandResult(CommandResult.Kind.NotConnected))
            }
            commandQueue.clear()
            commandInFlight = null
        }
    }

    @Synchronized
    private fun setConnectedGatt(gatt: BluetoothGatt, service: android.bluetooth.BluetoothGattService) {
        connectedGatt = gatt
        characteristicConf = service.getCharacteristic(CHARACTERISTIC_CONF_UUID)
        characteristicData = service.getCharacteristic(CHARACTERISTIC_DATA_UUID)
        characteristicCommand = service.getCharacteristic(CHARACTERISTIC_CMD_UUID)
        characteristicHeartbeat = service.getCharacteristic(CHARACTERISTIC_HEARTBEAT_UUID)
    }

    @Synchronized
    private fun transitionLifecycleStatus(status: BLELifecycleState) {
        if (lifecycleStatus != status) {
            appendLog("lifecycle ${lifecycleStatus} -> $status")
        }
        lifecycleStatus = status
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
                    transitionLifecycleStatus(BLELifecycleState.Discover)
                    val res = gatt.requestMtu(128)
                    appendLog("Request mtu $res")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    appendLog("Disconnected from $deviceAddress")
                    setConnectedGattToNull()
                    gatt.close()
                    transitionLifecycleStatus(BLELifecycleState.Off)
                }
            } else {
                // TODO: random error 133 - close() and try reconnect
                appendLog("ERROR: onConnectionStateChange status=$status deviceAddress=$deviceAddress, disconnecting")
                setConnectedGattToNull()
                gatt.close()
                transitionLifecycleStatus(BLELifecycleState.Off)
            }
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
            setConnectedGatt(gatt, service)
            transitionLifecycleStatus(BLELifecycleState.Connected)
            read(0, gatt)
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            appendLog("onMtuChanged New MTU $mtu status $status")
            gatt?.discoverServices()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (c.uuid.equals(CHARACTERISTIC_CONF_UUID)) {
                conf.copyFrom(value)
                for (l in listeners) l.onConf(conf)
                if (readIndex==0) {
                    read(readIndex + 1, gatt)
                }
            } else if (c.uuid.equals(CHARACTERISTIC_DATA_UUID)) {
                hostVersion = value[0].toInt()
                data.parse(value)
                for (l in listeners) l.onData(data)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (!c.uuid.equals(CHARACTERISTIC_CMD_UUID)) {
                if (status != BluetoothGatt.GATT_SUCCESS) appendLog("WARN: write ${c.uuid} status=$status")
                return
            }
            mainHandler.post {
                if (commandInFlight == null) {
                    appendLog("WARN: unexpected command write callback status=$status")
                    return@post
                }
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        cancelCommandWatchdog()
                        commandInFlight = null
                        if (commandQueue.isEmpty()) report(CommandResult(CommandResult.Kind.Sent))
                        pumpCommands()
                    }
                    GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_ENCRYPTION, GATT_AUTH_FAIL ->
                        onCommandSecurityError(gatt.device, status)
                    else -> {
                        appendLog("WARN: command '$commandInFlight' failed status=$status")
                        cancelCommandWatchdog()
                        commandInFlight = null
                        report(CommandResult(CommandResult.Kind.GattError, status))
                        pumpCommands()
                    }
                }
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
        } catch (_: FileNotFoundException) {
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


