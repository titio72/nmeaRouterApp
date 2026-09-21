package com.aboni.n2kRouter

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.Timer
import java.util.TimerTask

class MainActivity : BLEN2KListener, BLEApp() {

    private val ble: BLEThing by lazy { BLEThingImpl(this) }
    private val pageAdapter: N2KPagerAdapter by lazy { N2KPagerAdapter(this, ble) }
    private val timer = Timer("BLE connection check", false)

    //region Widgets References
    private val pager: ViewPager2
        get() = findViewById(R.id.pagerView)
    private val statusTxtView: TextView
        get() = findViewById(R.id.textStatus)
    private val btDeviceTxtView: DeviceListItemView
        get() = findViewById(R.id.textBTDevice)
    private val imgConnection: ImageView
        get() = findViewById(R.id.imgConnection)
    //endregion

    //region pages
    private class N2KPagerAdapter(context: Context, ble: BLEThing): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        class N2KViewPlaceHolder(context: Context): FrameLayout(context) {

            private var theView: N2KCardPage?= null

            fun loadContent(v: N2KCardPage) {
                v.tag = "card_content"
                addView(v)
                theView = v
            }
        }

        class N2KViewHolder(val view: N2KViewPlaceHolder): RecyclerView.ViewHolder(view)

        private val scanView:N2KScanView = N2KScanView(context, ble)
        private val settingsView: N2KSettingsView = N2KSettingsView(context, ble)
        private val dataView: N2KDataView = N2KDataView(context, ble)
        private val views = arrayOf( dataView, settingsView, scanView )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = N2KViewPlaceHolder(parent.context)
            v.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return N2KViewHolder(v)
        }

        override fun getItemCount(): Int {
            return views.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (position>=itemCount) throw RuntimeException("Invalid pager position $position")
            val v = views[position]
            (holder as N2KViewHolder).view.loadContent(v)
            v.onActivate()
        }
    }
    //endregion

    //region activity
    override fun onResume() {
        super.onResume()
        grantBluetoothCentralPermissions(AskType.InsistUntilSuccess) {
            success -> appendLog("BLE Permission: $success")
            if (success) {
                ble.startScan()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ble.addListener(this)
        ble.setPasskeyRequestHandler(::askPasskey)

        pager.adapter = pageAdapter
        updateBTView()

        timer.schedule(object: TimerTask() {
            override fun run() {
                ble.sendHeartbeat()
            }
        }, 0, 2500)

        appendLog("MainActivity.onCreate")
    }

    override fun onDestroy() {
        timer.cancel()
        ble.setPasskeyRequestHandler(null)
        ble.disconnect()
        ble.release()
        super.onDestroy()
    }
    //endregion

    //region UI Interaction
    private fun askPasskey(device: DeviceItem, failure: CommandResult?, result: (String?) -> Unit) {
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                result(null)
                return@runOnUiThread
            }
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            var answered = false
            fun answer(value: String?) {
                if (!answered) {
                    answered = true
                    result(value)
                }
            }
            val name = device.name.ifEmpty { device.id }
            val message = if (failure == null) getString(R.string.passkey_message, name)
                else getString(R.string.passkey_message_retry, name, describeCommandResult(this, failure))
            AlertDialog.Builder(this)
                .setTitle(R.string.passkey_title)
                .setMessage(message)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ -> answer(input.text.toString().trim()) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> answer(null) }
                .setOnCancelListener { answer(null) }
                .show()
        }
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        appendLog("status = $status scanning = $scanning")
        runOnUiThread {
            statusTxtView.text = status.toString()
            updateBTView()
            imgConnection.setImageResource(if (scanning) R.drawable.ic_scanning_on else R.drawable.ic_scanning_off)
        }
    }

    private fun updateBTView() {
        btDeviceTxtView.highlighted = ble.getStatus() == BLELifecycleState.Connected
        btDeviceTxtView.deviceItem = ble.getConnectedDevice() ?: NO_DEVICE
    }

    override fun onConf(conf: Conf) {
        // do nothing
    }

    override fun onData(data: Data) {
        // do nothing
    }

    override fun onScan(device: DeviceItem) {
        if (btDeviceTxtView.deviceItem.idMatch(device)) {
            btDeviceTxtView.deviceItem = device
        }
    }

    override fun onRssi(connectedDevice: DeviceItem, rssi: Int) {
        onScan(connectedDevice)
    }

    override fun onCommandResult(result: CommandResult) {
        runOnUiThread {
            Toast.makeText(this, describeCommandResult(this, result), Toast.LENGTH_LONG).show()
        }
    }
    //endregion
}