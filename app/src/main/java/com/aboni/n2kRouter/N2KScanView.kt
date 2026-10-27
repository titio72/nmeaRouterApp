package com.aboni.n2kRouter

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.aboni.marine.link.LinkStatus
import com.aboni.marine.ui.devices.DeviceList
import com.aboni.marine.ui.theme.MarineTheme
import com.aboni.marine.ui.theme.ThemeMode

/**
 * The Devices card: marine-kit's DeviceList of the scanned routers. Tapping one makes it the device to connect to.
 * [status] is the link status shown by the status bar, so both tell the same story.
 */
class N2KScanView(
    context: Context,
    ble: BLEThing?,
    private val status: State<LinkStatus>,
    private val themeMode: ThemeMode,
) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null, mutableStateOf(LinkStatus()), ThemeMode.System)

    //region widgets
    private val deviceListView: ComposeView
        get() = findViewById(R.id.deviceList)
    //endregion

    private val scanned = ScannedDevices()
    private val devices = mutableStateOf(scanned.list)
    private val chosenId = mutableStateOf(ble?.getDeviceToConnect())
    private val scanning = mutableStateOf(false)

    init {
        initView()
        attachCard(R.layout.scan_view)
        setTitleResource(R.string.scan_card_title)
        setImageResource(android.R.drawable.ic_menu_search)

        deviceListView.setContent {
            MarineTheme(themeMode) {
                DeviceList(
                    devices = devices.value,
                    status = status.value,
                    chosenId = chosenId.value,
                    onSelect = ::selectDevice,
                    scanning = scanning.value,
                    emptyText = context.getString(R.string.scan_no_devices),
                )
            }
        }
    }

    override fun onActivate() {
        chosenId.value = ble?.getDeviceToConnect()
    }

    override fun onHeaderImageClicked() {
        ble?.startScan()
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        post { this.scanning.value = scanning }
    }

    override fun onScan(device: DeviceItem) {
        post {
            scanned.update(device)
            devices.value = scanned.list
        }
    }

    private fun selectDevice(id: String) {
        chosenId.value = id
        ble?.setDeviceToConnect(id)
    }
}
