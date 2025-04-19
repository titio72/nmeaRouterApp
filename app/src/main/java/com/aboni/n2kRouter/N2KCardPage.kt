package com.aboni.n2kRouter

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

open class N2KCardPage(context: Context, val ble: BLEThing?): ConstraintLayout(context), BLEN2KListener {

    constructor(context: Context): this(context, null)

    //region widgets
    private val theContainer: ConstraintLayout
        get() = findViewById(R.id.theContainer)
    private val cardText: TextView
        get() = findViewById(R.id.cardText)
    private val cardImage: ImageView
        get() = findViewById(R.id.cardImg)
    //endregion

    protected fun initView() {
        inflate(context, R.layout.card_page, this)
    }

    open fun onHeaderImageClicked() {
    }

    fun setImageResource(r: Int) {
        cardImage.setImageResource(r)
    }

    fun setTitleResource(t: Int) {
        cardText.setText(t)
    }

    protected fun attachCard(view: Int) {
        inflate(context, view, theContainer)
        ble?.addListener(this)
        cardImage.setOnClickListener {
            onHeaderImageClicked()
        }
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
    }

    override fun onConf(conf: Conf) {
    }

    override fun onData(data: Data) {
    }

    override fun onScan(device: DeviceItem) {
    }

    override fun onRssi(connectedDevice: DeviceItem, rssi: Int) {
    }

    open fun onActivate() {
    }
}