package com.aboni.n2kRouter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import androidx.core.view.size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class N2KScanView(context: Context, ble: BLEThing?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null)
    //region widgets
    private val deviceListView: RecyclerView
        get() = findViewById(R.id.deviceList)
    //endregion

    private interface OnDeviceClickedListener {
        fun onDeviceClicked(d: DeviceListItemView)
    }

    //region list adapter
    private class DeviceItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val theView: DeviceListItemView = view as DeviceListItemView
    }

    private class DeviceListCustomAdapter(private val dataSet: List<DeviceItem>, private val listener: OnDeviceClickedListener, private val ble: BLEThing?): RecyclerView.Adapter<DeviceItemViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): DeviceItemViewHolder {
            val view = DeviceListItemView(viewGroup.context)
            view.setLayoutParams(
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            return DeviceItemViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: DeviceItemViewHolder, position: Int) {
            viewHolder.theView.deviceItem = dataSet[position]
            if (ble!=null)
                viewHolder.theView.highlighted = if (ble.getConnectedDevice()!=null) dataSet[position].idMatch(ble.getConnectedDevice()!!) else false
            else
                viewHolder.theView.highlighted = false
            viewHolder.theView.setOnClickListener { v ->
                run {
                    listener.onDeviceClicked(v as DeviceListItemView)
                }
            }
        }

        override fun onViewRecycled(holder: DeviceItemViewHolder) {
            holder.theView.setOnClickListener(null)
        }

        override fun getItemCount() = dataSet.size
    }


    private val devicesList: MutableList<DeviceItem> = ArrayList()
    private val deviceListAdapter =  DeviceListCustomAdapter(devicesList, object: OnDeviceClickedListener {
        override fun onDeviceClicked(d: DeviceListItemView) {
            setDevice(d)
        }
    }, ble)
    //endregion

    init {
        initView()
        attachCard(R.layout.scan_view)
        setTitleResource(R.string.scan_card_title)
        setImageResource(android.R.drawable.ic_menu_search)

        deviceListView.layoutManager = LinearLayoutManager(context)
        deviceListView.adapter = deviceListAdapter
   }

    override fun onActivate() {
        highlightDevice(ble?.getConnectedDevice()?.id)
    }

    override fun onHeaderImageClicked() {
        ble?.startScan()
        resetDevices()
    }

    private fun resetDevices() {
        for (device in devicesList) {
            device.updateRssi(-100)
            for (j in 0..<deviceListView.size) {
                val dView: DeviceListItemView = (deviceListView[j] as DeviceListItemView)
                if (dView.deviceItem.idMatch(device)) {
                    dView.deviceItem = device
                }
            }
        }
    }

    override fun onScan(device: DeviceItem) {
        for (i in 0..<devicesList.size) {
            if (devicesList[i].idMatch(device)) {
                for (j in 0..<deviceListView.size) {
                    val dView: DeviceListItemView = (deviceListView[j] as DeviceListItemView)
                    if (dView.deviceItem.idMatch(device)) {
                        dView.deviceItem = device
                    }
                }
                return
            }
        }
        devicesList.add(device)
        deviceListAdapter.notifyItemInserted(devicesList.size - 1)
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        if (status==BLELifecycleState.Connected) {
            highlightDevice(ble?.getConnectedDevice()?.id)
        }
    }

    private fun highlightDevice(id: String?) {
        for (i in 0..<deviceListView.size) {
            val dView: DeviceListItemView = (deviceListView[i] as DeviceListItemView)
            dView.highlighted = if (id!=null) (dView.deviceItem.id==id) else false
        }
    }

    private fun setDevice(d: DeviceListItemView) {
        highlightDevice(d.deviceItem.id)
        ble?.setDeviceToConnect(d.deviceItem.id)
    }
}